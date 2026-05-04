package com.pedro.agendabot

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import java.time.Instant
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class AutoClickService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var loopAtivo = false
    private var indiceData = 0
    private var ultimoCliqueData = 0L
    private var screenshotEmAndamento = false
    private var ultimaNotificacao = 0L

    private var screenshotToken = 0L
    private var ultimoInicioScreenshot = 0L
    private var ultimoCicloExecutado = 0L
    private var watchdogAtivo = false

    private val notificationChannelId = "vaga_facil_vagas"
    private val notificationId = 9901

    private val maxTextosPorTela = 250
    private val maxCandidatosPorTela = 40

    private val intervaloLoopMs = 3000L
    private val screenshotTimeoutMs = 8000L
    private val watchdogIntervalMs = 5000L
    private val maxTempoSemCicloMs = 12000L

    private val prefs by lazy {
        getSharedPreferences("bot_config", Context.MODE_PRIVATE)
    }

    private val loop = object : Runnable {
        override fun run() {
            executarCicloSeguro()
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            try {
                if (!roboLigado()) {
                    watchdogAtivo = false
                    screenshotEmAndamento = false
                    loopAtivo = false
                    return
                }

                val agora = System.currentTimeMillis()

                if (screenshotEmAndamento && agora - ultimoInicioScreenshot > screenshotTimeoutMs) {
                    screenshotEmAndamento = false
                }

                if (loopAtivo && ultimoCicloExecutado > 0 && agora - ultimoCicloExecutado > maxTempoSemCicloMs) {
                    screenshotEmAndamento = false
                    handler.removeCallbacks(loop)
                    handler.post(loop)
                }

                handler.postDelayed(this, watchdogIntervalMs)
            } catch (_: Exception) {
                watchdogAtivo = false
                screenshotEmAndamento = false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        try {
            criarCanalNotificacao()
            iniciarLoopSePrecisar()
        } catch (_: Exception) {
            loopAtivo = false
            screenshotEmAndamento = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            iniciarLoopSePrecisar()
        } catch (_: Exception) {
            loopAtivo = false
            screenshotEmAndamento = false
        }
    }

    override fun onInterrupt() {
        // Não derruba o app.
    }

    override fun onDestroy() {
        try {
            handler.removeCallbacksAndMessages(null)
        } catch (_: Exception) {
            // Ignora.
        }

        loopAtivo = false
        watchdogAtivo = false
        screenshotEmAndamento = false

        super.onDestroy()
    }

    private fun iniciarLoopSePrecisar() {
        if (!roboLigado()) return

        iniciarWatchdogSePrecisar()

        if (!loopAtivo) {
            loopAtivo = true
            handler.removeCallbacks(loop)
            handler.post(loop)
        }
    }

    private fun iniciarWatchdogSePrecisar() {
        if (!watchdogAtivo) {
            watchdogAtivo = true
            handler.removeCallbacks(watchdog)
            handler.postDelayed(watchdog, watchdogIntervalMs)
        }
    }

    private fun executarCicloSeguro() {
        try {
            ultimoCicloExecutado = System.currentTimeMillis()
            executarCiclo()
        } catch (_: Exception) {
            screenshotEmAndamento = false

            if (roboLigado()) {
                agendarProximoCiclo(intervaloLoopMs)
            } else {
                loopAtivo = false
            }
        }
    }

    private fun executarCiclo() {
        if (!roboLigado()) {
            desligarRoboLocal()
            loopAtivo = false
            screenshotEmAndamento = false
            return
        }

        iniciarWatchdogSePrecisar()

        if (screenshotEmAndamento) {
            val agora = System.currentTimeMillis()

            if (agora - ultimoInicioScreenshot > screenshotTimeoutMs) {
                screenshotEmAndamento = false
            } else {
                agendarProximoCiclo(700)
                return
            }
        }

        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }

        if (root != null && telaCorreta(root)) {
            verificarBotoesAmarelos(root) { clicouEmAlgum ->
                try {
                    if (!clicouEmAlgum) {
                        clicarProximaData()
                    }
                } catch (_: Exception) {
                    // Ignora erro no clique de data para não derrubar o serviço.
                }

                agendarProximoCiclo(intervaloLoopMs)
            }
        } else {
            agendarProximoCiclo(intervaloLoopMs)
        }
    }

    private fun agendarProximoCiclo(delay: Long) {
        try {
            if (roboLigado()) {
                loopAtivo = true
                iniciarWatchdogSePrecisar()
                handler.removeCallbacks(loop)
                handler.postDelayed(loop, delay)
            } else {
                loopAtivo = false
                screenshotEmAndamento = false
            }
        } catch (_: Exception) {
            loopAtivo = false
            screenshotEmAndamento = false
        }
    }

    private fun roboLigado(): Boolean {
        return try {
            prefs.getBoolean("robot_enabled", false) && acessoLiberadoParaModo()
        } catch (_: Exception) {
            false
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

    private fun desligarRoboLocal() {
        try {
            prefs.edit()
                .putBoolean("robot_enabled", false)
                .apply()
        } catch (_: Exception) {
            // Ignora.
        }
    }

    private fun telaCorreta(root: AccessibilityNodeInfo): Boolean {
        return try {
            val textos = mutableListOf<String>()
            coletarTextos(root, textos)

            val textoTela = limparTexto(textos.joinToString(" "))

            val temHorarioAberto = textoTela.contains("horarios abertos")
            val temBotao = textoTela.contains("quero me cadastrar")
            val temPausa = textoTela.contains("pausa")
            val temAgendamento = textoTela.contains("agendamento")

            temHorarioAberto || (temBotao && temPausa) || temAgendamento
        } catch (_: Exception) {
            false
        }
    }

    private fun coletarTextos(node: AccessibilityNodeInfo?, lista: MutableList<String>) {
        if (node == null) return
        if (lista.size >= maxTextosPorTela) return

        try {
            node.text?.toString()?.let {
                if (it.isNotBlank() && lista.size < maxTextosPorTela) {
                    lista.add(it)
                }
            }

            node.contentDescription?.toString()?.let {
                if (it.isNotBlank() && lista.size < maxTextosPorTela) {
                    lista.add(it)
                }
            }
        } catch (_: Exception) {
            // Ignora texto problemático.
        }

        val filhos = try {
            node.childCount
        } catch (_: Exception) {
            0
        }

        for (i in 0 until filhos) {
            if (lista.size >= maxTextosPorTela) return

            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }

            coletarTextos(child, lista)
        }
    }

    private fun verificarBotoesAmarelos(
        root: AccessibilityNodeInfo,
        finalizar: (Boolean) -> Unit
    ) {
        var finalizou = false

        fun finalizarSeguro(clicou: Boolean) {
            if (finalizou) return

            finalizou = true
            screenshotEmAndamento = false

            try {
                finalizar(clicou)
            } catch (_: Exception) {
                agendarProximoCiclo(intervaloLoopMs)
            }
        }

        try {
            val candidatos = mutableListOf<Rect>()
            coletarBotoesCandidatos(root, candidatos)

            val candidatosUnicos = limparDuplicados(candidatos)

            if (candidatosUnicos.isEmpty()) {
                finalizarSeguro(false)
                return
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                finalizarSeguro(false)
                return
            }

            screenshotEmAndamento = true
            ultimoInicioScreenshot = System.currentTimeMillis()
            screenshotToken = ultimoInicioScreenshot

            val tokenAtual = screenshotToken

            handler.postDelayed({
                if (!finalizou && screenshotEmAndamento && screenshotToken == tokenAtual) {
                    finalizarSeguro(false)
                }
            }, screenshotTimeoutMs)

            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {

                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            var bitmap: Bitmap? = null
                            var bufferFechado = false

                            fun fecharBuffer() {
                                if (!bufferFechado) {
                                    try {
                                        screenshot.hardwareBuffer.close()
                                    } catch (_: Exception) {
                                        // Ignora.
                                    }

                                    bufferFechado = true
                                }
                            }

                            try {
                                if (finalizou || screenshotToken != tokenAtual) {
                                    fecharBuffer()
                                    return
                                }

                                val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer,
                                    screenshot.colorSpace
                                )

                                bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)

                                fecharBuffer()

                                if (bitmap == null) {
                                    finalizarSeguro(false)
                                    return
                                }

                                val botoesAmarelos = candidatosUnicos.filter { rect ->
                                    botaoEstaAmarelo(bitmap!!, rect)
                                }

                                if (botoesAmarelos.isNotEmpty()) {
                                    clicarTodosOsBotoes(botoesAmarelos) {
                                        vibrar()
                                        enviarNotificacaoVagaPegada()
                                        finalizarSeguro(true)
                                    }
                                } else {
                                    finalizarSeguro(false)
                                }
                            } catch (_: Exception) {
                                finalizarSeguro(false)
                            } finally {
                                try {
                                    bitmap?.recycle()
                                } catch (_: Exception) {
                                    // Ignora.
                                }

                                fecharBuffer()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            finalizarSeguro(false)
                        }
                    }
                )
            } catch (_: Exception) {
                finalizarSeguro(false)
            }
        } catch (_: Exception) {
            finalizarSeguro(false)
        }
    }

    private fun coletarBotoesCandidatos(
        node: AccessibilityNodeInfo?,
        lista: MutableList<Rect>
    ) {
        if (node == null) return
        if (lista.size >= maxCandidatosPorTela) return

        try {
            val texto = limparTexto(node.text?.toString() ?: "")
            val desc = limparTexto(node.contentDescription?.toString() ?: "")

            if (texto.contains("quero me cadastrar") || desc.contains("quero me cadastrar")) {
                val alvo = acharPaiClicavel(node) ?: node

                val rect = Rect()

                try {
                    alvo.getBoundsInScreen(rect)
                } catch (_: Exception) {
                    // Ignora.
                }

                if (rect.width() <= 0 || rect.height() <= 0) {
                    try {
                        node.getBoundsInScreen(rect)
                    } catch (_: Exception) {
                        // Ignora.
                    }
                }

                if (rect.width() > 0 && rect.height() > 0) {
                    val expandido = expandirAreaDoBotao(rect)
                    lista.add(expandido)
                }
            }
        } catch (_: Exception) {
            // Ignora nó problemático.
        }

        val filhos = try {
            node.childCount
        } catch (_: Exception) {
            0
        }

        for (i in 0 until filhos) {
            if (lista.size >= maxCandidatosPorTela) return

            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }

            coletarBotoesCandidatos(child, lista)
        }
    }

    private fun acharPaiClicavel(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var atual = node

        repeat(8) {
            try {
                if (atual == null) return null

                if (atual!!.isClickable && atual!!.isEnabled) {
                    return atual
                }

                atual = atual!!.parent
            } catch (_: Exception) {
                return null
            }
        }

        return null
    }

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

    private fun botaoEstaAmarelo(bitmap: Bitmap, rectOriginal: Rect): Boolean {
        return try {
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

            amarelos >= 8 && proporcao >= 0.04f
        } catch (_: Exception) {
            false
        }
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
        var jaFinalizou = false

        fun finalizarUmaVez() {
            if (!jaFinalizou) {
                jaFinalizou = true
                finalizar()
            }
        }

        ordenados.forEachIndexed { index, rect ->
            handler.postDelayed({
                try {
                    if (!roboLigado()) {
                        finalizarUmaVez()
                        return@postDelayed
                    }

                    clicarNaTela(
                        rect.centerX().toFloat(),
                        rect.centerY().toFloat()
                    )

                    if (index == ordenados.lastIndex) {
                        finalizarUmaVez()
                    }
                } catch (_: Exception) {
                    finalizarUmaVez()
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

        val datas = obterDatasConfiguradas()

        if (datas.isEmpty()) return

        if (indiceData >= datas.size) {
            indiceData = 0
        }

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

    private fun obterDatasConfiguradas(): List<Pair<Float, Float>> {
        val quantidadeDias = prefs.getInt("days_count", 2)

        return when {
            quantidadeDias >= 7 -> {
                listOf(
                    Pair(0.11f, 0.245f),
                    Pair(0.24f, 0.245f),
                    Pair(0.37f, 0.245f),
                    Pair(0.50f, 0.245f),
                    Pair(0.63f, 0.245f),
                    Pair(0.76f, 0.245f),
                    Pair(0.89f, 0.245f)
                )
            }

            quantidadeDias == 2 -> {
                listOf(
                    Pair(0.11f, 0.245f),
                    Pair(0.24f, 0.245f)
                )
            }

            else -> {
                listOf(
                    Pair(0.11f, 0.245f),
                    Pair(0.24f, 0.245f),
                    Pair(0.37f, 0.245f)
                )
            }
        }
    }

    private fun clicarNaTela(x: Float, y: Float) {
        try {
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
        } catch (_: Exception) {
            // Ignora erro de clique para não derrubar o serviço.
        }
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

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(NotificationManager::class.java)

                val channel = NotificationChannel(
                    notificationChannelId,
                    "Alertas de vagas",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificações quando o Vaga Fácil clicar em uma vaga disponível."
                    enableVibration(true)
                }

                manager.createNotificationChannel(channel)
            } catch (_: Exception) {
                // Ignora erro ao criar canal.
            }
        }
    }

    private fun enviarNotificacaoVagaPegada() {
        try {
            val agora = System.currentTimeMillis()

            if (agora - ultimaNotificacao < 15000) {
                return
            }

            ultimaNotificacao = agora

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            criarCanalNotificacao()

            val abrirAppIntent = packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, MainActivity::class.java)

            abrirAppIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                9902,
                abrirAppIntent,
                flags
            )

            val titulo = "Vaga encontrada!"
            val mensagem = "O Vaga Fácil clicou em um horário disponível. Confira a tela da 99Food."

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, notificationChannelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

            builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(mensagem)
                .setStyle(Notification.BigTextStyle().bigText(mensagem))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                builder.setPriority(Notification.PRIORITY_HIGH)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, builder.build())
        } catch (_: Exception) {
            // Ignora erro de notificação para não travar o robô.
        }
    }

    private fun limparTexto(texto: String): String {
        val lower = texto.lowercase(Locale.ROOT)
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return normalized.replace("\\p{Mn}+".toRegex(), "")
    }
}
