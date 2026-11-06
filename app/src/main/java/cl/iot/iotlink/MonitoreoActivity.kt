package cl.iot.iotlink

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pantalla Monitoreo (criterio 2.1.1.2): muestra datos y comandos recibidos; opcionalmente envía telemetría real del equipo. */
class MonitoreoActivity : AppCompatActivity(), BtLink.Listener {
    private lateinit var tvConn: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvBat: TextView
    private lateinit var tvAct: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var swTel: SwitchMaterial
    private val handler = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val log = ArrayDeque<String>()

    private var actOn = false
    private var actLevel = 0
    private var actMode = "MANUAL"

    private val telemetry = object : Runnable {
        override fun run() { sendTelemetry(); handler.postDelayed(this, 2000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn) { finish(); return }
        setContentView(R.layout.activity_monitoreo)
        tvConn = findViewById(R.id.tv_conn); tvTemp = findViewById(R.id.tv_temp); tvBat = findViewById(R.id.tv_bat)
        tvAct = findViewById(R.id.tv_act); tvStats = findViewById(R.id.tv_stats); tvLog = findViewById(R.id.tv_log)
        swTel = findViewById(R.id.sw_telemetry)
        swTel.setOnCheckedChangeListener { _, on ->
            handler.removeCallbacks(telemetry)
            if (on) {
                if (!BtLink.connected) { Toast.makeText(this, "Sin enlace", Toast.LENGTH_SHORT).show(); swTel.isChecked = false }
                else handler.post(telemetry)
            }
        }
        render()
    }

    private fun sendTelemetry() {
        val i: Intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        BtLink.send("TEL_TEMP", String.format(Locale.US, "%.1f", temp))
        BtLink.send("TEL_BAT", level.toString())
    }

    override fun onMessage(type: String, value: String) {
        when (type) {
            "TEL_TEMP" -> tvTemp.text = "Temperatura: $value °C"
            "TEL_BAT" -> tvBat.text = "Nivel: $value %"
            "CMD_SWITCH" -> if (value == "1" || value == "0") actOn = value == "1"
            "CMD_LEVEL" -> value.toIntOrNull()?.takeIf { it in 0..100 }?.let { actLevel = it }
            "CMD_MODE" -> if (value in setOf("MANUAL", "AUTO", "ECO")) actMode = value
        }
        if (type != "TEL_TEMP" && type != "TEL_BAT") addLog("$type = $value")
        render()
    }

    override fun onState(text: String) { tvConn.text = "Enlace: $text"; render() }

    private fun render() {
        tvConn.text = "Enlace: ${BtLink.lastState}"
        tvAct.text = "${if (actOn) "ENCENDIDO" else "APAGADO"} · $actLevel % · $actMode"
        tvStats.text = BtLink.statsText()
    }

    private fun addLog(s: String) {
        log.addFirst("${fmt.format(Date())}  $s")
        while (log.size > 50) log.removeLast()
        tvLog.text = log.joinToString("\n")
    }

    override fun onStart() { super.onStart(); if (Session.loggedIn) { BtLink.addListener(this); render() } }
    override fun onStop() {
        super.onStop(); BtLink.removeListener(this)
        handler.removeCallbacks(telemetry); swTel.isChecked = false
    }
}
