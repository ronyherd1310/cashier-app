package com.cashierapp.photocheckout.ui.common.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.cashierapp.photocheckout.ui.theme.AppDimens

@Composable
public fun CameraCaptureScreen(
    state: CameraCaptureUiState,
    onCaptureClick: () -> Unit,
    onRequestPermissionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CameraCaptureUiState.Ready ->
            CameraReadyState(
                onCaptureClick = onCaptureClick,
                modifier = modifier,
            )

        CameraCaptureUiState.PermissionDenied ->
            CameraPermissionDeniedState(
                onRequestPermissionClick = onRequestPermissionClick,
                modifier = modifier,
            )
    }
}

@Composable
private fun CameraReadyState(
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Button(onClick = onCaptureClick) {
            Text(text = "Capture")
        }
    }
}

@Composable
private fun CameraPermissionDeniedState(
    onRequestPermissionClick: () -> Unit,
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
    }
}
