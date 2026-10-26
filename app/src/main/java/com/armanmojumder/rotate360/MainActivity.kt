package com.armanmojumder.rotate360

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var btnToggle: MaterialButton
    private lateinit var group: RadioGroup
    private lateinit var swBubble: MaterialSwitch
    private lateinit var tvPerm: TextView
    private lateinit var btnPerm: MaterialButton

    private val radioIds = mutableMapOf<Int, Int>() // modeId -> viewId
    private var binding = false

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startSvc() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvStatusDesc = findViewById(R.id.tvStatusDesc)
        btnToggle = findViewById(R.id.btnToggle)
        group = findViewById(R.id.modeGroup)
        swBubble = findViewById(R.id.swBubble)
        tvPerm = findViewById(R.id.tvPerm)
        btnPerm = findViewById(R.id.btnPerm)

        Modes.all.forEach { m ->
            val rb = MaterialRadioButton(this).apply {
                id = View.generateViewId()
                text = "${m.title}\n${m.desc}"
                textSize = 15f
                setPadding(8, 14, 8, 14)
            }
            radioIds[m.id] = rb.id
            group.addView(rb)
        }

        group.setOnCheckedChangeListener { _, checkedId ->
            if (binding) return@setOnCheckedChangeListener
            val modeId = radioIds.entries.firstOrNull { it.value == checkedId }?.key ?: return@setOnCheckedChangeListener
            Prefs.setMode(this, modeId)
            if (RotationService.running) send(RotationService.ACTION_APPLY)
        }

        swBubble.setOnCheckedChangeListener { _, on ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.setBubble(this, on)
            if (RotationService.running) send(RotationService.ACTION_APPLY)
        }

        btnToggle.setOnClickListener {
            if (RotationService.running) {
                startService(Intent(this, RotationService::class.java).setAction(RotationService.ACTION_STOP))
                Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 400)
            } else {
                tryStart()
            }
        }

        btnPerm.setOnClickListener { openOverlaySettings() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun tryStart() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startSvc()
    }

    private fun startSvc() {
        if (!Settings.canDrawOverlays(this)) return
        send(RotationService.ACTION_START)
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 400)
    }

    private fun send(action: String) {
        ContextCompat.startForegroundService(
            this, Intent(this, RotationService::class.java).setAction(action)
        )
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun refresh() {
        binding = true
        val running = RotationService.running
        tvStatus.text = getString(if (running) R.string.status_on else R.string.status_off)
        tvStatusDesc.text = getString(if (running) R.string.status_on_desc else R.string.status_off_desc)
        btnToggle.text = getString(if (running) R.string.btn_stop else R.string.btn_start)

        radioIds[Prefs.modeId(this)]?.let { group.check(it) }
        swBubble.isChecked = Prefs.bubble(this)

        val granted = Settings.canDrawOverlays(this)
        tvPerm.text = getString(if (granted) R.string.perm_ok else R.string.perm_needed)
        btnPerm.visibility = if (granted) View.GONE else View.VISIBLE
        binding = false
    }
}
