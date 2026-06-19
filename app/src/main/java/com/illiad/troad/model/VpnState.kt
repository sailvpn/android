package com.illiad.troad.model

enum class VpnState {
    DISCONNECTED,  // Core tunnel is completely idle and offline
    CONNECTING,    // Tunnel interface is allocated; Butler is handling authentication
    CONNECTED,     // Kernel routing is up and Go core is actively routing packet frames
    RECONNECTING,  // Core interfaces are hot-cycling due to structural config shifts
    SPEED,         // upstream, downstreram speed
    ERROR          // Handshake failed, credentials rejected, or connection dropped
}