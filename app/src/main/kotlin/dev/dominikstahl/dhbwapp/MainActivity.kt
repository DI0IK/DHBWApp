package dev.dominikstahl.dhbwapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import dev.dominikstahl.dhbwapp.data.local.UserPreferences
import dev.dominikstahl.dhbwapp.ui.DhbwApp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val userPreferences by lazy { UserPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            DhbwApp(httpClient = httpClient, userPreferences = userPreferences)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.close()
    }
}
