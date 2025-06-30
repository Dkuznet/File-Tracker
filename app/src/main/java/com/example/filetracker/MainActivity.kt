package com.example.filetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.filetracker.ui.theme.FileTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileTrackerTheme {
                MainScreen(this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activity: ComponentActivity) {
    var permissionRequested by remember { mutableStateOf(false) }
    var hasAllFilesPermission by remember {
        mutableStateOf(checkAllFilesPermission(activity))
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("File Tracker") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Greeting(
                name = "Android",
                modifier = Modifier.padding(bottom = 20.dp)
            )
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !checkAllFilesPermission(
                            activity
                        )
                    ) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        activity.startActivity(intent)
                        permissionRequested = true
                    } else {
                        // Запуск службы или нужной логики
                        Toast.makeText(
                            activity,
                            "Файловый сервис может быть запущен!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Text("Запустить сервис")
            }
        }
    }

    // Проверяем, вернулся ли пользователь из настроек и дал ли разрешение
    if (permissionRequested) {
        LaunchedEffect(Unit) {
            hasAllFilesPermission = checkAllFilesPermission(activity)
            if (hasAllFilesPermission) {
                Toast.makeText(
                    activity,
                    "Доступ к файлам предоставлен!",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    activity,
                    "Доступ к файлам не предоставлен!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

fun checkAllFilesPermission(activity: ComponentActivity): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello new $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FileTrackerTheme {
        Greeting("my Android")
    }
}