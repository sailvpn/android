package com.illiad.troad.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

// Explicit architectural imports from your project's custom core design theme
import com.illiad.troad.ui.theme.VpnCoral
import com.illiad.troad.ui.theme.OceanTertiaryDark // Soft Sand-Gold Highlights (#FFE4A7)
import com.illiad.troad.ui.theme.OceanPrimaryDark  // Clear Wave Blue (#0284C7)
import com.illiad.troad.R

enum class VpnStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING
}

/**
 * SmartStateSailLogo renders your project's brand icon asset.
 * It keeps the artistic foreground "V" fixed to the original Coral brand color,
 * while shifting the background canvas fill color to indicate tunnel state changes.
 */
@Composable
fun SmartStateSailLogo(
    status: VpnStatus,
    modifier: Modifier = Modifier.size(160.dp)
) {
    // 1. Compute the background canvas fill color based on the status
    val targetBgColor = when (status) {
        // Connected: Sand-Gold (#FFE4A7) - Sail is full of wind and in full swing!
        VpnStatus.CONNECTED -> OceanTertiaryDark

        // Disconnected: The default Slate-100 fallback (#F1F5F9) from the original design
        VpnStatus.DISCONNECTED -> Color(0xFFF1F5F9)

        // Connecting: Wave Blue (#0284C7) - Initializing the secure network tunnel stream
        VpnStatus.CONNECTING -> OceanPrimaryDark
    }

    // 2. Animate the background canvas transition smoothly over 450ms
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 450),
        label = "SailBackgroundFillTransition"
    )

    Box(modifier = modifier) {
        // LAYER 1: The background sail canvas shape.
        // We render this via 'Icon' now so we can apply the dynamic state color tints.
        Icon(
            painter = painterResource(id = R.drawable.ic_sail_bg),
            contentDescription = null, // Background layer is structural/non-decorative
            tint = animatedBgColor,
            modifier = Modifier.matchParentSize()
        )

        // LAYER 2: The foreground artistic "V" stroke line path overlay.
        // We use 'Image' here so that the XML's raw colors or vector attributes
        // are preserved statically. We can also force-tint it explicitly via modifier.
        Icon(
            painter = painterResource(id = R.drawable.ic_sail_fg),
            contentDescription = "VPN Gateway Active Link Status Indicator Graphic",
            tint = VpnCoral, // Statically locks the "V" lines to your brand identity Coral (#FF70A6)
            modifier = Modifier.matchParentSize()
        )
    }
}
