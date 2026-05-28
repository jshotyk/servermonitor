package com.example

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ServerRepository()
    private val weatherRepository = WeatherRepository()
    private val workManager = WorkManager.getInstance(application)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    private val _servers = MutableStateFlow(repository.getDefaultServers())
    val servers = _servers.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring = _isMonitoring.asStateFlow()

    private val _weather = MutableStateFlow<CurrentWeather?>(null)
    val weather = _weather.asStateFlow()

    private val _weatherStateInfo = MutableStateFlow("Ожидание данных (включите GPS)...")
    val weatherStateInfo = _weatherStateInfo.asStateFlow()

    private var pollJob: Job? = null

    init {
        // Run an initial check
        checkServers()
    }

    fun fetchWeather() {
        val context = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            _weatherStateInfo.value = "Получение местоположения..."
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        _weatherStateInfo.value = "Синхронизация погоды..."
                        viewModelScope.launch {
                            val weatherResponse = weatherRepository.getWeather(location.latitude, location.longitude)
                            if (weatherResponse?.current_weather != null) {
                                _weather.value = weatherResponse.current_weather
                                _weatherStateInfo.value = "Погода: ${weatherResponse.current_weather.temperature}°C"
                            } else {
                                _weatherStateInfo.value = "Не удалось загрузить данные о погоде"
                            }
                        }
                    } else {
                        _weatherStateInfo.value = "Местоположение недоступно (нет сигнала GPS)"
                    }
                }
                .addOnFailureListener {
                    _weatherStateInfo.value = "Ошибка доступа к локации"
                }
        } else {
            _weatherStateInfo.value = "Нет разрешения на геолокацию"
        }
    }

    fun checkServers() {
        viewModelScope.launch {
            val currentServers = _servers.value
            // Set all to checking
            _servers.value = currentServers.map { it.copy(status = ServerStatus.CHECKING) }

            val results = currentServers.map { server ->
                async { repository.checkServer(server) }
            }.awaitAll()

            _servers.value = results
        }
    }

    fun toggleMonitoring() {
        val currentState = _isMonitoring.value
        if (currentState) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        _isMonitoring.value = true
        // Start foreground polling
        pollJob = viewModelScope.launch {
            while (_isMonitoring.value) {
                checkServers()
                delay(10000) // Poll every 10 seconds in foreground
            }
        }

        // Schedule WorkManager for background checks
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "MonitorWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun stopMonitoring() {
        _isMonitoring.value = false
        pollJob?.cancel()
        workManager.cancelUniqueWork("MonitorWorker")
        // Reset status
        _servers.value = _servers.value.map { it.copy(status = ServerStatus.CHECKING, latencyMs = null) }
    }
}
