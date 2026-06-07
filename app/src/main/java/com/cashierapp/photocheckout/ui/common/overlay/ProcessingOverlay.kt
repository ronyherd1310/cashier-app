package com.cashierapp.photocheckout.ui.common.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashierapp.photocheckout.domain.model.ScanStage
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.TealPrimary

private val stageOrder = listOf(ScanStage.Uploading, ScanStage.Recognizing, ScanStage.Pricing)

private fun ScanStage.label(): String =
    when (this) {
        ScanStage.Uploading -> "Sending image"
        ScanStage.Recognizing -> "Recognizing items"
        ScanStage.Pricing -> "Building draft"
    }

/**
 * Staged processing overlay (C3, mockup 08): a progress ring, a three-step
 * checklist driven by [stage], and a Cancel action. The steps are the contract;
 * the ring is coarse.
 */
@Composable
public fun ProcessingOverlay(
    stage: ScanStage,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeIndex = stageOrder.indexOf(stage).coerceAtLeast(0)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .testTag("processing-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(AppDimens.screenPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(AppDimens.spaceLg)
                        .width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Processing image…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = "Looking up products in the catalog",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceLg))
                CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(AppDimens.spaceLg))
                stageOrder.forEachIndexed { index, step ->
                    StepRow(label = step.label(), done = index < activeIndex, active = index == activeIndex)
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("processing-cancel-button"),
                ) {
                    Text(text = "Cancel")
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    label: String,
    done: Boolean,
    active: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (done || active) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color =
                if (done || active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
