package com.pedro.agendabot

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var acessoText: TextView
    private lateinit var emailInput: EditText
    private lateinit var switchBusca: Switch
    private lateinit var btn2Dias: Button
    private lateinit var btn3Dias: Button
    private lateinit var btn7Dias: Button

    private val prefs by lazy {
        getSharedPreferences("bot_config", Context.MODE_PRIVATE)
    }

    private val backendUrl = "https://vaga-facil-backend.onrender.com"

    private val amarelo99 = Color.parseColor("#FFD400")
    private val amareloEscuro = Color.parseColor("#FFB800")
    private val preto99 = Color.parseColor("#1F1F1F")
    private val cinzaTexto = Color.parseColor("#5F5F5F")
    private val fundoApp = Color.parseColor("#F5F5F5")
    private val verdeDesconto = Color.parseColor("#0A8F38")
    private val vermelhoErro = Color.parseColor("#C62828")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!prefs.contains("days_count")) {
            prefs.edit().putInt("days_count", 2).apply()
        }

        pedirPermissaoNotificacao()

        val scroll = ScrollView(this).apply {
            setBackgroundColor(fundoApp)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        scroll.addView(root)

        root.addView(criarTopo())
        root.addView(espaco(10))
        root.addView(criarCardPlanos())
        root.addView(espaco(10))
        root.addView(criarCardAtivacao())
        root.addView(espaco(10))
        root.addView(criarCardDias())
        root.addView(espaco(10))
        root.addView(criarCardControle())
        root.addView(espaco(10))
        root.addView(criarCardStatus())
        root.addView(espaco(10))
        root.addView(criarCardComoUsar())

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        atualizarTela()
        atualizarSeletorDias()
    }

    private fun pedirPermissaoNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun criarTopo(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(amarelo99, amareloEscuro)
            ).apply {
                cornerRadius = dp(26).toFloat()
            }
            elevation = dp(2).toFloat()
        }

        val linhaTopo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badge = TextView(this).apply {
            text = "99FOOD"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#FFF0A8"))
            }
        }

        val logo99 = TextView(this).apply {
            text = "99"
            textSize = 17f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        }

        val logoLp = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            marginStart = dp(10)
        }

        linhaTopo.addView(badge)
        linhaTopo.addView(logo99, logoLp)

        val title = TextView(this).apply {
            text = "Vaga Fácil"
            textSize = 27f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(0, dp(14), 0, dp(4))
        }

        val desc = TextView(this).apply {
            text = "Sem tempo para acompanhar vagas? O Vaga Fácil monitora horários disponíveis e pega a vaga para você automaticamente."
            textSize = 13.5f
            setTextColor(Color.parseColor("#2A2A2A"))
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        val linhaChips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }

        val chip1 = criarChipTopo("Rápido")
        val chip2 = criarChipTopo("Automático")
        val chip3 = criarChipTopo("Simples")

        linhaChips.addView(chip1)
        linhaChips.addView(espacoHorizontal(8))
        linhaChips.addView(chip2)
        linhaChips.addView(espacoHorizontal(8))
        linhaChips.addView(chip3)

        card.addView(linhaTopo)
        card.addView(title)
        card.addView(desc)
        card.addView(linhaChips)

        return card
    }

    private fun criarChipTopo(texto: String): TextView {
        return TextView(this).apply {
            text = texto
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#FFF0A8"))
            }
        }
    }

    private fun criarCardStatus(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Status")

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(preto99)
            setPadding(0, dp(6), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        card.addView(titulo)
        card.addView(statusText)

        return card
    }

    private fun criarCardAtivacao(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Ativação do acesso")

        val desc = TextView(this).apply {
            text = "O modo 2 dias é grátis. Para liberar 3 dias e 7 dias, use o e-mail do pagamento e toque em Verificar pagamento."
            textSize = 13f
            setTextColor(cinzaTexto)
            setPadding(0, dp(5), 0, dp(10))
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        emailInput = EditText(this).apply {
            hint = "seuemail@gmail.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            textSize = 15f
            setSingleLine(true)
            setTextColor(preto99)
            setHintTextColor(Color.parseColor("#999999"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setText(prefs.getString("user_email", "") ?: "")
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#F7F7F7"))
                setStroke(dp(1), Color.parseColor("#DDDDDD"))
            }
        }

        acessoText = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(10), 0, dp(8))
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        val btnVerificar = criarBotaoSecundario("Verificar pagamento")
        btnVerificar.setOnClickListener {
            verificarPagamento()
        }

        val deviceInfo = TextView(this).apply {
            text = "ID do aparelho: ${obterDeviceId()}"
            textSize = 10.5f
            setTextColor(Color.parseColor("#777777"))
            setPadding(0, dp(8), 0, 0)
        }

        card.addView(titulo)
        card.addView(desc)
        card.addView(emailInput)
        card.addView(acessoText)
        card.addView(btnVerificar)
        card.addView(deviceInfo)

        return card
    }

    private fun criarCardDias(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Modo de busca")

        val desc = TextView(this).apply {
            text = "2 dias é grátis. 3 dias e 7 dias fazem parte dos planos pagos."
            textSize = 13f
            setTextColor(cinzaTexto)
            setPadding(0, dp(5), 0, dp(10))
        }

        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        btn2Dias = criarBotaoSeletor("2 dias")
        btn3Dias = criarBotaoSeletor("3 dias")
        btn7Dias = criarBotaoSeletor("7 dias")

        btn2Dias.setOnClickListener {
            prefs.edit().putInt("days_count", 2).apply()
            atualizarSeletorDias()
            atualizarTela()
            Toast.makeText(this, "Modo 2 dias grátis selecionado.", Toast.LENGTH_SHORT).show()
        }

        btn3Dias.setOnClickListener {
            if (!acessoAtivoLocal()) {
                Toast.makeText(
                    this,
                    "O modo 3 dias faz parte dos planos pagos. Use 2 dias grátis ou assine.",
                    Toast.LENGTH_LONG
                ).show()
                atualizarSeletorDias()
                atualizarTela()
                return@setOnClickListener
            }

            prefs.edit().putInt("days_count", 3).apply()
            atualizarSeletorDias()
            atualizarTela()
            Toast.makeText(this, "Modo 3 dias selecionado.", Toast.LENGTH_SHORT).show()
        }

        btn7Dias.setOnClickListener {
            if (!acessoAtivoLocal()) {
                Toast.makeText(
                    this,
                    "O modo 7 dias faz parte dos planos pagos. Use 2 dias grátis ou assine.",
                    Toast.LENGTH_LONG
                ).show()
                atualizarSeletorDias()
                atualizarTela()
                return@setOnClickListener
            }

            prefs.edit().putInt("days_count", 7).apply()
            atualizarSeletorDias()
            atualizarTela()
            Toast.makeText(this, "Modo 7 dias selecionado.", Toast.LENGTH_SHORT).show()
        }

        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }

        val lp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }

        val lp3 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        linha.addView(btn2Dias, lp1)
        linha.addView(btn3Dias, lp2)
        linha.addView(btn7Dias, lp3)

        card.addView(titulo)
        card.addView(desc)
        card.addView(linha)

        return card
    }

    private fun criarCardPlanos(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Planos")

        val desc = TextView(this).apply {
            text = "Use grátis o modo 2 dias. Assine para liberar os modos 3 dias e 7 dias."
            textSize = 13f
            setTextColor(cinzaTexto)
            setPadding(0, dp(5), 0, dp(10))
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        val planoMensal = criarPlanoMensal()
        val planoTrimestral = criarPlanoTrimestral()

        val aviso = TextView(this).apply {
            text = "Após pagar, volte ao app e toque em Verificar pagamento para liberar os modos pagos."
            textSize = 11f
            setTextColor(Color.parseColor("#777777"))
            setPadding(0, dp(8), 0, 0)
        }

        card.addView(titulo)
        card.addView(desc)
        card.addView(planoMensal)
        card.addView(espaco(10))
        card.addView(planoTrimestral)
        card.addView(aviso)

        return card
    }

    private fun criarPlanoMensal(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#FFFBEA"))
                setStroke(dp(1), Color.parseColor("#FFE58A"))
            }
        }

        val nome = TextView(this).apply {
            text = "Plano Mensal"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
        }

        val preco = TextView(this).apply {
            text = "R$ 9,99/mês"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(0, dp(4), 0, dp(3))
        }

        val detalhe = TextView(this).apply {
            text = "Libera os modos 3 dias e 7 dias."
            textSize = 13f
            setTextColor(cinzaTexto)
        }

        val botao = criarBotaoPrincipal("Assinar mensal")
        botao.setOnClickListener {
            iniciarCheckout("mensal")
        }

        box.addView(nome)
        box.addView(preco)
        box.addView(detalhe)
        box.addView(espaco(8))
        box.addView(botao)

        return box
    }

    private fun criarPlanoTrimestral(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#FFF7CC"))
                setStroke(dp(2), amareloEscuro)
            }
        }

        val badge = TextView(this).apply {
            text = "R$ 2,98 OFF"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(amarelo99)
                setStroke(dp(1), amareloEscuro)
            }
        }

        val nome = TextView(this).apply {
            text = "Plano Trimestral"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(0, dp(8), 0, 0)
        }

        val preco = TextView(this).apply {
            text = "R$ 26,99/trimestre"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(0, dp(4), 0, dp(3))
        }

        val detalhe = TextView(this).apply {
            text = "Melhor opção para deixar os modos pagos ativos por mais tempo."
            textSize = 13f
            setTextColor(cinzaTexto)
        }

        val botao = criarBotaoPrincipal("Assinar trimestral")
        botao.setOnClickListener {
            iniciarCheckout("trimestral")
        }

        box.addView(badge)
        box.addView(nome)
        box.addView(preco)
        box.addView(detalhe)
        box.addView(espaco(8))
        box.addView(botao)

        return box
    }

    private fun criarCardControle(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Controle da busca")

        val desc = TextView(this).apply {
            text = "O modo 2 dias funciona grátis. Para 3 dias e 7 dias, é necessário acesso ativo."
            textSize = 13f
            setTextColor(cinzaTexto)
            setPadding(0, dp(5), 0, dp(10))
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        switchBusca = Switch(this).apply {
            text = "Ativar busca automática"
            textSize = 17f
            setTextColor(preto99)
            setPadding(0, dp(6), 0, dp(10))

            setOnCheckedChangeListener { _, isChecked ->
                alterarEstadoBusca(isChecked)
            }
        }

        val btnAccessibility = criarBotaoSecundario("Abrir acessibilidade")
        btnAccessibility.setOnClickListener {
            abrirAcessibilidade()
        }

        card.addView(titulo)
        card.addView(desc)
        card.addView(switchBusca)
        card.addView(btnAccessibility)

        return card
    }

    private fun criarCardComoUsar(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Como usar")

        val texto = TextView(this).apply {
            text = """
1. O modo 2 dias pode ser usado grátis.
2. Para liberar 3 dias e 7 dias, escolha um plano.
3. Finalize o pagamento no Mercado Pago.
4. Volte ao app e toque em Verificar pagamento.
5. Se a acessibilidade não puder ser ativada, abra as informações do app VagaFacil.
6. Toque no menu de 3 pontos no canto superior direito.
7. Ative a opção "Permitir configurações restritas".
8. Volte e abra a tela de acessibilidade.
9. Ative o serviço do VagaFacil na acessibilidade.
10. Escolha o modo: 2 dias, 3 dias ou 7 dias.
11. Ligue a busca automática.
12. Abra a tela de horários da 99.
13. Para parar, volte aqui e desligue.
            """.trimIndent()
            textSize = 13f
            setTextColor(Color.parseColor("#444444"))
            setLineSpacing(dp(3).toFloat(), 1.0f)
            setPadding(0, dp(6), 0, 0)
        }

        val dicaExtra = TextView(this).apply {
            text = "Dica: em alguns Androids, apps instalados fora da Play Store precisam da permissão de configurações restritas para liberar a acessibilidade."
            textSize = 11f
            setTextColor(Color.parseColor("#777777"))
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        card.addView(titulo)
        card.addView(texto)
        card.addView(dicaExtra)

        return card
    }

    private fun iniciarCheckout(plano: String) {
        val email = emailInput.text.toString().trim().lowercase()

        if (!email.contains("@") || email.length < 6) {
            Toast.makeText(this, "Digite um e-mail válido primeiro.", Toast.LENGTH_LONG).show()
            return
        }

        prefs.edit()
            .putString("user_email", email)
            .putBoolean("license_active", false)
            .apply()

        atualizarTela()

        Toast.makeText(this, "Gerando pagamento...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val json = JSONObject()
                json.put("email", email)
                json.put("deviceId", obterDeviceId())
                json.put("plan", plano)

                val resposta = postJson("$backendUrl/api/create-checkout", json)

                val ok = resposta.optBoolean("ok", false)
                val checkoutUrl = resposta.optString("checkout_url", "")

                runOnUiThread {
                    if (ok && checkoutUrl.isNotBlank()) {
                        abrirLink(checkoutUrl)
                        Toast.makeText(
                            this,
                            "Depois de pagar, volte e toque em Verificar pagamento.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            resposta.optString("error", "Erro ao criar pagamento."),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Erro ao conectar com o servidor: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun verificarPagamento() {
        val email = emailInput.text.toString().trim().lowercase()

        if (!email.contains("@") || email.length < 6) {
            Toast.makeText(this, "Digite o e-mail usado no pagamento.", Toast.LENGTH_LONG).show()
            return
        }

        prefs.edit().putString("user_email", email).apply()

        Toast.makeText(this, "Verificando pagamento...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val encodedEmail = URLEncoder.encode(email, "UTF-8")
                val encodedDevice = URLEncoder.encode(obterDeviceId(), "UTF-8")

                val url = "$backendUrl/api/check-license?email=$encodedEmail&deviceId=$encodedDevice"
                val resposta = getJson(url)

                val active = resposta.optBoolean("active", false)
                val status = resposta.optString("status", "unknown")
                val plan = resposta.optString("plan", "none")
                val expiresAt = resposta.optString("expires_at", "")

                prefs.edit()
                    .putBoolean("license_active", active)
                    .putString("license_status", status)
                    .putString("license_plan", plan)
                    .putString("license_expires_at", expiresAt)
                    .apply()

                runOnUiThread {
                    atualizarTela()

                    if (active) {
                        Toast.makeText(this, "Acesso pago liberado!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Pagamento ainda não liberado. O modo 2 dias continua grátis.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Erro ao verificar pagamento: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun alterarEstadoBusca(ativo: Boolean) {
        if (ativo && !acessoLiberadoParaModo()) {
            prefs.edit()
                .putBoolean("robot_enabled", false)
                .apply()

            Toast.makeText(
                this,
                "Este modo precisa de plano ativo. Use 2 dias grátis ou assine.",
                Toast.LENGTH_LONG
            ).show()

            atualizarTela()
            return
        }

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
                "Busca ligada. Abra a tela de horários.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Busca desligada.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun atualizarTela() {
        val acessibilidade = isAccessibilityEnabled()
        var buscaAtiva = prefs.getBoolean("robot_enabled", false)
        var dias = prefs.getInt("days_count", 2)
        val acessoAtivo = acessoAtivoLocal()

        if (!acessoAtivo && dias != 2) {
            dias = 2
            buscaAtiva = false
            prefs.edit()
                .putInt("days_count", 2)
                .putBoolean("robot_enabled", false)
                .apply()
        }

        val acessoLiberado = dias == 2 || acessoAtivo
        val plano = prefs.getString("license_plan", "none") ?: "none"
        val status = prefs.getString("license_status", "not_found") ?: "not_found"
        val expira = prefs.getString("license_expires_at", "") ?: ""

        statusText.text = buildString {
            append("Acessibilidade: ")
            append(if (acessibilidade) "ativada" else "desativada")
            append("\n")
            append("Acesso: ")
            append(
                when {
                    acessoAtivo -> "pago ativo"
                    dias == 2 -> "modo grátis"
                    else -> "inativo"
                }
            )
            append("\n")
            append("Busca: ")
            append(if (buscaAtiva && acessibilidade && acessoLiberado) "ligada" else "desligada")
            append("\n")
            append("Modo: ")
            append(
                when (dias) {
                    2 -> "2 dias grátis"
                    3 -> "3 dias"
                    else -> "7 dias"
                }
            )
            append("\n")
            append("Intervalo: 3 segundos")
        }

        if (::acessoText.isInitialized) {
            acessoText.text = buildString {
                append("Modo grátis: ")
                append("2 dias liberado")
                append("\n")
                append("Plano pago: ")
                append(if (acessoAtivo) "ATIVO" else "INATIVO")
                append("\n")
                append("Plano: ")
                append(plano)
                append("\n")
                append("Status servidor: ")
                append(status)
                if (expira.isNotBlank()) {
                    append("\n")
                    append("Válido até: ")
                    append(expira.take(10))
                }
            }

            acessoText.setTextColor(if (acessoLiberado) verdeDesconto else vermelhoErro)
        }

        if (::switchBusca.isInitialized) {
            switchBusca.setOnCheckedChangeListener(null)
            switchBusca.isChecked = buscaAtiva && acessibilidade && acessoLiberado
            switchBusca.setOnCheckedChangeListener { _, isChecked ->
                alterarEstadoBusca(isChecked)
            }
        }
    }

    private fun acessoLiberadoParaModo(): Boolean {
        val dias = prefs.getInt("days_count", 2)
        return dias == 2 || acessoAtivoLocal()
    }

    private fun acessoAtivoLocal(): Boolean {
        val active = prefs.getBoolean("license_active", false)
        if (!active) return false

        val expiresAt = prefs.getString("license_expires_at", null) ?: return false

        return try {
            val expira = Instant.parse(expiresAt)
            expira.isAfter(Instant.now())
        } catch (_: Exception) {
            false
        }
    }

    private fun atualizarSeletorDias() {
        val dias = prefs.getInt("days_count", 2)

        if (::btn2Dias.isInitialized && ::btn3Dias.isInitialized && ::btn7Dias.isInitialized) {
            aplicarEstiloSeletor(btn2Dias, dias == 2)
            aplicarEstiloSeletor(btn3Dias, dias == 3)
            aplicarEstiloSeletor(btn7Dias, dias == 7)
        }
    }

    private fun obterDeviceId(): String {
        return Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"
    }

    private fun postJson(urlString: String, body: JSONObject): JSONObject {
        val connection = URL(urlString).openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.connectTimeout = 45000
        connection.readTimeout = 45000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body.toString())
            writer.flush()
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        return JSONObject(text)
    }

    private fun getJson(urlString: String): JSONObject {
        val connection = URL(urlString).openConnection() as HttpURLConnection

        connection.requestMethod = "GET"
        connection.connectTimeout = 45000
        connection.readTimeout = 45000

        val code = connection.responseCode
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        return JSONObject(text)
    }

    private fun abrirAcessibilidade() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun abrirLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show()
        }
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

    private fun criarCardBase(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#E7E7E7"))
            }
        }
    }

    private fun criarTitulo(texto: String): TextView {
        return TextView(this).apply {
            text = texto
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
        }
    }

    private fun criarBotaoPrincipal(texto: String): Button {
        return Button(this).apply {
            text = texto
            textSize = 15f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(amarelo99, amareloEscuro)
            ).apply {
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(14), dp(11), dp(14), dp(11))
        }
    }

    private fun criarBotaoSecundario(texto: String): Button {
        return Button(this).apply {
            text = texto
            textSize = 15f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#FFF7CC"))
                setStroke(dp(1), amareloEscuro)
            }
            setPadding(dp(14), dp(11), dp(14), dp(11))
        }
    }

    private fun criarBotaoSeletor(texto: String): Button {
        return Button(this).apply {
            text = texto
            textSize = 14f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(11), dp(8), dp(11))
        }
    }

    private fun aplicarEstiloSeletor(button: Button, ativo: Boolean) {
        if (ativo) {
            button.setTextColor(preto99)
            button.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(amarelo99, amareloEscuro)
            ).apply {
                cornerRadius = dp(16).toFloat()
            }
        } else {
            button.setTextColor(Color.parseColor("#444444"))
            button.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#F1F1F1"))
                setStroke(dp(1), Color.parseColor("#DDDDDD"))
            }
        }
    }

    private fun espaco(valorDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(valorDp)
            )
        }
    }

    private fun espacoHorizontal(valorDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(valorDp),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun dp(valor: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valor.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
