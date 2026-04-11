package com.example.dystopia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import com.example.dystopia.MainScreen
import com.example.dystopia.ui.theme.DystopiaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ Проверяем запуск из уведомления сразу при создании
        if (intent?.getBooleanExtra("FROM_NOTIFICATION", false) == true) {
            viewModel.requestNavigationReset()
        }

        setContent {
            DystopiaTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel)
                }
            }
        }

        viewModel.syncUiWithMediaSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("FROM_NOTIFICATION", false)) {
            viewModel.requestNavigationReset()
            // Убираем флаг, чтобы сброс не срабатывал при повороте экрана
            intent.removeExtra("FROM_NOTIFICATION")
        }
    }

    // ✅ Восстанавливаем состояние при возврате в приложение
    override fun onResume() {
        super.onResume()
        viewModel.syncFromService()
        viewModel.syncUiWithMediaSession()
    }
}