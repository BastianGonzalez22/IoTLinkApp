package cl.iot.iotlink

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

/** Pantalla de Login (criterio 2.1.1.1). Credenciales con hash PBKDF2 + sal y bloqueo tras 5 intentos fallidos. */
class LoginActivity : AppCompatActivity() {
    private val maxFails = 5
    private val lockMs = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val etUser = findViewById<TextInputEditText>(R.id.et_user)
        val etPass = findViewById<TextInputEditText>(R.id.et_pass)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnReg = findViewById<Button>(R.id.btn_register)
        val tvMsg = findViewById<TextView>(R.id.tv_msg)

        fun registered() = prefs.contains("hash")
        fun refresh() {
            btnReg.visibility = if (registered()) View.GONE else View.VISIBLE
            tvMsg.text = if (registered()) "" else "Primer uso: crea tu cuenta local."
        }
        fun pass(): CharArray {
            val e = etPass.text ?: return CharArray(0)
            return CharArray(e.length).also { e.getChars(0, e.length, it, 0) }
        }

        btnReg.setOnClickListener {
            val user = etUser.text.toString().trim()
            val pw = pass()
            if (user.length < 4 || pw.size < 8) {
                tvMsg.text = "Usuario mínimo 4 caracteres y contraseña mínimo 8."
            } else {
                val salt = Security.newSalt()
                prefs.edit()
                    .putString("user", user)
                    .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString("hash", Base64.encodeToString(Security.hashPassword(pw, salt), Base64.NO_WRAP))
                    .apply()
                tvMsg.text = "Cuenta creada. Ya puedes ingresar."
                refresh()
            }
            pw.fill('0')
            etPass.setText("")
        }

        btnLogin.setOnClickListener {
            val now = System.currentTimeMillis()
            val lockUntil = prefs.getLong("lockUntil", 0)
            if (now < lockUntil) {
                tvMsg.text = "Demasiados intentos. Espera ${(lockUntil - now) / 1000 + 1} s."
                return@setOnClickListener
            }
            if (!registered()) { tvMsg.text = "Primero crea una cuenta."; return@setOnClickListener }

            val pw = pass()
            val salt = Base64.decode(prefs.getString("salt", "")!!, Base64.NO_WRAP)
            val stored = Base64.decode(prefs.getString("hash", "")!!, Base64.NO_WRAP)
            val userOk = etUser.text.toString().trim() == prefs.getString("user", null)
            val passOk = Security.constantTimeEquals(Security.hashPassword(pw, salt), stored)
            pw.fill('0')
            etPass.setText("")

            if (userOk && passOk) {
                prefs.edit().putInt("fails", 0).apply()
                Session.user = etUser.text.toString().trim()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                val fails = prefs.getInt("fails", 0) + 1
                if (fails >= maxFails) {
                    prefs.edit().putInt("fails", 0).putLong("lockUntil", now + lockMs).apply()
                    tvMsg.text = "Cuenta bloqueada 30 s por intentos fallidos."
                } else {
                    prefs.edit().putInt("fails", fails).apply()
                    tvMsg.text = "Credenciales inválidas ($fails/$maxFails)." // mensaje genérico
                }
            }
        }
        refresh()
    }
}
