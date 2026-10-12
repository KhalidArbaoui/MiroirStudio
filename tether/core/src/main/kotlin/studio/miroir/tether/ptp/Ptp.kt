package studio.miroir.tether.ptp

/** PTP (ISO 15740) codes used by MIROIR Tether, plus Nikon and Sony vendor extensions. */
object Ptp {
    // Container types
    const val TYPE_COMMAND = 1
    const val TYPE_DATA = 2
    const val TYPE_RESPONSE = 3
    const val TYPE_EVENT = 4
    const val HEADER_SIZE = 12

    // Standard operations
    const val OP_GET_DEVICE_INFO = 0x1001
    const val OP_OPEN_SESSION = 0x1002
    const val OP_CLOSE_SESSION = 0x1003
    const val OP_GET_STORAGE_IDS = 0x1004
    const val OP_GET_OBJECT_INFO = 0x1008
    const val OP_GET_OBJECT = 0x1009
    const val OP_GET_THUMB = 0x100A
    const val OP_INITIATE_CAPTURE = 0x100E
    const val OP_GET_DEVICE_PROP_DESC = 0x1014
    const val OP_GET_DEVICE_PROP_VALUE = 0x1015
    const val OP_SET_DEVICE_PROP_VALUE = 0x1016
    const val OP_GET_PARTIAL_OBJECT = 0x101B

    // Nikon vendor operations
    const val OP_NIKON_AF_DRIVE = 0x90C1
    const val OP_NIKON_START_LIVE_VIEW = 0x9201
    const val OP_NIKON_END_LIVE_VIEW = 0x9202
    const val OP_NIKON_GET_LIVE_VIEW_IMG = 0x9203
    const val OP_NIKON_CAPTURE_SDRAM = 0x90C0
    const val OP_NIKON_GET_LARGE_THUMB = 0x90C4
    const val OP_NIKON_CHECK_EVENT = 0x90C7
    const val OP_NIKON_DEVICE_READY = 0x90C8
    const val OP_NIKON_CAPTURE_REC_IN_MEDIA = 0x9207

    // Sony vendor operations
    const val OP_SONY_SDIO_CONNECT = 0x9201
    const val OP_SONY_GET_EXT_DEVICE_INFO = 0x9202
    const val OP_SONY_SET_CONTROL_DEVICE_A = 0x9205
    const val OP_SONY_SET_CONTROL_DEVICE_B = 0x9207
    const val OP_SONY_GET_ALL_DEVICE_PROP_DATA = 0x9209

    // Responses
    const val RC_OK = 0x2001
    const val RC_GENERAL_ERROR = 0x2002
    const val RC_SESSION_NOT_OPEN = 0x2003
    const val RC_OPERATION_NOT_SUPPORTED = 0x2005
    const val RC_INVALID_OBJECT_HANDLE = 0x2009
    const val RC_STORE_FULL = 0x200C
    const val RC_DEVICE_BUSY = 0x2019
    const val RC_SESSION_ALREADY_OPEN = 0x201E
    const val RC_NIKON_OUT_OF_FOCUS = 0xA002
    const val RC_NIKON_NOT_LIVE_VIEW = 0xA00B

    // Events
    const val EV_OBJECT_ADDED = 0x4002
    const val EV_DEVICE_PROP_CHANGED = 0x4006
    const val EV_CAPTURE_COMPLETE = 0x400D
    const val EV_NIKON_OBJECT_ADDED_IN_SDRAM = 0xC101
    const val EV_SONY_OBJECT_ADDED = 0xC201
    const val EV_SONY_PROPERTY_CHANGED = 0xC203

    // Object formats
    const val FMT_ASSOCIATION = 0x3001
    const val FMT_JPEG = 0x3801
    const val FMT_TIFF = 0x380D
    const val FMT_SONY_ARW = 0xB101

    // Standard properties
    const val PROP_WHITE_BALANCE = 0x5005
    const val PROP_F_NUMBER = 0x5007
    const val PROP_EXPOSURE_TIME = 0x500D
    const val PROP_EXPOSURE_PROGRAM = 0x500E
    const val PROP_EXPOSURE_INDEX = 0x500F
    const val PROP_EXPOSURE_BIAS = 0x5010

    // Nikon properties
    const val NIKON_PROP_COLOR_TEMPERATURE = 0xD01E
    const val NIKON_PROP_EXPOSURE_TIME = 0xD100

    // Sony properties / control codes
    const val SONY_PROP_SHUTTER = 0xD20D
    const val SONY_PROP_COLOR_TEMPERATURE = 0xD20F
    const val SONY_PROP_ISO = 0xD21E
    const val SONY_PROP_PRIORITY_KEY = 0xD25A
    const val SONY_PRIORITY_PC_REMOTE = 0x0010L
    const val SONY_PROP_OBJECT_IN_MEMORY = 0xD215
    const val SONY_PROP_BATTERY = 0xD218
    const val SONY_CTRL_S1_HALF_PRESS = 0xD2C1
    const val SONY_CTRL_S2_RELEASE = 0xD2C2
    const val SONY_PRESS = 2
    const val SONY_RELEASE = 1
    const val SONY_IN_MEMORY_HANDLE = 0xFFFFC001L
    const val SONY_LIVE_VIEW_HANDLE = 0xFFFFC002L

    // Nikon SDRAM handle
    const val NIKON_SDRAM_HANDLE = 0xFFFF0001L

    // USB vendor IDs
    const val USB_VENDOR_NIKON = 0x04B0
    const val USB_VENDOR_SONY = 0x054C

    private val responseNames = mapOf(
        0x2001 to "OK", 0x2002 to "GeneralError", 0x2003 to "SessionNotOpen",
        0x2004 to "InvalidTransactionID", 0x2005 to "OperationNotSupported",
        0x2006 to "ParameterNotSupported", 0x2007 to "IncompleteTransfer",
        0x2009 to "InvalidObjectHandle", 0x200A to "InvalidDevicePropFormat", 0x200B to "InvalidDevicePropValue",
        0x2013 to "DevicePropNotSupported", 0x200C to "StoreFull", 0x200F to "AccessDenied",
        0x2019 to "DeviceBusy", 0x201D to "InvalidParameter", 0x201E to "SessionAlreadyOpen",
        0xA002 to "Nikon:OutOfFocus", 0xA00B to "Nikon:NotLiveView",
    )

    fun responseName(code: Int): String = responseNames[code] ?: code.hex4()
}
