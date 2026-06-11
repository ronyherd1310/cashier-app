package com.cashierapp.photocheckout.ui.common.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashierapp.photocheckout.ui.theme.AppDimens

@Composable
public fun CameraCaptureScreen(
    state: CameraCaptureUiState,
    onCaptureClick: () -> Unit,
    onRequestPermissionClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable BoxScope.() -> Unit = {},
) {
    when (state) {
        CameraCaptureUiState.Ready ->
            CameraReadyState(
                onCaptureClick = onCaptureClick,
                onClose = onClose,
                modifier = modifier,
                preview = preview,
            )

        CameraCaptureUiState.PermissionDenied ->
            CameraPermissionDeniedState(
                onRequestPermissionClick = onRequestPermissionClick,
                onClose = onClose,
                modifier = modifier,
            )
    }
}

@Composable
private fun CameraReadyState(
    onCaptureClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        preview()
        IconButton(
            onClick = onClose,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(AppDimens.spaceMd)
                    .testTag("camera-close-button"),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close camera",
                tint = Color.White,
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppDimens.spaceXl)
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(width = 4.dp, color = Color.White.copy(alpha = 0.4f), shape = CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onCaptureClick)
                    .testTag("camera-capture-button"),
        )
    }
}

@Composable
private fun CameraPermissionDeniedState(
    onRequestPermissionClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera permission required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Text(
            text = "Allow camera access to capture product reference photos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        Button(onClick = onRequestPermissionClick) {
            Text(text = "Grant permission")
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        TextButton(onClick = onClose) {
            Text(text = "Cancel")
        }
    }
}
