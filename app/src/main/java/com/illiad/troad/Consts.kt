package com.illiad.troad

object Consts {
    const val TAG = "TroadService"

    const val TUN_IP = "10.8.0.2"
    const val DNS1 = "8.8.8.8"
    const val DNS2 = "8.8.4.4"
    const val MTU = 1300 // Android VpnService.Builder.establish() will crash on a too high number

    const val ACTION_CONNECT = "qmt7e"
    const val ACTION_DISCONNECT = "dtpov"

    // Keys for passing parameters via Intent extras
    const val EXTRA_SERVER_ADDRESS = "mjupy"
    const val EXTRA_SERVER_PORT = "yrqvm"
    const val EXTRA_SHARED_SECRET = "ebhel"


    const val NOTIFICATION_CHANNEL_ID = "TroadService"
    const val NOTIFICATION_CHANNEL_NAME = "Troad"
    const val NOTIFICATION_ID = 1 // Unique ID for the notification
    const val PENDING_INTENT_REQUEST_CODE_OPEN_APP = 1001 // Or any other unique integer
    const val PENDING_INTENT_REQUEST_CODE_DISCONNECT = 1002

    // ... other constants ...
    const val ACTION_VPN_STATUS_BROADCAST = "zsg35"
    const val EXTRA_STATUS_MESSAGE = "gcifu"
    const val EXTRA_IS_CONNECTED = "zprvu"

}