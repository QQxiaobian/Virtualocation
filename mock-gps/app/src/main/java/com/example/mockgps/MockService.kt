package com.example.mockgps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class MockService : Service() {

    companion object {
        const val CHANNEL_ID = "mock_gps"
        const val NOTI_ID = 1
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val ACTION_STOP = "com.example.mockgps.STOP"
    }

    private val lm by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTI_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTI_ID, notification)
        }

        val lat = intent?.getDoubleExtra(EXTRA_LAT, 39.9087) ?: 39.9087
        val lng = intent?.getDoubleExtra(EXTRA_LNG, 116.3975) ?: 116.3975
        startMock(lat, lng)
        return START_STICKY
    }

    private fun startMock(lat: Double, lng: Double) {
        stopWorker()

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                lm.removeTestProvider(provider)
            } catch (_: Exception) {
            }
            try {
                lm.addTestProvider(
                    provider,
                    provider == LocationManager.NETWORK_PROVIDER,
                    provider == LocationManager.GPS_PROVIDER,
                    false,
                    false,
                    true,
                    true,
                    true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                lm.setTestProviderEnabled(provider, true)
            } catch (_: SecurityException) {
                // 未获得模拟位置权限：需在开发者选项中选中本应用，或用 appops 授权
                stopSelf()
                return
            }
        }

        running = true
        worker = thread(name = "mock-location") {
            while (running) {
                for (provider in providers) {
                    val loc = Location(provider).apply {
                        latitude = lat
                        longitude = lng
                        accuracy = 3f
                        altitude = 50.0
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    }
                    try {
                        lm.setTestProviderLocation(provider, loc)
                    } catch (_: Exception) {
                    }
                }
                Thread.sleep(1000)
            }
            for (provider in providers) {
                try {
                    lm.removeTestProvider(provider)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun stopWorker() {
        running = false
        worker?.join(1500)
        worker = null
    }

    override fun onDestroy() {
        stopWorker()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "虚拟定位", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MockService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("虚拟定位运行中")
            .setContentText("正在向系统写入模拟坐标")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .addAction(0, "停止", stopIntent)
            .build()
    }
}
