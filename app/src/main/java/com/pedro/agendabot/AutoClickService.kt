package com.pedro.agendabot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.Locale

class AutoClickService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var loopAtivo = false
    private var indiceData = 0
    private var ultimoCliqueBotao = 0L
    private var ultimoCliqueData = 0L

    private val prefs by lazy {
        getSharedPreferences("bot_config", Context.MODE_PRIVATE)
    }

    /*
     * Posições das 3 primeiras datas visíveis na tela.
     *
     * Se no seu celular clicar torto, ajuste só esses valores:
     *
     * 0.11f = primeira data, mais para esquerda
     * 0.24f = segunda data
     * 0.37f = terceira data
     * 0.245f = altura da linha das datas
     */
    private val datas = listOf(
        Pair(0.11f, 0.245f), // 1ª data visível
        Pair(0.24f, 0.245f), // 2ª data visível
        Pair(0.37f, 0.245f)  // 3ª data visível
    )

    private val loop = object : Runnable {
        override fun run() {
            if (!roboLigado()) {
                loopAtivo = false
                return
            }

            val root = rootInActiveWindow

            if (root != null && telaCorreta(root)) {
                val clicouEmVaga = procurarBotaoEClicar(root)

                if (clicouEmVaga) {
                    vibrar()

                    /*
                     * Não desliga sozinho.
                     * Depois de clicar em uma vaga, continua em loop para pegar outras.
                     */
                    handler.postDelayed(this, 3000)
                    return
                }

                clicarProximaData()
            }

            handler.postDelayed(this, 3000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        iniciarLoopSePrecisar()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        iniciarLoopSePrecisar()
    }

    override fun onInterrupt() {
        // Nada aqui.
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        loopAtivo = false
        super.onDestroy()
    }

    private fun iniciarLoopSePrecisar() {
        if (!roboLigado()) return

        if (!loopAtivo) {
            loopAtivo = true
            handler.post(loop)
        }
    }

    private fun roboLigado(): Boolean {
        return prefs.getBoolean("robot_enabled", false)
    }

    private fun telaCorreta(root: AccessibilityNodeInfo): Boolean {
        val textos = mutableListOf<String>()
        coletarTextos(root, textos)

        val textoTela = limparTexto(textos.joinToString(" "))

        val temHorarioAberto = textoTela.contains("horarios abertos")
        val temBotao = textoTela.contains("quero me cadastrar")
        val temPausa = textoTela.contains("pausa")
        val temAgendamento = textoTela.contains("agendamento")

        return temHorarioAberto || (temBotao && temPausa) || temAgendamento
    }

    private fun coletarTextos(node: AccessibilityNodeInfo?, lista: MutableList<String>) {
        if (node == null) return

        node.text?.toString()?.let { lista.add(it) }
        node.contentDescription?.toString()?.let { lista.add(it) }

        for (i in 0 until node.childCount) {
            coletarTextos(node.getChild(i), lista)
        }
    }

    /*
     * Procura qualquer botão "Quero me cadastrar" que esteja ativo.
     * Se encontrar, clica.
     */
    private fun procurarBotaoEClicar(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val texto = limparTexto(node.text?.toString() ?: "")
        val desc = limparTexto(node.contentDescription?.toString() ?: "")

        if (texto.contains("quero me cadastrar") || desc.contains("quero me cadastrar")) {
            val alvo = acharPaiClicavel(node)

            if (alvo != null && alvo.isEnabled) {
                val agora = System.currentTimeMillis()

                // Evita repetir clique no mesmo botão em milissegundos.
                if (agora - ultimoCliqueBotao > 2500) {
                    ultimoCliqueBotao = agora

                    val clicou = alvo.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    if (!clicou) {
                        clicarNoCentroDoNode(alvo)
                    }

                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {
            if (procurarBotaoEClicar(node.getChild(i))) {
                return true
            }
        }

        return false
    }

    private fun acharPaiClicavel(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var atual = node

        repeat(8) {
            if (atual == null) return null

            if (atual!!.isClickable && atual!!.isEnabled) {
                return atual
            }

            atual = atual!!.parent
        }

        return null
    }

    private fun clicarProximaData() {
        val agora = System.currentTimeMillis()

        if (agora - ultimoCliqueData < 2500) return

        ultimoCliqueData = agora

        val display = resources.displayMetrics
        val largura = display.widthPixels
        val altura = display.heightPixels

        val posicao = datas[indiceData]

        val x = largura * posicao.first
        val y = altura * posicao.second

        clicarNaTela(x, y)

        indiceData++

        if (indiceData >= datas.size) {
            indiceData = 0
        }
    }

    private fun clicarNoCentroDoNode(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (rect.width() <= 0 || rect.height() <= 0) return

        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()

        clicarNaTela(x, y)
    }

    private fun clicarNaTela(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    80
                )
            )
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun vibrar() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VibratorManager::class.java)
                val vibrator = manager.defaultVibrator
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        250,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            250,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(250)
                }
            }
        } catch (_: Exception) {
            // Ignora erro de vibração.
        }
    }

    private fun limparTexto(texto: String): String {
        val lower = texto.lowercase(Locale.ROOT)
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return normalized.replace("\\p{Mn}+".toRegex(), "")
    }
}
