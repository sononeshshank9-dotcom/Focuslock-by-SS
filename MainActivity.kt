package com.focuslock

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageBtn: Button
    private lateinit var overlayBtn: Button
    private lateinit var statusText: TextView
    private lateinit var minutesInput: EditText
    private lateinit var lockSwitch: Switch
    private lateinit var startBtn: Button
    private val boxes = ArrayList<CheckBox>()
    private val selected = HashSet<String>()

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(Store.blocked(this))

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(28), dp(16), dp(16))

        val title = TextView(this)
        title.text = "FocusLock"
        title.textSize = 28f
        title.setTextColor(Color.BLACK)
        root.addView(title)

        statusText = TextView(this)
        statusText.textSize = 16f
        statusText.setPadding(0, dp(8), 0, dp(12))
        root.addView(statusText)

        usageBtn = Button(this)
        usageBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        root.addView(usageBtn)

        overlayBtn = Button(this)
        overlayBtn.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
            )
        }
        root.addView(overlayBtn)

        minutesInput = EditText(this)
        minutesInput.inputType = InputType.TYPE_CLASS_NUMBER
        minutesInput.hint = "Minutes to block (max 1440)"
        minutesInput.setText("60")
        root.addView(minutesInput)

        lockSwitch = Switch(this)
        lockSwitch.text = "Locked mode: can't stop early (also blocks Settings)"
        lockSwitch.setPadding(0, dp(8), 0, dp(8))
        root.addView(lockSwitch)

        startBtn = Button(this)
        startBtn.setOnClickListener { onStartStop() }
        root.addView(startBtn)

        val listTitle = TextView(this)
        listTitle.text = "Apps to block"
        listTitle.textSize = 18f
        listTitle.setTextColor(Color.BLACK)
        listTitle.setPadding(0, dp(20), 0, dp(8))
        root.addView(listTitle)

        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN)
        launcher.addCategory(Intent.CATEGORY_LAUNCHER)
        val items = pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { Pair(it.loadLabel(pm).toString(), it) }
            .sortedBy { it.first.lowercase() }

        for ((label, ri) in items) {
            val pkg = ri.activityInfo.packageName
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL

            val icon = ImageView(this)
            icon.setImageDrawable(ri.loadIcon(pm))
            row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

            val cb = CheckBox(this)
            cb.text = label
            cb.isChecked = selected.contains(pkg)
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(pkg) else selected.remove(pkg)
                Store.setBlocked(this, selected)
            }
            boxes.add(cb)
            row.addView(cb)
            root.addView(row)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        refresh()
    }

    private fun onStartStop() {
        if (Store.active(this)) {
            if (!Store.locked(this)) {
                Store.setEndTime(this, 0L)
                stopService(Intent(this, BlockService::class.java))
            }
        } else {
            if (!hasUsage() || !Settings.canDrawOverlays(this)) {
                toast("Grant both permissions first")
                return
            }
            if (selected.isEmpty()) {
                toast("Select at least one app")
                return
            }
            var mins = minutesInput.text.toString().toLongOrNull() ?: 0L
            if (mins <= 0L) {
                toast("Enter the number of minutes")
                return
            }
            if (mins > 1440L) mins = 1440L
            Store.setBlocked(this, selected)
            Store.setLocked(this, lockSwitch.isChecked)
            Store.setEndTime(this, System.currentTimeMillis() + mins * 60000L)
            startForegroundService(Intent(this, BlockService::class.java))
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun hasUsage(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun refresh() {
        val usage = hasUsage()
        val overlayOk = Settings.canDrawOverlays(this)
        usageBtn.text = if (usage) "Usage Access: allowed" else "1. Allow Usage Access"
        usageBtn.isEnabled = !usage
        overlayBtn.text = if (overlayOk) "Display over other apps: allowed" else "2. Allow display over other apps"
        overlayBtn.isEnabled = !overlayOk

        val active = Store.active(this)
        val locked = Store.locked(this)
        if (active) {
            val left = (Store.endTime(this) - System.currentTimeMillis()) / 1000L
            val until = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(Store.endTime(this)))
            statusText.text = String.format(
                Locale.ENGLISH, "Blocking is ON - %d:%02d:%02d left", left / 3600, (left % 3600) / 60, left % 60
            )
            startBtn.text = if (locked) "Locked until " + until else "Stop session"
            startBtn.isEnabled = !locked
        } else {
            statusText.text = "Blocking is OFF"
            startBtn.text = "Start blocking"
            startBtn.isEnabled = true
        }
        minutesInput.isEnabled = !active
        lockSwitch.isEnabled = !active
        val boxesEnabled = !(active && locked)
        for (b in boxes) b.isEnabled = boxesEnabled
    }
}
