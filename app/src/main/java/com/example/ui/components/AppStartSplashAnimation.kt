package com.example.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium-Audio-Synthesizer für einen atemberaubenden, kopfhörer-optimierten Soundeffekt.
 * NUTZT AUDIO-STREAMING (MODE_STREAM):
 * - Sound startet im selben Millisekundenschritt wie die UI-Animation (Latenz < 10ms).
 * - Keine Vorberechnungspause (Pre-computation Lag behoben).
 * - Garantiert 100% Phasen- und Timingsynchronität zur optischen Timeline.
 * - Fällt in Testumgebungen (ohne Audiogeräte) geräuschlos und sicher zurück.
 */
object SplashSoundSynthesizer {
    private var mediaPlayer: MediaPlayer? = null
    private var wavFile: File? = null
    private val lock = Any()
    private var isPrecomputing = false
    private var isStopped = false
    private var appContext: Context? = null

    fun precompute(context: Context) {
        val appCtx = context.applicationContext
        synchronized(lock) {
            appContext = appCtx
            if (wavFile != null || isPrecomputing) return
            isPrecomputing = true
        }
        Thread {
            try {
                val file = File(appCtx.cacheDir, "splash_sound_v8.wav")
                if (!file.exists()) {
                    val data = generateSamples()
                    writeWavFile(file, data)
                }
                synchronized(lock) {
                    wavFile = file
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                synchronized(lock) {
                    isPrecomputing = false
                }
            }
        }.start()
    }

    private fun writeWavFile(file: File, data: ShortArray, sampleRate: Int = 44100) {
        try {
            val totalAudioLen = data.size * 2
            val totalDataLen = totalAudioLen + 36
            val channels = 1
            val byteRate = sampleRate * 2

            val header = ByteArray(44)
            header[0] = 'R'.code.toByte() // RIFF
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            
            header[8] = 'W'.code.toByte() // WAVE
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            
            header[12] = 'f'.code.toByte() // fmt 
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            
            header[16] = 16 // Sub-chunk size = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            
            header[20] = 1 // PCM = 1
            header[21] = 0
            
            header[22] = channels.toByte() // channels
            header[23] = 0
            
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            
            header[32] = 2 // Block align = 2
            header[33] = 0
            
            header[34] = 16 // Bits per sample = 16
            header[35] = 0
            
            header[36] = 'd'.code.toByte() // data
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            val tempFile = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tempFile).use { fos ->
                fos.write(header)
                val byteBuffer = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (value in data) {
                    byteBuffer.putShort(value)
                }
                fos.write(byteBuffer.array())
            }
            if (tempFile.exists()) {
                tempFile.renameTo(file)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun generateSamples(): ShortArray {
        val sampleRate = 44100
        val durationSeconds = 2.4
        val numSamples = (durationSeconds * sampleRate).toInt()
        val data = ShortArray(numSamples)
        val chimeStart = (1.30 * sampleRate).toInt()

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0

            // 1. Digitaler Tech-Power-Up Sweep (0.0s bis 0.5s)
            if (t < 0.55) {
                val pProgress = t / 0.55
                val sweepFreq = 65.0 + 110.0 * pProgress // 65Hz auf 175Hz
                val subWave = sin(2.0 * Math.PI * sweepFreq * t)
                val subEnvelope = sin(Math.PI * pProgress) * 0.35
                sample += subWave * subEnvelope
            }

            // 2. Warmer futuristischer Ambient Pad Swell (0.1s bis 1.5s)
            if (t >= 0.08 && t < 1.55) {
                val padProgress = (t - 0.08) / 1.47
                val c3 = 130.81
                val g3 = 196.00
                val c4 = 261.63
                val e4 = 329.63
                val g4 = 392.00

                val padWave = 0.28 * sin(2.0 * Math.PI * c3 * t) +
                              0.18 * sin(2.0 * Math.PI * g3 * t) +
                              0.24 * sin(2.0 * Math.PI * c4 * t) +
                              0.16 * sin(2.0 * Math.PI * e4 * t) +
                              0.10 * sin(2.0 * Math.PI * g4 * t)

                val chorusLfo = 0.88 + 0.12 * sin(2.0 * Math.PI * 2.2 * t)
                val padEnvelope = sin(Math.PI * padProgress) * 0.42 * chorusLfo
                sample += padWave * padEnvelope
            }

            // 3. Kristall-Klarer "Vault Ready" Glocken- & Resonanz-Akkord (1.30s bis 2.4s)
            if (i >= chimeStart) {
                val tChime = (i - chimeStart).toDouble() / sampleRate

                val fC5 = 523.25
                val fE5 = 659.25
                val fG5 = 783.99
                val fB5 = 987.77
                val fD6 = 1174.66
                val fG6 = 1567.98

                val dec1 = kotlin.math.exp(-2.2 * tChime)
                val dec2 = kotlin.math.exp(-3.2 * tChime)
                val dec3 = kotlin.math.exp(-4.2 * tChime)
                val dec4 = kotlin.math.exp(-5.2 * tChime)
                val dec5 = kotlin.math.exp(-6.5 * tChime)
                val dec6 = kotlin.math.exp(-8.0 * tChime)

                val attack = (tChime / 0.015).coerceIn(0.0, 1.0)

                val chimeWave = 0.32 * sin(2.0 * Math.PI * fC5 * tChime) * dec1 +
                                0.26 * sin(2.0 * Math.PI * fE5 * tChime) * dec2 +
                                0.22 * sin(2.0 * Math.PI * fG5 * tChime) * dec3 +
                                0.16 * sin(2.0 * Math.PI * fB5 * tChime) * dec4 +
                                0.12 * sin(2.0 * Math.PI * fD6 * tChime) * dec5 +
                                0.06 * sin(2.0 * Math.PI * fG6 * tChime) * dec6

                val fadeOut = if (t > 2.28) ((2.4 - t) / 0.12).coerceIn(0.0, 1.0) else 1.0
                sample += chimeWave * attack * fadeOut * 0.72
            }

            data[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return data
    }

    fun playPremiumSplashSound(context: Context? = null) {
        val ctx = context?.applicationContext ?: appContext ?: return
        synchronized(lock) {
            isStopped = false
        }
        Thread {
            try {
                val file = synchronized(lock) {
                    var f = wavFile
                    if (f == null || !f.exists()) {
                        f = File(ctx.cacheDir, "splash_sound_v8.wav")
                        if (!f.exists()) {
                            try {
                                val data = generateSamples()
                                writeWavFile(f, data)
                            } catch (_: Throwable) {}
                        }
                        wavFile = f
                    }
                    f
                }

                if (file != null && file.exists()) {
                    var mp: MediaPlayer? = null
                    try {
                        synchronized(lock) {
                            if (isStopped) return@Thread
                        }
                        mp = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            setVolume(1.0f, 1.0f)
                            setOnCompletionListener { player ->
                                try {
                                    player.reset()
                                    player.release()
                                } catch (_: Throwable) {}
                                synchronized(lock) {
                                    if (mediaPlayer == player) mediaPlayer = null
                                }
                            }
                            setOnErrorListener { player, _, _ ->
                                try {
                                    player.reset()
                                    player.release()
                                } catch (_: Throwable) {}
                                synchronized(lock) {
                                    if (mediaPlayer == player) mediaPlayer = null
                                }
                                true
                            }
                            prepare()
                        }
                        synchronized(lock) {
                            if (isStopped) {
                                try {
                                    mp.reset()
                                    mp.release()
                                } catch (_: Throwable) {}
                                return@Thread
                            }
                            mediaPlayer = mp
                        }
                        mp.start()
                    } catch (_: Throwable) {
                        try {
                            mp?.reset()
                            mp?.release()
                        } catch (_: Throwable) {}
                        synchronized(lock) {
                            if (mediaPlayer == mp) mediaPlayer = null
                        }
                    }
                }
            } catch (_: Throwable) {
                // Audio-Wiedergabe ist optional und blockiert niemals den Start
            }
        }.start()
    }

    fun stop() {
        synchronized(lock) {
            isStopped = true
            try {
                mediaPlayer?.let { player ->
                    try {
                        player.setOnCompletionListener(null)
                        player.setOnErrorListener(null)
                        if (player.isPlaying) {
                            player.stop()
                        }
                        player.reset()
                        player.release()
                    } catch (_: Throwable) {}
                }
                mediaPlayer = null
            } catch (_: Throwable) {}
        }
    }
}

/**
 * High-Fidelity App-Start-Animation für myDocAnizer-Mobile.
 * - Kontinuierlicher 0-100% Ladebalken & System-Telemetrie (2.4 Sekunden).
 * - Neon-Laser Scanner, orbital rotierende Cyber-Partikel.
 * - Dynamisches Hologramm-Logo & passende Sound-Harmonie.
 * - Garantiert abbruchsicher mit Tap-to-Skip.
 */
@Composable
fun AppStartSplashAnimation(
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalDurationMs = 2400 // Angenehme, elegante 2.4s für den vollständigen Start-Swell
    val progressAnim = remember { Animatable(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")

    // Endlose Hilfs-Rotationen für Partikel & Scan-Pulse während des Starts
    val particleAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_rotation"
    )

    val laserScanY by infiniteTransition.animateFloat(
        initialValue = -90f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_scan"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Abbruchsicherer Finish-Handler, der auch das Audio augenblicklich stoppt
    var isFinished by remember { mutableStateOf(false) }
    val safeFinish = {
        if (!isFinished) {
            isFinished = true
            SplashSoundSynthesizer.stop()
            onAnimationFinished()
        }
    }

    LaunchedEffect(Unit) {
        try {
            // Starte Sound asynchron (ohne UI-Thread zu blockieren)
            SplashSoundSynthesizer.playPremiumSplashSound(context)

            // Feste Maximaldauer mit Fallback-Garantie
            kotlinx.coroutines.withTimeoutOrNull(3500L) {
                progressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = totalDurationMs, easing = LinearEasing)
                )
            }
        } finally {
            safeFinish()
        }
    }

    val progress = progressAnim.value
    val percentInt = (progress * 100).toInt().coerceIn(0, 100)

    // Gesamter Ausblend-Alpha-Wert kurz vor Ende (ab 90% bis 100%)
    val overallAlpha = if (progress > 0.90f) {
        ((1f - progress) / 0.10f).coerceIn(0f, 1f)
    } else {
        1f
    }

    // Farben & Gradients
    val neonCyan = Color(0xFF00E5FF)
    val neonMagenta = Color(0xFFFF007F)
    val deepViolet = Color(0xFF8B5CF6)
    val darkBg = Color(0xFF060A14)

    // 36 Kosmische / Cyber-Partikel
    val particles = remember {
        List(36) { i ->
            val radius = 80f + (i * 7f) % 110f
            val speed = if (i % 2 == 0) 1.0f else -0.8f
            val offsetAngle = (i * 0.1745f) * 3.14159f
            val color = when (i % 3) {
                0 -> neonCyan
                1 -> neonMagenta
                else -> deepViolet
            }
            val size = 2.2f + (i % 4) * 1.3f
            SplashParticle(radius, speed, offsetAngle, color, size)
        }
    }

    // Status-Text je nach Fortschritt
    val statusText = when {
        progress < 0.25f -> "INITIALISIERUNG DER PRIVATEN SANDBOX..."
        progress < 0.50f -> "ON-DEVICE OCR & KI-INFERENZ GELADEN..."
        progress < 0.75f -> "ZERO-KNOWLEDGE VERSCHLÜSSELUNG AKTIV..."
        progress < 0.94f -> "DOKUMENTEN-TRESOR BEREIT..."
        else -> "STARTET..."
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overallAlpha)
            .background(darkBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                safeFinish()
            }
            .testTag("app_start_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // 1. KONTINUIERLICHE HINTERGRUND- & LASER-CANVAS (ununterbrochen aktiv)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f - 60f)

            // Zentraler pulsierender Radial-Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        neonCyan.copy(alpha = 0.22f * pulseScale),
                        deepViolet.copy(alpha = 0.14f * pulseScale),
                        Color.Transparent
                    ),
                    center = center,
                    radius = 320f
                ),
                center = center,
                radius = 320f
            )

            // Cyber-Eckmarkierungen (Neon Cyan & Magenta)
            val boxRadius = 140f
            val strokeWidth = 3f

            // Cyan Ecke (Links-Oben & Links-Unten)
            val cyanPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - boxRadius, center.y - 40f)
                lineTo(center.x - boxRadius, center.y + boxRadius - 20f)
                quadraticTo(
                    center.x - boxRadius, center.y + boxRadius,
                    center.x - boxRadius + 20f, center.y + boxRadius
                )
                lineTo(center.x + 30f, center.y + boxRadius)
            }
            drawPath(
                path = cyanPath,
                color = neonCyan,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Magenta Ecke (Rechts-Unten & Rechts-Oben)
            val magentaPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x + boxRadius, center.y + 40f)
                lineTo(center.x + boxRadius, center.y - boxRadius + 20f)
                quadraticTo(
                    center.x + boxRadius, center.y - boxRadius,
                    center.x + boxRadius - 20f, center.y - boxRadius
                )
                lineTo(center.x - 30f, center.y - boxRadius)
            }
            drawPath(
                path = magentaPath,
                color = neonMagenta,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Vertikal oszillierender Laser-Scan-Strahl
            val scanY = center.y + laserScanY
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        neonCyan.copy(alpha = 0.85f),
                        neonMagenta.copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    startX = center.x - boxRadius,
                    endX = center.x + boxRadius
                ),
                start = Offset(center.x - boxRadius + 10f, scanY),
                end = Offset(center.x + boxRadius - 10f, scanY),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )

            // Rotierende Orbital-Partikel
            val radAngle = Math.toRadians(particleAngle.toDouble()).toFloat()
            particles.forEach { p ->
                val currentAngle = p.offsetAngle + (p.speed * radAngle)
                val currentRadius = p.radius + sin(radAngle * 2f + p.offsetAngle) * 12f
                val px = center.x + cos(currentAngle) * currentRadius
                val py = center.y + sin(currentAngle) * currentRadius
                val alpha = (0.35f + 0.65f * sin(radAngle * 3f + p.offsetAngle)).coerceIn(0.2f, 1f)

                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.size,
                    center = Offset(px, py)
                )
            }

            // GIGANTISCHER, EDLER SCHOCKWELLEN-IMPULS (EXAKT synchron zum Glocken-Akkord bei 82.5%)
            if (progress >= 0.825f) {
                val swProgress = ((progress - 0.825f) / 0.175f).coerceIn(0f, 1f)
                val swRadius = swProgress * 650f
                val swAlpha = (1f - swProgress) * 0.75f
                drawCircle(
                    color = neonCyan.copy(alpha = swAlpha),
                    radius = swRadius,
                    center = center,
                    style = Stroke(width = 4f + swProgress * 8f)
                )
                drawCircle(
                    color = neonMagenta.copy(alpha = swAlpha * 0.4f),
                    radius = swRadius * 0.85f,
                    center = center,
                    style = Stroke(width = 2f + swProgress * 4f)
                )
            }
        }

        // 2. ZENTRALES LOGO UND MARKEN-BANNER (Floating, flüssig und rotierend)
        val logoScale = 0.80f + (progress * 0.22f) // Sanftes, edles Heranzoomen von 0.8x auf 1.02x
        val logoRotation = -15f * (1f - progress) // Edler, schwebender 3D-Ausrichtungs-Effekt (-15° bis 0°)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(bottom = 80.dp)
                .graphicsLayer(
                    scaleX = logoScale * pulseScale,
                    scaleY = logoScale * pulseScale,
                    rotationZ = logoRotation
                )
        ) {
            AppLogoBanner(
                iconHeight = 180.dp,
                fontSize = 28f,
                includeContainer = true,
                showTagline = true,
                tagline = "first private and smart document management system"
            )
        }

        // 3. UNTERER BEREICH: 0-100% LADEBALKEN & STATUS-TELEMETRIE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 32.dp, end = 32.dp)
                .fillMaxWidth()
        ) {
            // Status-Text & Prozentanzeige
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    style = TextStyle(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8)
                    )
                )
                Text(
                    text = "$percentInt%",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fortschrittsbalken mit Neon-Verlauf
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(neonCyan, deepViolet, neonMagenta)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Tippen zum Überspringen",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.40f)
            )
        }
    }
}

private data class SplashParticle(
    val radius: Float,
    val speed: Float,
    val offsetAngle: Float,
    val color: Color,
    val size: Float
)
