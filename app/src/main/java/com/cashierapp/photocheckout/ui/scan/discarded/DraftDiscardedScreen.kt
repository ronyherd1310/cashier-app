package com.cashierapp.photocheckout.ui.scan.discarded

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cashierapp.photocheckout.ui.common.glass.GradientButton
import com.cashierapp.photocheckout.ui.theme.AppDimens

/**
 * Draft Discarded success screen (mockup 18). Reached after confirming a discard;
 * no sale record exists (SALE-5).
 */
@Composable
public fun DraftDiscardedScreen(
    onNewScan: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding)
                .testTag("draft-discarded-screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Text(
            text = "Draft Discarded",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Text(
            text = "All items were removed and no sale was created.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXl))
        GradientButton(
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("discarded-new-scan-button"),
            label = "New Scan",
            onClick = onNewScan,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("discarded-home-button"),
            onClick = onBackToHome,
            shape = RoundedCornerShape(AppDimens.controlRadius),
        ) {
            Text("Back to Home")
        }
    }
}
