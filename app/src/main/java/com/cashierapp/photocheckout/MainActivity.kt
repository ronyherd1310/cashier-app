package com.cashierapp.photocheckout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cashierapp.photocheckout.ui.shell.AppShell
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoCheckoutTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppShell()
                }
            }
        }
    }
}
