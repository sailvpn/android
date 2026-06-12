package com.illiad.troad.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.illiad.troad.ui.theme.VpnCoral
import com.illiad.troad.R
import com.illiad.troad.model.VpnStatus
// Inside your SmartStateSailLogo.kt

@Composable
fun SmartStateSailLogo(
    status: VpnStatus,
    modifier: Modifier = Modifier.size(160.dp)
) {
    // Reads directly from the active type-safe state container token!
    val animatedBgColor by animateColorAsState(
        targetValue = status.sailBgColor,
        animationSpec = tween(durationMillis = 450),
        label = "SailBackgroundFillTransition"
    )

    Box(modifier = modifier) {
        // LAYER 1: Background Sail Canvas
        Icon(
            painter = painterResource(id = R.drawable.ic_sail_bg),
            contentDescription = null,
            tint = animatedBgColor,
            modifier = Modifier.matchParentSize()
        )

        // LAYER 2: Foreground Brand Coral "V" (Locked Statically)
        Icon(
            painter = painterResource(id = R.drawable.ic_sail_fg),
            contentDescription = "VPN Gateway Anchor Identity Graphic",
            tint = VpnCoral,
            modifier = Modifier.matchParentSize()
        )
    }
}
