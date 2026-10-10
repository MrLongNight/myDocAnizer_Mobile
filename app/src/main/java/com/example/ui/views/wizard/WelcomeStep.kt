package com.example.ui.views.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.DocAnizerViewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

@Composable
fun StepWelcome(
    viewModel: DocAnizerViewModel,
    isManualReconfig: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = if (isManualReconfig) "App-Konfiguration von myDocAnizer" else "Willkommen bei myDocAnizer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Deine sichere App für Dokumente, Rechnungen und Verträge – mit automatischer Texterkennung und flexibler Datensicherung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontSize = 11.5.sp
                )
            }
        }

        val titleColor = MaterialTheme.colorScheme.onSurface
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant

        val text1 = remember(titleColor, bodyColor) {
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = titleColor, fontSize = 12.5.sp)) {
                    append("Sicher & geschützt:\n")
                }
                withStyle(SpanStyle(color = bodyColor, fontSize = 11.5.sp)) {
                    append("Deine Dokumente gehören dir. Sie werden sicher verschlüsselt direkt auf deinem Smartphone gespeichert – ohne Schnüffelei oder ungefragte Datenweitergabe.")
                }
            }
        }

        FlowingArticleBlock(
            iconRes = R.drawable.icon_datensicherheit,
            iconDescription = "Sicher & geschützt",
            iconWidth = 100.dp,
            iconHeight = 100.dp,
            iconAlignment = Alignment.Start,
            text = text1
        )

        val text2 = remember(titleColor, bodyColor) {
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = titleColor, fontSize = 12.5.sp)) {
                    append("Automatische Belegerkennung:\n")
                }
                withStyle(SpanStyle(color = bodyColor, fontSize = 11.5.sp)) {
                    append("Die smarte Erkennung liest Rechnungen und Briefe direkt auf dem Handy. Wichtige Daten wie Beträge, Absender und Fristen werden sofort gefunden, ohne dass etwas ins Internet geschickt wird.")
                }
            }
        }

        FlowingArticleBlock(
            iconRes = R.drawable.icon_ondevice_ai_agent_hq,
            iconDescription = "Automatische Belegerkennung",
            iconWidth = 96.dp,
            iconHeight = 96.dp,
            iconAlignment = Alignment.End,
            text = text2
        )

        val text3 = remember(titleColor, bodyColor) {
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = titleColor, fontSize = 12.5.sp)) {
                    append("Übersichtliche Ordner:\n")
                }
                withStyle(SpanStyle(color = bodyColor, fontSize = 11.5.sp)) {
                    append("Sortiere alles so, wie du es brauchst: mit praktischen Ordnern, eigenen Notizfeldern und einer schnellen Suche nach jedem Stichwort.")
                }
            }
        }

        FlowingArticleBlock(
            iconRes = R.drawable.icon_ordnerstruktur,
            iconDescription = "Übersichtliche Ordner",
            iconWidth = 110.dp,
            iconHeight = 110.dp,
            iconAlignment = Alignment.Start,
            text = text3
        )

        val text4 = remember(titleColor, bodyColor) {
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = titleColor, fontSize = 12.5.sp)) {
                    append("Automatische Sicherung:\n")
                }
                withStyle(SpanStyle(color = bodyColor, fontSize = 11.5.sp)) {
                    append("Schütze dich vor Datenverlust: Gleiche deine Dokumente kabellos mit deinem Computer oder Tablet ab und erstelle verschlüsselte Sicherheitskopien (z. B. auf Google Drive oder auf USB-Sticks).")
                }
            }
        }

        FlowingArticleBlock(
            iconRes = R.drawable.icon_app_connect,
            iconDescription = "Automatische Sicherung",
            iconWidth = 144.dp,
            iconHeight = 98.dp,
            iconAlignment = Alignment.End,
            text = text4
        )
    }
}

@Composable
fun FlowingArticleBlock(
    iconRes: Int,
    iconDescription: String,
    iconWidth: Dp,
    iconHeight: Dp,
    iconAlignment: Alignment.Horizontal,
    text: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            val totalWidthPx = constraints.maxWidth
            val imageWidthPx = with(density) { iconWidth.roundToPx() }
            val imageHeightPx = with(density) { iconHeight.roundToPx() }
            val spacingPx = with(density) { 12.dp.roundToPx() }
            val textBesideWidthPx = (totalWidthPx - imageWidthPx - spacingPx).coerceAtLeast(100)

            val (textTop, textBottom) = remember(totalWidthPx, text, iconHeight) {
                val layoutResult = textMeasurer.measure(
                    text = text,
                    style = textStyle,
                    constraints = Constraints(maxWidth = textBesideWidthPx)
                )

                if (layoutResult.lineCount <= 1) {
                    text to null
                } else {
                    val firstLineHeight = if (layoutResult.lineCount > 0) {
                        layoutResult.getLineBottom(0) - layoutResult.getLineTop(0)
                    } else 30f
                    val maxBottom = imageHeightPx.toFloat() + (firstLineHeight * 0.35f)
                    var lastLineBeside = -1
                    for (i in 0 until layoutResult.lineCount) {
                        val bottom = layoutResult.getLineBottom(i)
                        if (bottom <= maxBottom) {
                            lastLineBeside = i
                        } else {
                            break
                        }
                    }

                    if (lastLineBeside >= layoutResult.lineCount - 1 || lastLineBeside < 0) {
                        if (lastLineBeside < 0) {
                            val splitOffset = layoutResult.getLineEnd(minOf(1, layoutResult.lineCount - 1), visibleEnd = true)
                            var startIdx = splitOffset
                            while (startIdx < text.length && (text.text[startIdx] == ' ' || text.text[startIdx] == '\n')) {
                                startIdx++
                            }
                            text.subSequence(0, splitOffset) to (if (startIdx < text.length) text.subSequence(startIdx, text.length) else null)
                        } else {
                            text to null
                        }
                    } else {
                        val splitOffset = layoutResult.getLineEnd(lastLineBeside, visibleEnd = true)
                        var startIdx = splitOffset
                        while (startIdx < text.length && (text.text[startIdx] == ' ' || text.text[startIdx] == '\n')) {
                            startIdx++
                        }
                        val top = text.subSequence(0, splitOffset)
                        val bottom = if (startIdx < text.length) text.subSequence(startIdx, text.length) else null
                        top to bottom
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (iconAlignment == Alignment.Start) {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = iconDescription,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(iconWidth, iconHeight)
                        )
                        Text(
                            text = textTop ?: text,
                            style = textStyle,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = textTop ?: text,
                            style = textStyle,
                            modifier = Modifier.weight(1f)
                        )
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = iconDescription,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(iconWidth, iconHeight)
                        )
                    }
                }

                if (textBottom != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = textBottom,
                        style = textStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
