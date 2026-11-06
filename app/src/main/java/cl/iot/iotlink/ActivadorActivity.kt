package cl.iot.iotlink

import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

/** Pantalla Activador (criterio 2.1.1.2): switch, slider, radioButtons y editText que envían datos al otro dispositivo. */
class ActivadorActivity : AppCompatActivity(), BtLink.Listener {
    private lateinit var tvConn: TextView
    private lateinit var tvFeedback: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn) { finish(); return }
        setContentView(R.layout.activity_activador)
        tvConn = findViewById(R.id.tv_conn); tvFeedback = findViewById(R.id.tv_feedback)
        val tvLevel = findViewById<TextView>(R.id.tv_level)
        val slider = findViewById<Slider>(R.id.slider_level)

        findViewById<SwitchMaterial>(R.id.sw_actuator).setOnCheckedChangeListener { v, on ->
            if (v.isPressed) report(BtLink.send("CMD_SWITCH", if (on) "1" else "0"), if (on) "Encender" else "Apagar")
        }
        slider.addOnChangeListener { _, value, _ -> tvLevel.text = "Intensidad: ${value.toInt()} %" }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(s: Slider) {}
            override fun onStopTrackingTouch(s: Slider) {
                report(BtLink.send("CMD_LEVEL", s.value.toInt().toString()), "Intensidad ${s.value.toInt()} %")
            }
        })
        findViewById<RadioGroup>(R.id.rg_mode).setOnCheckedChangeListener { _, id ->
            val mode = when (id) { R.id.rb_auto -> "AUTO"; R.id.rb_eco -> "ECO"; else -> "MANUAL" }
            report(BtLink.send("CMD_MODE", mode), "Modo $mode")
        }
        val etMsg = findViewById<TextInputEditText>(R.id.et_msg)
        findViewById<Button>(R.id.btn_send_msg).setOnClickListener {
            val m = etMsg.text.toString().trim()
            if (m.isNotEmpty()) { report(BtLink.send("MSG", m), "Mensaje enviado"); etMsg.setText("") }
        }
        findViewById<Button>(R.id.btn_ping).setOnClickListener { report(BtLink.ping(), "Ping enviado") }
    }

    private fun report(ok: Boolean, what: String) {
        tvFeedback.text = if (ok) "✔ $what" else "✖ Sin enlace: no se envió"
    }

    override fun onMessage(type: String, value: String) {
        if (type == "RTT") tvFeedback.text = "Latencia ida y vuelta: $value ms"
        if (type == "SEC") tvFeedback.text = "⚠ $value"
    }
    override fun onState(text: String) { tvConn.text = "Enlace: $text" }

    override fun onStart() { super.onStart(); if (Session.loggedIn) { BtLink.addListener(this); tvConn.text = "Enlace: ${BtLink.lastState}" } }
    override fun onStop() { super.onStop(); BtLink.removeListener(this) }
}
