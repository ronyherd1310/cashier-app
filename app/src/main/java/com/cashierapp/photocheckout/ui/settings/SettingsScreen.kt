package com.cashierapp.photocheckout.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.TealPrimary

/** Minimal Settings screen: OpenRouter key (masked) + model id (T9). */
@Composable
public fun SettingsScreen(
    state: SettingsUiState,
    onApiKeyChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

        Text(text = "OpenRouter API key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().testTag("settings-api-key-field"),
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("sk-or-...") },
            shape = RoundedCornerShape(AppDimens.controlRadius),
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Text(text = "Model id", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().testTag("settings-model-id-field"),
            value = state.modelId,
            onValueChange = onModelIdChange,
            singleLine = true,
            shape = RoundedCornerShape(AppDimens.controlRadius),
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Text(
            text = "Currency: IDR",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("settings-save-button"),
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            shape = RoundedCornerShape(AppDimens.controlRadius),
        ) {
            Text("Save")
        }
        if (state.saved) {
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Text(
                modifier = Modifier.testTag("settings-saved-confirmation"),
                text = "Saved.",
                color = TealPrimary,
            )
        }
    }
}
