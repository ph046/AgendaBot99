package com.pedro.agendabot

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var switchBusca: Switch
    private lateinit var btn3Dias: Button
    private lateinit var btn7Dias: Button

    private val prefs by lazy {
        getSharedPreferences("bot_config", Context.MODE_PRIVATE)
    }

    /*
     * LINKS REAIS DO MERCADO PAGO.
     */
    private val linkMensal = "https://www.mercadopago.com.br/subscriptions/checkout?preapproval_plan_id=9786832ee8224e78b048956df6963dc2"
    private val linkTrimestral = "https://www.mercadopago.com.br/subscriptions/checkout?preapproval_plan_id=ca92e94590464e44b834d5bb61454732"

    private val amarelo99 = Color.parseColor("#FFD400")
    private val amareloEscuro = Color.parseColor("#FFB800")
    private val preto99 = Color.parseColor("#1F1F1F")
    private val cinzaTexto = Color.parseColor("#5F5F5F")
    private val fundoApp = Color.parseColor("#F5F5F5")
    private val verdeDesconto = Color.parseColor("#0A8F38")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!prefs.contains("days_count")) {
            prefs.edit().putInt("days_count", 3).apply()
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(fundoApp)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        scroll.addView(root)

        root.addView(criarTopo())
        root.addView(espaco(14))
        root.addView(criarCardStatus())
        root.addView(espaco(14))
        root.addView(criarCardDias())
        root.addView(espaco(14))
        root.addView(criarCardPlanos())
        root.addView(espaco(14))
        root.addView(criarCardControle())
        root.addView(espaco(14))
        root.addView(criarCardComoUsar())

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        atualizarTela()
        atualizarSeletorDias()
    }

    private fun criarTopo(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(amarelo99, amareloEscuro)
            ).apply {
                cornerRadius = dp(24).toFloat()
            }
        }

        val badge = TextView(this).apply {
            text = "99 ENTREGA FOOD"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#44FFFFFF"))
            }
        }

        val title = TextView(this).apply {
            text = "Vaga Fácil"
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(0, dp(12), 0, dp(4))
        }

        val desc = TextView(this).apply {
            text = "Monitore horários e pegue vaga quando o botão disponível aparecer."
            textSize = 15f
            setTextColor(Color.parseColor("#2A2A2A"))
            setLineSpacing(dp(3).toFloat(), 1.0f)
        }

        card.addView(badge)
        card.addView(title)
        card.addView(desc)

        return card
    }

    private fun criarCardStatus(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Status")

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(preto99)
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        card.addView(titulo)
        card.addView(statusText)

        return card
    }

    private fun criarCardDias(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Modo de busca")

        val desc = TextView(this).apply {
            text = "Escolha quantos dias a busca deve verificar em loop."
            textSize = 14f
            setTextColor(cinzaTexto)
            setPadding(0, dp(6), 0, dp(12))
        }

        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        btn3Dias = criarBotaoSeletor("3 dias")
        btn7Dias = criarBotaoSeletor("7 dias")

        btn3Dias.setOnClickListener {
            prefs.edit()
                .putInt("days_count", 3)
                .apply()

            atualizarSeletorDias()
            atualizarTela()

            Toast.makeText(this, "Modo 3 dias selecionado.", Toast.LENGTH_SHORT).show()
        }

        btn7Dias.setOnClickListener {
            prefs.edit()
                .putInt("days_count", 7)
                .apply()

            atualizarSeletorDias()
            atualizarTela()

            Toast.makeText(this, "Modo 7 dias selecionado.", Toast.LENGTH_SHORT).show()
        }

        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        }

        val lp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        linha.addView(btn3Dias, lp1)
        linha.addView(btn7Dias, lp2)

        card.addView(titulo)
        card.addView(desc)
        card.addView(linha)

        return card
    }

    private fun criarCardPlanos(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Planos")

        val desc = TextView(this).apply {
            text = "Escolha o plano e finalize pelo Mercado Pago."
            textSize = 14f
            setTextColor(cinzaTexto)
            setPadding(0, dp(6), 0, dp(12))
        }

        val planoMensal = criarPlanoMensal()
        val planoTrimestral = criarPlanoTrimestral()

        val aviso = TextView(this).apply {
            text = "Após o pagamento, aguarde a liberação conforme combinado com o vendedor."
            textSize = 12f
            setTextColor(Color.parseColor("#777777"))
            setPadding(0, dp(10), 0, 0)
        }

        card.addView(titulo)
        card.addView(desc)
        card.addView(planoMensal)
        card.addView(espaco(12))
        card.addView(planoTrimestral)
        card.addView(aviso)

        return card
    }

    private fun criarPlanoMensal(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#FFFBEA"))
                setStroke(dp(1), Color.parseColor("#FFE58A"))
            }
        }

        val nome = TextView(this).apply {
            text = "Plano Mensal"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
        }

        val preco = TextView(this).apply {
            text = "R$ 9,99/mês"
            textSize = 27f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(0, dp(6), 0, dp(4))
        }

        val detalhe = TextView(this).apply {
            text = "Para quem quer começar pagando pouco."
            textSize = 14f
            setTextColor(cinzaTexto)
        }

        val botao = criarBotaoPrincipal("Assinar mensal")
        botao.setOnClickListener {
            abrirLink(linkMensal)
        }

        box.addView(nome)
        box.addView(preco)
        box.addView(detalhe)
        box.addView(espaco(10))
        box.addView(botao)

        return box
    }

    private fun criarPlanoTrimestral(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#FFF7CC"))
                setStroke(dp(2), amareloEscuro)
            }
        }

        val badge = TextView(this).apply {
            text = "MAIS VANTAJOSO"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(amarelo99)
                setStroke(dp(1), amareloEscuro)
            }
        }

        val nome = TextView(this).apply {
            text = "Plano Trimestral"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(0, dp(10), 0, 0)
        }

        val preco = TextView(this).apply {
            text = "R$ 26,99/trimestre"
            textSize = 27f
            setTypeface(null, Typeface.BOLD)
            setTextColor(preto99)
            setPadding(0, dp(6), 0, dp(4))
        }

        val desconto = TextView(this).apply {
            text = "Economize R$ 2,98 comparado com 3 mensalidades."
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(verdeDesconto)
            setPadding(0, dp(2), 0, dp(4))
        }

        val detalhe = TextView(this).apply {
            text = "Melhor opção para deixar ativo por mais tempo."
            textSize = 14f
            setTextColor(cinzaTexto)
        }

        val botao = criarBotaoPrincipal("Assinar trimestral")
        botao.setOnClickListener {
            abrirLink(linkTrimestral)
        }

        box.addView(badge)
        box.addView(nome)
        box.addView(preco)
        box.addView(desconto)
        box.addView(detalhe)
        box.addView(espaco(10))
        box.addView(botao)

        return box
    }

    private fun criarCardControle(): View {
        val card = criarCardBase()

        val titulo = criarTitulo("Controle da busca")

        val desc = TextView(this).apply {
            text = "Ative a acessibilidade antes de ligar a busca automática."
            textSize = 14f
            setTextColor(cinzaTexto)
            setPadding(0, dp(6), 0, dp(12))
        }

        switchBusca = Switch(this).apply {
            text = "Ativar busca automática"
            textSize = 18f
            setTextColor(preto99)
            setPadding(0, dp(8), 0, dp(12))

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
1. Ative a acessibilidade do Vaga Fácil.
2. Escolha o modo: 3 dias ou 7 dias.
3. Abra a tela de horários da 99.
4. Ligue a busca automática.
5. O app procura botões amarelos disponíveis.
6. Para parar, volte aqui e desligue.
            """.trimIndent()
            textSize = 14f
            setTextColor(Color.parseColor("#444444"))
            setLineSpacing(dp(4).toFloat(), 1.0f)
            setPadding(0, dp(8), 0, 0)
        }

        card.addView(titulo)
        card.addView(texto)

        return card
    }

    private fun alterarEstadoBusca(ativo: Boolean) {
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
        val buscaAtiva = prefs.getBoolean("robot_enabled", false)
        val dias = prefs.getInt("days_count", 3)

        statusText.text = buildString {
            append("Acessibilidade: ")
            append(if (acessibilidade) "ativada" else "desativada")
            append("\n")
            append("Busca: ")
            append(if (buscaAtiva && acessibilidade) "ligada" else "desligada")
            append("\n")
            append("Modo: ")
            append("$dias dias")
            append("\n")
            append("Intervalo: 3 segundos")
        }

        if (::switchBusca.isInitialized) {
            switchBusca.setOnCheckedChangeListener(null)
            switchBusca.isChecked = buscaAtiva && acessibilidade
            switchBusca.setOnCheckedChangeListener { _, isChecked ->
                alterarEstadoBusca(isChecked)
            }
        }
    }

    private fun atualizarSeletorDias() {
        val dias = prefs.getInt("days_count", 3)

        if (::btn3Dias.isInitialized && ::btn7Dias.isInitialized) {
            aplicarEstiloSeletor(btn3Dias, dias == 3)
            aplicarEstiloSeletor(btn7Dias, dias == 7)
        }
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
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#E7E7E7"))
            }
        }
    }

    private fun criarTitulo(texto: String): TextView {
        return TextView(this).apply {
            text = texto
            textSize = 19f
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
            setPadding(dp(14), dp(12), dp(14), dp(12))
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
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
    }

    private fun criarBotaoSeletor(texto: String): Button {
        return Button(this).apply {
            text = texto
            textSize = 16f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(14), dp(12), dp(14), dp(12))
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

    private fun dp(valor: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valor.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
