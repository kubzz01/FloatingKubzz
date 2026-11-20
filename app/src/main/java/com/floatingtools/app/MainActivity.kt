package com.floatingtools.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Floating Tools"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
        }

        val desc = TextView(this).apply {
            text = "Bubble melayang berisi browser, penerjemah, kalkulator, dan catatan untuk membantu belajar."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
            setTextColor(Color.DKGRAY)
        }

        val start = Button(this).apply {
            text = "Aktifkan Floating"
            setOnClickListener { startFloating() }
        }

        val stop = Button(this).apply {
            text = "Matikan Floating"
            setOnClickListener { stopService(Intent(this@MainActivity, FloatingService::class.java)) }
        }

        root.addView(title)
        root.addView(desc)
        root.addView(start)
        root.addView(stop)
        setContentView(root)
    }

    private fun startFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan tampil di atas aplikasi lain", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        startForegroundService(Intent(this, FloatingService::class.java))
        moveTaskToBack(true)
    }
}
