package com.illiad.troad.model

import androidx.compose.ui.graphics.Color
import com.illiad.troad.ui.theme.OceanPrimaryDark
import com.illiad.troad.ui.theme.OceanTertiaryDark

/**
 * Represents the complete, type-safe lifecycle architecture of your VPN tunnel layer.
 */
sealed interface VpnStatus {

    // Explicit UI design property declarations enforced across all state variants
    val statusLabel: String
    val sailBgColor: Color

    /**
     * DISCONNECTED: The default system state.
     * Keeps the canvas matched identically to your original gateway design.
     */
    data object Disconnected : VpnStatus {
        override val statusLabel: String = "Disconnected"
        override val sailBgColor: Color = Color(0xFFF1F5F9) // Slate-100 fallback
    }

    /**
     * CONNECTING: Handshaking with the gateway.
     * Shifts the backdrop canvas into clear Wave Blue while negotiating certificates.
     * Optional payload slot: track the current stage (e.g., "Authenticating", "Loading Certs")
     */
    data class Connecting(val stage: String = "Connecting...") : VpnStatus {
        override val statusLabel: String = stage
        override val sailBgColor: Color = OceanPrimaryDark // Wave Blue (#0284C7)
    }

    /**
     * CONNECTED: The tunnel is secure and online.
     * The sail fills with vibrant Sand-Gold to indicate it's in full swing!
     * Optional payload slots: include raw download/upload statistics directly in the state.
     */
    data class Connected(
        val downloadSpeed: String = "0.0 Mbps",
        val uploadSpeed: String = "0.0 Mbps"
    ) : VpnStatus {
        override val statusLabel: String = "Connected"
        override val sailBgColor: Color = OceanTertiaryDark // Sand-Gold Highlights (#FFE4A7)
    }

    /**
     * ERROR: Something went wrong during setup or execution.
     * Displays the specific cause to the user.
     */
    data class Error(val message: String) : VpnStatus {
        override val statusLabel: String = message
        override val sailBgColor: Color = Color(0xFFEF4444) // Error Red
    }
}
