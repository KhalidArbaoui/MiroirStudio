package studio.miroir.tether.camera

/**
 * Standard Sony 1/3-stop values, for the pick list when the camera reports only the current
 * shutter / aperture / EV value and not the list (A7 III over USB). The dial then clicks there.
 */
object SonyStops {
    /** Shutter as (numerator shl 16) or denominator, slowest first. */
    val shutter: List<Long> = buildList {
        for (tenths in listOf(300, 250, 200, 150, 130, 100, 80, 60, 50, 40, 32, 25, 20, 16, 13, 10, 8, 6, 5, 4)) add((tenths.toLong() shl 16) or 10)
        for (den in listOf(3, 4, 5, 6, 8, 10, 13, 15, 20, 25, 30, 40, 50, 60, 80, 100, 125, 160, 200, 250, 320, 400, 500,
            640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000)) add((1L shl 16) or den.toLong())
    }

    /** f-number × 100. */
    val aperture: List<Long> = listOf(140, 160, 180, 200, 220, 250, 280, 320, 350, 400, 450, 500, 560, 630, 710, 800,
        900, 1000, 1100, 1300, 1400, 1600, 1800, 2000, 2200).map { it.toLong() }

    /** Exposure compensation in 1/1000 EV, as Sony counts thirds (0.3, 0.7). */
    val exposureComp: List<Long> = (-5..5).flatMap { whole ->
        listOf(whole * 1000 - 700, whole * 1000 - 300, whole * 1000)
    }.filter { it in -5000..5000 }.map { (it and 0xFFFF).toLong() }

    fun forSetting(id: SettingId): List<Long> = when (id) {
        SettingId.Shutter -> shutter
        SettingId.Aperture -> aperture
        SettingId.ExposureComp -> exposureComp
        else -> emptyList()
    }
}
