package com.illiad.troad.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

// Explicit architectural imports from your project's custom core design theme
import com.illiad.troad.ui.theme.VpnCoral
import com.illiad.troad.ui.theme.OceanPrimaryDark
import com.illiad.troad.ui.theme.OceanTertiaryDark
import com.illiad.troad.R

/**
 * Declares the three operational status footprints of your VPN tunnel network client lifecycle.
 */
enum class VpnStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING
}

/**
 * SmartStateSailLogo renders your project's brand icon asset, maintaining a fixed Slate-100
 * vector background canvas canvas sheet, while smoothly blending the primary artistic "V" paths
 * through color transitions mapping onto the state of the active connection layer.
 *
 * @param status The current operational network state engine input token.
 * @param modifier Custom layouts modifiers handling sizing arrays. Defaults to 160 square device pixels.
 */
@Composable
fun SmartStateSailLogo(
    status: VpnStatus,
    modifier: Modifier = Modifier.size(160.dp)
) {
    // Computes the dynamic layout target color bound from your theme tokens based on active context
    val targetVColor = when (status) {
        VpnStatus.CONNECTED -> OceanPrimaryDark     // Clear Wave Blue (#0284C7)
        VpnStatus.DISCONNECTED -> VpnCoral          // Warm Friendly Coral (#FF70A6)
        VpnStatus.CONNECTING -> OceanTertiaryDark   // Soft Sand-Gold Highlights (#FFE4A7)
    }

    // Intercepts the target color layout calculations to generate a hardware-accelerated smooth crossfade
    val animatedVColor by animateColorAsState(
        targetValue = targetVColor,
        animationSpec = tween(durationMillis = 450), // 450 milliseconds elegant linear transition fade
        label = "SailLogoColorTransition"
    )

    Box(modifier = modifier) {
        // LAYER 1: The permanent static background canvas layer asset layout tracking.
        // Rendered explicitly via 'Image' component context so internal #FFF1F5F9 hex strings
        // bypass the system dynamic theme coloration overrides entirely.
        Image(
            painter = painterResource(id = R.drawable.ic_sail_bg),
            contentDescription = null, // Set null as structural context layer is fully non-decorative
            modifier = Modifier.matchParentSize()
        )

        // LAYER 2: The foreground artistic "V" stroke line path overlay components layer.
        // Managed cleanly through the 'Icon' component framework layout context to inject the real-time
        // calculated 'animatedVColor' dynamic hardware canvas color matrix masks safely over white paths.
        Icon(
            painter = painterResource(id = R.drawable.ic_sail_fg),
            contentDescription = "VPN Gateway Active Link Status Indicator Graphic",
            tint = animatedVColor,
            modifier = Modifier.matchParentSize()
        )
    }
}

