package com.cashierapp.photocheckout.ui.scan.draft

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cashierapp.photocheckout.domain.model.DraftLine
import com.cashierapp.photocheckout.domain.money.IdrFormat
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.TealPrimary
import java.io.File
import java.util.Locale

private val Amber = Color(0xFFF5A623)

/** A single draft line (mockup 09/14): thumbnail, name, SKU, qty × unit, line total, confidence. */
@Composable
public fun DraftLineRow(
    line: DraftLine,
    onClick: () -> Unit,
    resolvePhotoPath: (String) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag("draft-line-${line.sku}")
                .padding(vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(AppDimens.spaceSm))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            model = line.photoPath?.let { File(resolvePhotoPath(it)) },
            contentDescription = "${line.name} photo",
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = line.sku,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Text(
                text = "${line.quantity} × IDR ${IdrFormat.format(line.unitPriceMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "IDR ${IdrFormat.format(line.lineTotalMinor)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TealPrimary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (line.lowConfidence) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Low confidence",
                        tint = Amber,
                        modifier =
                            Modifier
                                .size(16.dp)
                                .testTag("draft-low-confidence-${line.sku}"),
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                }
                Text(
                    modifier = Modifier.testTag("draft-confidence-${line.sku}"),
                    text = String.format(Locale.US, "%.2f", line.confidence),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (line.lowConfidence) Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
