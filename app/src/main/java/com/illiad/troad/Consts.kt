package com.illiad.troad

object Consts {
    const val TS = "TroadService"
    const val TM = "TokenManager"
    const val VM = "SettingsViewModel"
    // Native Kotlin logic to determine IP without NetworkInterface.getNetworkInterfaces()
    const val tunIp10_8_0_2 = "10.8.0.2"
    const val DNS1111 = "1.1.1.1"
    const val DNS1001 = "1.0.0.1"
    const val DNS8888 = "8.8.8.8"
    const val DNS9999 = "9.9.9.9"
    const val MTU = 1300 // Android VpnService.Builder.establish() will crash on a too high number

    const val ACTION_CONNECT = "qmt7e"
    const val ACTION_DISCONNECT = "dtpov"
    const val ACTION_RESTART = "b5jku"

    const val NOTIFICATION_CHANNEL_ID = "SailVpn"
    const val NOTIFICATION_ID = 1 // Unique ID for the notification
    const val PENDING_INTENT_REQUEST_CODE_DISCONNECT = 1002

    // ... other constants ...
    const val ACTION_VPN_STATUS_BROADCAST = "zsg35"
    const val EXTRA_STATE = "gcifu"
    const val EXTRA_MSG = "mg8cp"

    const val MIN: Int = 1
    const val MAX: Int = 64 // important, maximum value 128

}