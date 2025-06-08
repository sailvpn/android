package com.illiad.troad

object Consts {
    const val TAG = "TroadService"

    const val ACTION_CONNECT = "com.illiad.troad.CONNECT"
    const val ACTION_DISCONNECT = "com.illiad.troad.DISCONNECT"

    // Keys for passing parameters via Intent extras
    const val EXTRA_SERVER_ADDRESS = "com.illiad.troad.SERVER_ADDRESS"
    const val EXTRA_SERVER_PORT = "com.illiad.troad.SERVER_PORT"
    const val EXTRA_SHARED_SECRET = "com.illiad.troad.SHARED_SECRET"


    const val NOTIFICATION_CHANNEL_ID = "TroadService"
    const val NOTIFICATION_CHANNEL_NAME = "Troad"
    const val NOTIFICATION_ID = 1 // Unique ID for the notification
    const val PENDING_INTENT_REQUEST_CODE_OPEN_APP = 1001 // Or any other unique integer
    const val PENDING_INTENT_REQUEST_CODE_DISCONNECT = 1002

    // ... other constants ...
    const val ACTION_VPN_STATUS_BROADCAST = "com.illiad.troad.STATUS_BROADCAST"
    const val EXTRA_STATUS_MESSAGE = "com.illiad.troad.STATUS_MESSAGE"
    const val EXTRA_IS_CONNECTED = "com.illiad.troad.IS_CONNECTED"


}