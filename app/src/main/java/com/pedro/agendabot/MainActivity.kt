package com.pedro.agendabot

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var switchBot: Switch

    private val prefs by lazy {
        getSharedPreferences("bot_config", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(40, 70, 40, 40)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "AgendaBot 99"
            textSize = 28f
            setTextColor(Color.BLACK)
            setTypeface(null, 1)
        }

        val desc = TextView(this).apply {
            text = "Liga o robô, abre a tela de Horários abertos da 99 e deixa ele alternar as 3 primeiras datas em loop. Quando aparecer botão amarelo, ele clica e continua procurando outras vagas até você desligar."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(0, 24, 0, 24)
        }

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 20, 0, 20)
        }

        switchBot = Switch(this).apply {
            text = "Ativar robô em loop"
            textSize = 18f
            setTextColor(Color.BLACK)
            setPadding(0, 30, 0, 30)

            setOnCheckedChangeListener { _, isChecked ->
                alterarEstadoBot(isChecked)
            }
        }

        val btnAccessibility = Button(this).apply {
            text = "Abrir acessibilidade"
            textSize = 16f
            setOnClickListener {
                abrirAcessibilidade()
            }
        }

        val btnInstructions = TextView(this).apply {
            text = """
                
                Como usar:
                
                1. Clique em Abrir acessibilidade.
                2. Ative o serviço AgendaBot 99.
                3. Volte para este app e ative o robô.
                4. Abra a tela Horários abertos na 99.
                5. Ele vai clicar nas 3 primeiras datas a cada 3 segundos.
                6. Quando aparecer Quero me cadastrar amarelo, ele clica.
                7. Depois do clique, ele continua procurando outras vagas.
                8. Para desligar, volte aqui e desative o robô.
                
            """.trimIndent()
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.START
        }

        root.addView(title)
        root.addView(desc)
        root.addView(statusText)
        root.addView(switchBot)
        root.addView(btnAccessibility)
        root.addView(btnInstructions)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        atualizarTela()
    }

    private fun alterarEstadoBot(ativo: Boolean) {
        if (ativo && !isAccessibilityEnabled()) {
            prefs.edit()
                .putBoolean("robot_enabled", false)
                .apply()

            Toast.makeText(
                this,
                "Ative o serviço de acessibilidade primeiro.",
                Toast.LENGTH_LONG
            ).show()

            abrirAcessibilidade()
            atualizarTela()
            return
        }

        prefs.edit()
            .putBoolean("robot_enabled", ativo)
            .apply()

        atualizarTela()

        if (ativo) {
            Toast.makeText(
                this,
                "Robô ligado em loop. Abra a tela Horários abertos.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Robô desligado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun atualizarTela() {
        val acessibilidade = isAccessibilityEnabled()
        val roboAtivo = prefs.getBoolean("robot_enabled", false)

        statusText.text = buildString {
            append("Acessibilidade: ")
            append(if (acessibilidade) "ativada" else "desativada")
            append("\n")
            append("Robô: ")
            append(if (roboAtivo && acessibilidade) "ligado em loop" else "desligado")
            append("\n")
            append("Intervalo: 3 segundos")
        }

        switchBot.setOnCheckedChangeListener(null)
        switchBot.isChecked = roboAtivo && acessibilidade
        switchBot.setOnCheckedChangeListener { _, isChecked ->
            alterarEstadoBot(isChecked)
        }
    }

    private fun abrirAcessibilidade() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expectedComponent = ComponentName(
            this,
            AutoClickService::class.java
        ).flattenToString()

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)

        for (service in splitter) {
            if (service.equals(expectedComponent, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
