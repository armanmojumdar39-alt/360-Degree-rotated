package com.armanmojumder.rotate360

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.abs

class RotationService : Service() {

    companion object {
        const val ACTION_START = "com.armanmojumder.rotate360.START"
        const val ACTION_APPLY = "com.armanmojumder.rotate360.APPLY"
        const val ACTION_STOP = "com.armanmojumder.rotate360.STOP"
        private const val CHANNEL_ID = "rotate360_channel"
        private const val NOTIF_ID = 360

        @Volatile
        var running = false
    }

    private lateinit var wm: WindowManager

    // Invisible overlay that forces the screen orientation for EVERY app
    private var lockView: View? = null
    private lateinit var lockLp: WindowManager.LayoutParams

    // Draggable floating bubble + quick panel
    private var bubbleRoot: LinearLayout? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val ch = NotificationChannel(CHANNEL_ID, "Rotate360 service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        ensureLock()
        applyMode()
        syncBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        lockView?.let { runCatching { wm.removeView(it) } }
        bubbleRoot?.let { runCatching { wm.removeView(it) } }
        lockView = null
        bubbleRoot = null
        super.onDestroy()
    }

    // ---------- Orientation lock ----------

    private fun ensureLock() {
        if (lockView != null) return
        lockLp = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        lockView = View(this)
        wm.addView(lockView, lockLp)
    }

    private fun applyMode() {
        val mode = Modes.byId(Prefs.modeId(this))
        lockLp.screenOrientation = mode.orientation
        lockView?.let { wm.updateViewLayout(it, lockLp) }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        rebuildPanel()
    }

    // ---------- Notification ----------

    private fun buildNotification(): Notification {
        val mode = Modes.byId(Prefs.modeId(this))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RotationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rotate)
            .setContentTitle("Rotate360 is active")
            .setContentText("Mode: ${mode.title}")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    // ---------- Floating bubble ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun syncBubble() {
        if (Prefs.bubble(this)) showBubble() else hideBubble()
    }

    private fun hideBubble() {
        bubbleRoot?.let { runCatching { wm.removeView(it) } }
        bubbleRoot = null
        panel = null
    }

    private fun showBubble() {
        if (bubbleRoot != null) return

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_rotate)
            setBackgroundResource(R.drawable.bg_bubble)
            setPadding(dp(13), dp(13), dp(13), dp(13))
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            alpha = 0.92f
        }
        val pnl = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_panel)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        root.addView(icon)
        root.addView(pnl)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(220)
        }

        icon.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var downX = 0f
            var downY = 0f
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x; startY = lp.y
                        downX = e.rawX; downY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        if (moved) {
                            lp.x = startX + dx
                            lp.y = startY + dy
                            wm.updateViewLayout(root, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) togglePanel()
                }
                return true
            }
        })

        bubbleRoot = root
        bubbleLp = lp
        panel = pnl
        wm.addView(root, lp)
        rebuildPanel()
    }

    private fun togglePanel() {
        val p = panel ?: return
        val lp = bubbleLp ?: return
        val open = p.visibility != View.VISIBLE
        p.visibility = if (open) View.VISIBLE else View.GONE
        if (open) {
            // keep the panel fully on screen
            val m = resources.displayMetrics
            val maxY = m.heightPixels - dp(52 + 8 + Modes.all.size * 44 + 24)
            val maxX = m.widthPixels - dp(220)
            lp.y = lp.y.coerceIn(0, maxY.coerceAtLeast(0))
            lp.x = lp.x.coerceIn(0, maxX.coerceAtLeast(0))
        }
        bubbleRoot?.let { wm.updateViewLayout(it, lp) }
    }

    private fun rebuildPanel() {
        val p = panel ?: return
        p.removeAllViews()
        val current = Prefs.modeId(this)
        Modes.all.forEach { mode ->
            val item = TextView(this).apply {
                text = mode.title
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                minHeight = dp(44)
                minWidth = dp(200)
                if (mode.id == current) setBackgroundResource(R.drawable.bg_item_selected)
                setOnClickListener {
                    Prefs.setMode(this@RotationService, mode.id)
                    applyMode()
                    panel?.visibility = View.GONE
                    bubbleRoot?.let { r -> bubbleLp?.let { l -> wm.updateViewLayout(r, l) } }
                }
            }
            p.addView(item)
        }
    }
}
