package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class MonitorWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository = ServerRepository()

    override suspend fun doWork(): Result = coroutineScope {
        val servers = repository.getDefaultServers()
        
        val results = servers.map { server ->
            async { repository.checkServer(server) }
        }.awaitAll()

        val ruServers = results.filter { it.region == Region.RU }
        val euServers = results.filter { it.region == Region.EU }

        val ruOfflineCount = ruServers.count { it.status == ServerStatus.OFFLINE }
        val euOfflineCount = euServers.count { it.status == ServerStatus.OFFLINE }

        val ruMostlyOnline = ruServers.size - ruOfflineCount >= ruServers.size / 2
        val euMostlyOffline = euOfflineCount > euServers.size / 2

        if (ruMostlyOnline && euMostlyOffline) {
            sendNotification(
                "Тревога: Включены белые списки!",
                "Доступ к внешним серверам (Европа) заблокирован. Российские сервера доступны."
            )
        } else if (ruOfflineCount == ruServers.size && euOfflineCount == euServers.size) {
            sendNotification(
                "Ошибка сети",
                "Все тестируемые серверы недоступны. Проверьте интернет-соединение!"
            )
        } else if (euOfflineCount > 0) {
            // Optional: notify about some EU failures
            // sendNotification("Сбой соединения", "$euOfflineCount европейских серверов отключены.")
        }

        Result.success()
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Мониторинг серверов",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
