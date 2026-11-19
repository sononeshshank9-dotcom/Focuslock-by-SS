package com.focuslock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var lastPkg: String? = null
    private var lastQuery = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (doCheck()) handler.postDelayed(this, 600)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("focus", "FocusLock", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "focus")
            .setContentTitle("FocusLock is active")
            .setContentText("Blocked apps are locked until " + fmt(Store.endTime(this)))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(tick)
        if (!Store.active(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        removeOverlay()
        super.onDestroy()
    }

    private fun fmt(t: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

    // Returns false when the session is over and the service is stopping.
    private fun doCheck(): Boolean {
        if (!Store.active(this)) {
            removeOverlay()
            Store.setLocked(this, false)
            stopSelf()
            return false
        }
        val fg = foregroundPackage() ?: return true
        if (fg != packageName && effectiveBlocked().contains(fg)) {
            showOverlay()
        } else {
            removeOverlay()
        }
        return true
    }

    private fun effectiveBlocked(): Set<String> {
        val s = HashSet<String>(Store.blocked(this))
        if (Store.locked(this)) {
            // In locked mode, also block the places where you could undo the lock.
            s.add("com.android.settings")
            s.add("com.android.packageinstaller")
            s.add("com.google.android.packageinstaller")
            s.add("com.google.android.permissioncontroller")
        }
        return s
    }

    private fun foregroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L) now - 10000L else lastQuery
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = e.packageName
            }
        }
        lastQuery = now - 1000L
        return lastPkg
    }

    private fun showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#111827"))
        root.setPadding(64, 64, 64, 64)
        root.isClickable = true

        val title = TextView(this)
        title.text = "Stay focused"
        title.textSize = 30f
        title.setTextColor(Color.WHITE)

        val sub = TextView(this)
        sub.text = "This app is blocked until " + fmt(Store.endTime(this))
        sub.textSize = 16f
        sub.setTextColor(Color.LTGRAY)
        sub.setPadding(0, 24, 0, 48)

        val btn = Button(this)
        btn.text = "Go to home screen"
        btn.setOnClickListener { goHome() }

        root.addView(title)
        root.addView(sub)
        root.addView(btn)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(root, lp)
        overlay = root
    }

    private fun removeOverlay() {
        val v = overlay
        if (v != null) {
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
            } catch (e: Exception) {
            }
        }
        overlay = null
    }

    private fun goHome() {
        lastPkg = null
        removeOverlay()
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_HOME)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
