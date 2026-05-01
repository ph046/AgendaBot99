package com.pedro.agendabot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class AutoClickService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var loopAtivo = false
    private var indiceData = 0
    private var ultimoCliqueData = 0L
    private var screenshotEmAndamento = false

    private val prefs by lazy {
        getSharedPreferences("bot_config", Context.MODE_PRIVATE)
    }

    /*
     * Posições das 3 primeiras datas visíveis.
     */
    private val datas = listOf(
        Pair(0.11f, 0.245f), // 1ª data visível
        Pair(0.24f, 0.245f), // 2ª data visível
        Pair(0.37f, 0.245f)  // 3ª data visível
    )

    private val loop = object : Runnable {
        override fun run() {
            executarCiclo()
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
        screenshotEmAndamento = false
        super.onDestroy()
    }

    private fun iniciarLoopSePrecisar() {
        if (!roboLigado()) return

        if (!loopAtivo) {
            loopAtivo = true
            handler.post(loop)
        }
    }

    private fun executarCiclo() {
        if (!roboLigado()) {
            loopAtivo = false
            screenshotEmAndamento = false
            return
        }

        if (screenshotEmAndamento) {
            handler.postDelayed(loop, 500)
            return
        }

        val root = rootInActiveWindow

        if (root != null && telaCorreta(root)) {
            verificarBotoesAmarelos(root) { clicouEmAlgum ->
                if (!clicouEmAlgum) {
                    clicarProximaData()
                }

                handler.postDelayed(loop, 3000)
            }
        } else {
            handler.postDelayed(loop, 3000)
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
     * Agora ele NÃO clica só pelo texto.
     * Ele primeiro localiza os botões "Quero me cadastrar",
     * depois confere no print se a área do botão está AMARELA.
     */
    private fun verificarBotoesAmarelos(
        root: AccessibilityNodeInfo,
        finalizar: (Boolean) -> Unit
    ) {
        val candidatos = mutableListOf<Rect>()
        coletarBotoesCandidatos(root, candidatos)

        val candidatosUnicos = limparDuplicados(candidatos)

        if (candidatosUnicos.isEmpty()) {
            finalizar(false)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            finalizar(false)
            return
        }

        screenshotEmAndamento = true

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {

                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )

                        val bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)

                        screenshot.hardwareBuffer.close()

                        if (bitmap == null) {
                            screenshotEmAndamento = false
                            finalizar(false)
                            return
                        }

                        val botoesAmarelos = candidatosUnicos.filter { rect ->
                            botaoEstaAmarelo(bitmap, rect)
                        }

                        bitmap.recycle()

                        if (botoesAmarelos.isNotEmpty()) {
                            clicarTodosOsBotoes(botoesAmarelos) {
                                vibrar()
                                screenshotEmAndamento = false
                                finalizar(true)
                            }
                        } else {
                            screenshotEmAndamento = false
                            finalizar(false)
                        }
                    } catch (_: Exception) {
                        screenshotEmAndamento = false
                        finalizar(false)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    screenshotEmAndamento = false
                    finalizar(false)
                }
            }
        )
    }

    private fun coletarBotoesCandidatos(
        node: AccessibilityNodeInfo?,
        lista: MutableList<Rect>
    ) {
        if (node == null) return

        val texto = limparTexto(node.text?.toString() ?: "")
        val desc = limparTexto(node.contentDescription?.toString() ?: "")

        if (texto.contains("quero me cadastrar") || desc.contains("quero me cadastrar")) {
            val alvo = acharPaiClicavel(node) ?: node

            val rect = Rect()
            alvo.getBoundsInScreen(rect)

            if (rect.width() <= 0 || rect.height() <= 0) {
                node.getBoundsInScreen(rect)
            }

            if (rect.width() > 0 && rect.height() > 0) {
                val expandido = expandirAreaDoBotao(rect)
                lista.add(expandido)
            }
        }

        for (i in 0 until node.childCount) {
            coletarBotoesCandidatos(node.getChild(i), lista)
        }
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

    /*
     * Expande a área porque às vezes o texto ocupa só o centro,
     * mas o amarelo está no fundo do botão.
     */
    private fun expandirAreaDoBotao(rectOriginal: Rect): Rect {
        val display = resources.displayMetrics
        val larguraTela = display.widthPixels
        val alturaTela = display.heightPixels

        val larguraMinima = max(260, (larguraTela * 0.38f).toInt())
        val alturaMinima = 70

        val centroX = rectOriginal.centerX()
        val centroY = rectOriginal.centerY()

        val larguraFinal = max(rectOriginal.width(), larguraMinima)
        val alturaFinal = max(rectOriginal.height(), alturaMinima)

        val left = (centroX - larguraFinal / 2).coerceAtLeast(0)
        val right = (centroX + larguraFinal / 2).coerceAtMost(larguraTela - 1)
        val top = (centroY - alturaFinal / 2).coerceAtLeast(0)
        val bottom = (centroY + alturaFinal / 2).coerceAtMost(alturaTela - 1)

        return Rect(left, top, right, bottom)
    }

    /*
     * Detecta amarelo do botão.
     * Cinza não passa nesse filtro.
     */
    private fun botaoEstaAmarelo(bitmap: Bitmap, rectOriginal: Rect): Boolean {
        val rect = Rect(
            rectOriginal.left.coerceAtLeast(0),
            rectOriginal.top.coerceAtLeast(0),
            rectOriginal.right.coerceAtMost(bitmap.width - 1),
            rectOriginal.bottom.coerceAtMost(bitmap.height - 1)
        )

        if (rect.width() <= 0 || rect.height() <= 0) return false

        var total = 0
        var amarelos = 0

        val step = max(3, min(rect.width(), rect.height()) / 14)

        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val pixel = bitmap.getPixel(x, y)

                total++

                if (pixelEhAmarelo(pixel)) {
                    amarelos++
                }

                x += step
            }

            y += step
        }

        if (total == 0) return false

        val proporcao = amarelos.toFloat() / total.toFloat()

        return amarelos >= 8 && proporcao >= 0.04f
    }

    private fun pixelEhAmarelo(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)

        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        return hue in 38f..68f &&
            saturation > 0.45f &&
            value > 0.55f &&
            r > 180 &&
            g > 145 &&
            b < 140
    }

    private fun clicarTodosOsBotoes(
        botoes: List<Rect>,
        finalizar: () -> Unit
    ) {
        val ordenados = botoes.sortedBy { it.top }

        if (ordenados.isEmpty()) {
            finalizar()
            return
        }

        var delay = 0L

        ordenados.forEachIndexed { index, rect ->
            handler.postDelayed({
                clicarNaTela(
                    rect.centerX().toFloat(),
                    rect.centerY().toFloat()
                )

                if (index == ordenados.lastIndex) {
                    finalizar()
                }
            }, delay)

            delay += 600L
        }
    }

    private fun limparDuplicados(lista: List<Rect>): List<Rect> {
        val resultado = mutableListOf<Rect>()

        lista.sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })
            .forEach { rect ->
                val jaExiste = resultado.any { existente ->
                    retangulosParecidos(existente, rect)
                }

                if (!jaExiste) {
                    resultado.add(rect)
                }
            }

        return resultado
    }

    private fun retangulosParecidos(a: Rect, b: Rect): Boolean {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)

        val largura = right - left
        val altura = bottom - top

        if (largura <= 0 || altura <= 0) return false

        val areaIntersecao = largura * altura
        val areaMenor = min(a.width() * a.height(), b.width() * b.height())

        return areaIntersecao > areaMenor * 0.55f
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
