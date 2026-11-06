package cl.iot.iotlink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import javax.crypto.spec.SecretKeySpec

/** Pantalla de enlace: elige modo servidor/cliente, ingresa la clave compartida y navega a las pantallas. */
class MainActivity : AppCompatActivity(), BtLink.Listener {
    private var adapter: BluetoothAdapter? = null
    private var devices: List<BluetoothDevice> = emptyList()
    private lateinit var tvStatus: TextView
    private lateinit var sp: Spinner
    private lateinit var etKey: TextInputEditText

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) loadDevices() else tvStatus.text = "Permiso Bluetooth denegado"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn) { goLogin(); return }
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tv_status)
        sp = findViewById(R.id.sp_devices)
        etKey = findViewById(R.id.et_key)
        adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            val a = readyAdapter() ?: return@setOnClickListener
            val k = linkKey() ?: return@setOnClickListener
            BtLink.startServer(a, k)
        }
        findViewById<Button>(R.id.btn_connect).setOnClickListener {
            val a = readyAdapter() ?: return@setOnClickListener
            val k = linkKey() ?: return@setOnClickListener
            if (devices.isEmpty()) { toast("No hay dispositivos emparejados"); return@setOnClickListener }
            BtLink.connect(a, devices[sp.selectedItemPosition], k)
        }
        findViewById<Button>(R.id.btn_disconnect).setOnClickListener { BtLink.disconnect() }
        findViewById<Button>(R.id.btn_monitor).setOnClickListener { startActivity(Intent(this, MonitoreoActivity::class.java)) }
        findViewById<Button>(R.id.btn_trigger).setOnClickListener { startActivity(Intent(this, ActivadorActivity::class.java)) }
        findViewById<Button>(R.id.btn_logout).setOnClickListener {
            BtLink.disconnect(); Session.clear(); goLogin()
        }
        ensurePermission()
    }

    private fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) else loadDevices()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun loadDevices() {
        val a = adapter
        if (a == null) { tvStatus.text = "Este equipo no tiene Bluetooth"; return }
        devices = a.bondedDevices.toList()
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            devices.map { "${it.name} (${it.address})" })
    }

    private fun readyAdapter(): BluetoothAdapter? {
        val a = adapter
        if (a == null || !a.isEnabled) { toast("Activa el Bluetooth"); return null }
        return a
    }

    private fun linkKey(): SecretKeySpec? {
        val p = etKey.text.toString()
        if (p.length < 8) { toast("La clave compartida debe tener al menos 8 caracteres"); return null }
        return Security.linkKey(p)
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    override fun onStart() { super.onStart(); if (Session.loggedIn) { BtLink.addListener(this); tvStatus.text = BtLink.lastState } }
    override fun onStop() { super.onStop(); BtLink.removeListener(this) }
    override fun onState(text: String) { tvStatus.text = text }
}
