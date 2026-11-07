package com.example.mockgps

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var latInput: EditText
    private lateinit var lngInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS),
                REQ_PERMISSION
            )
        }

        val pad = (16 * resources.displayMetrics.density).toInt()
        latInput = EditText(this).apply {
            hint = "纬度"
            setText("39.9087")
        }
        lngInput = EditText(this).apply {
            hint = "经度"
            setText("116.3975")
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(this@MainActivity).apply {
                text = "启动前请在「开发者选项 → 选择模拟位置信息应用」中选中本应用，或执行：\n" +
                    "su -c \"appops set com.example.mockgps android:mock_location allow\""
            })
            addView(latInput)
            addView(lngInput)
            addView(Button(this@MainActivity).apply {
                text = "开始"
                setOnClickListener { toggle(true) }
            })
            addView(Button(this@MainActivity).apply {
                text = "停止"
                setOnClickListener { toggle(false) }
            })
        }
        setContentView(root)
    }

    private fun toggle(start: Boolean) {
        val intent = Intent(this, MockService::class.java)
        if (start) {
            intent.putExtra(MockService.EXTRA_LAT, latInput.text.toString().toDoubleOrNull() ?: 39.9087)
            intent.putExtra(MockService.EXTRA_LNG, lngInput.text.toString().toDoubleOrNull() ?: 116.3975)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            intent.action = MockService.ACTION_STOP
            startService(intent)
        }
    }

    private companion object {
        const val REQ_PERMISSION = 1
    }
}
