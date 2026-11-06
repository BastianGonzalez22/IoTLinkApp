package cl.iot.iotlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * Enlace Bluetooth clásico (RFCOMM seguro, solo dispositivos emparejados) entre dos teléfonos.
 * Capa de aplicación: cada trama va cifrada con AES-256-GCM y lleva número de secuencia (anti-repetición).
 * Un teléfono actúa de servidor (espera) y el otro de cliente (conecta, con reintentos automáticos).
 */
@SuppressLint("MissingPermission") // el permiso BLUETOOTH_CONNECT se solicita en MainActivity
object BtLink {
    val APP_UUID: UUID = UUID.fromString("8c1b0c1e-5f3a-4e0a-9d77-2a5b6c3d1e42")
    private const val MAX_RETRY = 5
    private val ALLOWED = setOf("TEL_TEMP", "TEL_BAT", "CMD_SWITCH", "CMD_LEVEL", "CMD_MODE", "MSG", "PING", "PONG")

    interface Listener {
        fun onState(text: String) {}
        fun onMessage(type: String, value: String) {}
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val main = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadExecutor()
    private val sendLock = Any()

    @Volatile var connected = false
        private set
    @Volatile var lastState = "Desconectado"
        private set
    @Volatile private var running = false
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var key: SecretKeySpec? = null
    private var sendSeq = 0L
    private var recvSeq = 0L

    // Estadísticas para el informe de estabilidad (criterio 2.1.4.8)
    val sent = AtomicInteger(); val received = AtomicInteger()
    val rejected = AtomicInteger(); val drops = AtomicInteger()
    @Volatile var lastRttMs = -1L

    fun addListener(l: Listener) { listeners.addIfAbsent(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private fun state(t: String) { lastState = t; main.post { listeners.forEach { it.onState(t) } } }
    private fun deliver(type: String, value: String) { main.post { listeners.forEach { it.onMessage(type, value) } } }

    private fun resetStats() { sent.set(0); received.set(0); rejected.set(0); drops.set(0); lastRttMs = -1 }

    /** Modo servidor: espera conexión y vuelve a escuchar si el enlace cae. */
    fun startServer(adapter: BluetoothAdapter, k: SecretKeySpec) {
        disconnect(); key = k; running = true; resetStats()
        thread(name = "bt-accept") {
            while (running) {
                try {
                    state("Esperando conexión…")
                    val ss = adapter.listenUsingRfcommWithServiceRecord("IoTLink", APP_UUID)
                    serverSocket = ss
                    val s = ss.accept()
                    ss.close(); serverSocket = null
                    attach(s)
                    if (running) drops.incrementAndGet()
                } catch (e: IOException) {
                    if (running) { state("Error de servidor: ${e.message}"); Thread.sleep(1500) }
                }
            }
        }
    }

    /** Modo cliente: conecta al dispositivo emparejado; reintenta con espera creciente. */
    fun connect(adapter: BluetoothAdapter, device: BluetoothDevice, k: SecretKeySpec) {
        disconnect(); key = k; running = true; resetStats()
        thread(name = "bt-connect") {
            var attempts = 0
            while (running && attempts <= MAX_RETRY) {
                try {
                    state(if (attempts == 0) "Conectando…" else "Reintentando ($attempts/$MAX_RETRY)…")
                    adapter.cancelDiscovery()
                    val s = device.createRfcommSocketToServiceRecord(APP_UUID) // canal seguro (cifrado/autenticado)
                    s.connect()
                    attempts = 0
                    attach(s)
                    if (running) drops.incrementAndGet()
                } catch (e: IOException) { /* se reintenta abajo */ }
                if (!running) break
                attempts++
                try { Thread.sleep(2000L * attempts) } catch (_: InterruptedException) { break }
            }
            if (running) { running = false; state("No se pudo (re)conectar") }
        }
    }

    private fun attach(s: BluetoothSocket) {
        val k = key ?: return
        socket = s
        synchronized(sendLock) { sendSeq = 0 }
        recvSeq = 0
        out = s.outputStream
        connected = true
        state("Conectado con ${s.remoteDevice.name ?: s.remoteDevice.address}")
        var bad = 0
        try {
            val reader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                if (line.length > 2048) { rejected.incrementAndGet(); continue }
                val plain = Security.decrypt(k, line)
                if (plain == null) {
                    rejected.incrementAndGet(); bad++
                    deliver("SEC", "Trama rechazada: autenticación fallida")
                    if (bad >= 3) break // clave distinta o intento de inyección: se corta el enlace
                    continue
                }
                val parts = plain.split("|", limit = 3)
                val seq = parts.getOrNull(0)?.toLongOrNull()
                if (parts.size < 3 || seq == null || seq <= recvSeq || parts[1] !in ALLOWED || parts[2].length > 200) {
                    rejected.incrementAndGet(); bad++
                    deliver("SEC", "Trama rechazada: repetida o inválida")
                    if (bad >= 3) break
                    continue
                }
                recvSeq = seq; bad = 0; received.incrementAndGet()
                handle(parts[1], parts[2])
            }
        } catch (_: IOException) {
        } finally {
            try { s.close() } catch (_: IOException) {}
            connected = false; out = null; socket = null
            state(if (running) "Enlace perdido" else "Desconectado")
        }
    }

    private fun handle(type: String, value: String) {
        when (type) {
            "PING" -> send("PONG", value)
            "PONG" -> {
                val t0 = value.toLongOrNull() ?: return
                lastRttMs = (System.nanoTime() - t0) / 1_000_000
                deliver("RTT", lastRttMs.toString())
            }
            else -> deliver(type, value)
        }
    }

    fun send(type: String, value: String): Boolean {
        val o = out; val k = key
        if (!connected || o == null || k == null) return false
        sender.execute {
            try {
                synchronized(sendLock) {
                    val seq = ++sendSeq
                    val clean = value.replace('\n', ' ').take(200)
                    val frame = Security.encrypt(k, "$seq|$type|$clean")
                    o.write((frame + "\n").toByteArray(Charsets.UTF_8)); o.flush()
                    sent.incrementAndGet()
                }
            } catch (_: IOException) {
                try { socket?.close() } catch (_: IOException) {}
            }
        }
        return true
    }

    fun ping() = send("PING", System.nanoTime().toString())

    fun disconnect() {
        running = false
        try { serverSocket?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        serverSocket = null
        connected = false
        state("Desconectado")
    }

    fun statsText() =
        "Enviados: ${sent.get()} · Recibidos: ${received.get()} · Rechazados: ${rejected.get()} · Caídas: ${drops.get()}" +
            if (lastRttMs >= 0) " · RTT: $lastRttMs ms" else ""
}
