package com.floatingtools.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private val webViews = mutableListOf<WebView>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        hidePanel()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("floating", "Floating Tools", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "floating")
            .setContentTitle("Floating Tools aktif")
            .setContentText("Ketuk bubble untuk membuka panel")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val size = dp(52)
        val tv = TextView(this).apply {
            text = "F"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(37, 99, 235))
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(200)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    lp.x = startX + dx
                    lp.y = startY + dy
                    wm.updateViewLayout(tv, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }

        wm.addView(tv, lp)
        bubble = tv
    }

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun showPanel() {
        val dm = resources.displayMetrics
        val w = minOf(dm.widthPixels - dp(16), dp(340))
        val h = minOf(dm.heightPixels - dp(120), dp(480))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(200, 205, 215))
            }
        }

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val box = FrameLayout(this)
        val names = listOf("Web", "Trans", "Hitung", "Catat", "Tutup", "Stop")

        names.forEachIndexed { i, n ->
            val b = Button(this).apply {
                text = n
                textSize = 11f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
            }
            b.setOnClickListener {
                when (i) {
                    4 -> hidePanel()
                    5 -> stopSelf()
                    else -> select(box, i)
                }
            }
            tabs.addView(b, LinearLayout.LayoutParams(0, dp(40), 1f))
        }

        root.addView(tabs, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(box, LinearLayout.LayoutParams(MATCH, 0, 1f))
        select(box, 0)

        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(60)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        wm.addView(root, lp)
        panel = root
    }

    private fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        webViews.forEach { it.destroy() }
        webViews.clear()
    }

    private fun select(box: FrameLayout, i: Int) {
        box.removeAllViews()
        val v = when (i) {
            0 -> browserView()
            1 -> translateView()
            2 -> calcView()
            else -> notesView()
        }
        box.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(url: String): WebView {
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.webViewClient = WebViewClient()
        w.loadUrl(url)
        webViews.add(w)
        return w
    }

    private fun toUrl(q: String): String {
        val t = q.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) {
            t
        } else if (t.contains(".") && !t.contains(" ")) {
            "https://$t"
        } else {
            "https://www.google.com/search?q=" + Uri.encode(t)
        }
    }

    private fun browserView(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val input = EditText(this).apply {
            hint = "Cari atau ketik alamat"
            textSize = 13f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        val web = newWebView("https://www.google.com")
        input.setOnEditorActionListener { v, _, _ ->
            web.loadUrl(toUrl(v.text.toString()))
            true
        }
        col.addView(input, LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(web, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun translateView(): View =
        newWebView("https://translate.google.com/?sl=auto&tl=id")

    private fun calcView(): View {
        val expr = StringBuilder()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val disp = TextView(this).apply {
            text = "0"
            textSize = 26f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(Color.BLACK)
        }
        col.addView(disp, LinearLayout.LayoutParams(MATCH, dp(70)))

        fun press(k: String) {
            when (k) {
                "C" -> expr.setLength(0)
                "DEL" -> if (expr.isNotEmpty()) expr.deleteCharAt(expr.length - 1)
                "=" -> {
                    val r = runCatching { fmt(ExprParser(expr.toString()).parse()) }.getOrDefault("Error")
                    expr.setLength(0)
                    if (r != "Error") expr.append(r)
                    disp.text = r
                    return
                }
                else -> expr.append(k)
            }
            disp.text = if (expr.isEmpty()) "0" else expr.toString()
        }

        val rows = listOf(
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("0", ".", "=", "+"),
            listOf("C", "DEL")
        )
        rows.forEach { r ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.forEach { k ->
                val b = Button(this).apply {
                    text = k
                    textSize = 16f
                    setOnClickListener { press(k) }
                }
                line.addView(b, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
            col.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        return col
    }

    private fun fmt(v: Double): String {
        return if (v.isNaN() || v.isInfinite()) {
            "Error"
        } else if (v == Math.floor(v) && Math.abs(v) < 1e15) {
            v.toLong().toString()
        } else {
            v.toString()
        }
    }

    private fun notesView(): View {
        val prefs = getSharedPreferences("floating", Context.MODE_PRIVATE)
        val edit = EditText(this).apply {
            hint = "Tulis catatan di sini"
            textSize = 14f
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(prefs.getString("notes", ""))
        }
        edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("notes", s?.toString() ?: "").apply()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        return edit
    }
}

private class ExprParser(private val s: String) {
    private var pos = 0

    fun parse(): Double {
        val v = expr()
        if (pos < s.length) throw IllegalArgumentException("bad input")
        return v
    }

    private fun expr(): Double {
        var v = term()
        while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
            val op = s[pos]
            pos += 1
            val r = term()
            v = if (op == '+') v + r else v - r
        }
        return v
    }

    private fun term(): Double {
        var v = factor()
        while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
            val op = s[pos]
            pos += 1
            val r = factor()
            v = if (op == '*') v * r else v / r
        }
        return v
    }

    private fun factor(): Double {
        if (pos < s.length && s[pos] == '-') {
            pos += 1
            return -factor()
        }
        val st = pos
        while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos += 1
        return s.substring(st, pos).toDouble()
    }
}
