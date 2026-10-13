package studio.miroir.tether.camera

import studio.miroir.tether.ptp.Ptp
import studio.miroir.tether.ptp.PropDesc
import kotlin.math.abs
import kotlin.math.roundToInt

/** Camera settings the tablet can show and change. */
enum class SettingId(val label: String) {
    Mode("Mode"),
    Shutter("Shutter"),
    Aperture("Aperture"),
    Iso("ISO"),
    ExposureComp("Exp. comp"),
    WhiteBalance("WB"),
    ColorTemp("Kelvin"),
}

class CameraSetting(
    val id: SettingId,
    val display: String,
    /** True when the tablet can step it (camera mode and lock permitting). */
    val adjustable: Boolean,
    /** Why it can't be changed from the tablet, e.g. "Lens ring". */
    val lockedBy: String? = null,
    /** Values the camera offers right now, in step order, for the tap-to-pick list. */
    val options: List<SettingOption> = emptyList(),
    val current: Long? = null,
)

class SettingOption(val value: Long, val label: String)

object SettingFormat {
    fun format(id: SettingId, code: Int, v: Long): String = when (id) {
        SettingId.Mode -> when (v.toInt()) {
            1 -> "M"; 2 -> "P"; 3 -> "A"; 4 -> "S"
            0x8010 -> "Auto"; 0x8050, 0x8051, 0x8052, 0x8053 -> "U" + (v.toInt() - 0x804F)
            else -> "0x%X".format(v)
        }
        SettingId.Shutter -> shutter(code, v)
        SettingId.Aperture -> if (v <= 0) "--" else "f/" + trim(v / 100.0)
        SettingId.Iso -> when {
            v == 0xFFFFFFL || v == 0xFFFFFFFFL -> "ISO AUTO"
            else -> "ISO ${v and 0xFFFFFF}"
        }
        SettingId.ExposureComp -> {
            val ev = v.toInt().toShort() / 1000.0
            if (abs(ev) < 0.05) "0.0 EV" else (if (ev > 0) "+" else "") + "%.1f EV".format(ev)
        }
        SettingId.WhiteBalance -> whiteBalance(v.toInt())
        SettingId.ColorTemp -> "${v}K"
    }

    private fun shutter(code: Int, v: Long): String {
        if (code == Ptp.PROP_EXPOSURE_TIME) {
            // Standard: units of 1/10000 s.
            if (v == 0xFFFFFFFFL) return "Bulb"
            val s = v / 10000.0
            return if (s >= 0.3) trim(s) + "s" else "1/" + (1 / s).roundToInt()
        }
        // Nikon 0xD100 / Sony 0xD20D: numerator << 16 | denominator.
        if (v == 0L || v == 0xFFFFFFFFL) return "Bulb"
        if (v == 0xFFFFFFFEL) return "Time"
        val num = (v ushr 16) and 0xFFFF
        val den = v and 0xFFFF
        if (den == 0L) return "--"
        return if (num == 1L && den > 1) "1/$den" else trim(num.toDouble() / den) + "s"
    }

    private fun whiteBalance(v: Int): String = when (v) {
        1 -> "Manual"; 2 -> "Auto"; 3 -> "One-push"; 4 -> "Daylight"; 5 -> "Fluorescent"
        6 -> "Tungsten"; 7 -> "Flash"
        0x8001, 0x8002, 0x8003, 0x8004, 0x8005, 0x8006 -> "Fluorescent"
        0x8010 -> "Cloudy"; 0x8011 -> "Shade"; 0x8012 -> "Kelvin"; 0x8013 -> "Preset"
        0x8016 -> "Natural Auto"
        0x8020, 0x8021, 0x8022 -> "Custom ${v - 0x801F}"
        0x8030 -> "Underwater"
        else -> "0x%04X".format(v)
    }

    private fun trim(d: Double): String =
        if (d == d.roundToInt().toDouble()) d.roundToInt().toString() else "%.1f".format(d)

    /** Next value in [values] from [current] in [direction] (+1/-1), or null at either end. */
    fun neighbor(values: List<Long>, current: Long, direction: Int): Long? {
        if (values.isEmpty()) return null
        val i = values.indexOf(current)
        if (i < 0) {
            // Current not listed (camera rounding): go to the closest one in that direction.
            val sorted = values.sorted()
            return if (direction > 0) sorted.firstOrNull { it > current } else sorted.lastOrNull { it < current }
        }
        val j = i + direction
        return if (j in values.indices) values[j] else null
    }

    /** Values for the pick list: Kelvin thinned to 100 K steps, everything else as offered. */
    fun pickList(id: SettingId, values: List<Long>): List<Long> =
        if (id == SettingId.ColorTemp && values.size > 40) values.sorted().filter { it % 100 == 0L } else values

    /** Kelvin steps of ~100 K even when the camera offers 10 K increments. */
    fun kelvinNeighbor(desc: PropDesc, direction: Int): Long? {
        val values = desc.allowedValues.sorted()
        if (values.isEmpty()) return null
        val target = desc.current + 100 * direction
        return if (direction > 0) values.firstOrNull { it >= target } ?: values.last().takeIf { it != desc.current }
        else values.lastOrNull { it <= target } ?: values.first().takeIf { it != desc.current }
    }
}
