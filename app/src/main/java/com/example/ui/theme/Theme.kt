package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

fun getColorSchemeForSkin(skin: String, isDark: Boolean, highContrast: Boolean = false): ColorScheme {
    if (highContrast) {
        return if (isDark) {
            darkColorScheme(
                primary = Color(0xFF38BDF8),
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF0284C7),
                onPrimaryContainer = Color.White,
                secondary = Color(0xFF34D399),
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFF059669),
                onSecondaryContainer = Color.White,
                background = Color(0xFF000000),
                onBackground = Color(0xFFFFFFFF),
                surface = Color(0xFF0D1117),
                onSurface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFF1E293B),
                onSurfaceVariant = Color(0xFFF8FAFC),
                outline = Color(0xFFE2E8F0),
                outlineVariant = Color(0xFF94A3B8),
                error = Color(0xFFF87171),
                onError = Color.Black,
                errorContainer = Color(0xFF991B1B),
                onErrorContainer = Color.White
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF1D4ED8),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDBEAFE),
                onPrimaryContainer = Color(0xFF000000),
                secondary = Color(0xFF047857),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFD1FAE5),
                onSecondaryContainer = Color(0xFF000000),
                background = Color(0xFFFFFFFF),
                onBackground = Color(0xFF000000),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF000000),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF000000),
                outline = Color(0xFF000000),
                outlineVariant = Color(0xFF475569),
                error = Color(0xFFB91C1C),
                onError = Color.White,
                errorContainer = Color(0xFFFEE2E2),
                onErrorContainer = Color(0xFF7F1D1D)
            )
        }
    }

    return when (skin) {
        "BLUE" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF3B82F6),
                onPrimary = Color.White,
                primaryContainer = Color(0xFF1E3A8A),
                onPrimaryContainer = Color(0xFFDBEAFE),
                secondary = Color(0xFF0284C7),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFF075985),
                onSecondaryContainer = Color(0xFFE0F2FE),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF2563EB),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDBEAFE),
                onPrimaryContainer = Color(0xFF1E3A8A),
                secondary = Color(0xFF0284C7),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE0F2FE),
                onSecondaryContainer = Color(0xFF0C4A6E),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "EMERALD" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF10B981),
                onPrimary = Color(0xFF022C22),
                primaryContainer = Color(0xFF064E3B),
                onPrimaryContainer = Color(0xFFA7F3D0),
                secondary = Color(0xFF2DD4BF),
                onSecondary = Color(0xFF042F2E),
                secondaryContainer = Color(0xFF115E59),
                onSecondaryContainer = Color(0xFF99F6E4),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF059669),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD1FAE5),
                onPrimaryContainer = Color(0xFF064E3B),
                secondary = Color(0xFF0D9488),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCCFBF1),
                onSecondaryContainer = Color(0xFF115E59),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "SLATE" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF94A3B8),
                onPrimary = Color(0xFF0F172A),
                primaryContainer = Color(0xFF334155),
                onPrimaryContainer = Color(0xFFF1F5F9),
                secondary = Color(0xFF64748B),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFF1E293B),
                onSecondaryContainer = Color(0xFFCBD5E1),
                background = Color(0xFF0B0F17),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF182234),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF2D3748),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF475569),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE2E8F0),
                onPrimaryContainer = Color(0xFF1E293B),
                secondary = Color(0xFF334155),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFF1F5F9),
                onSecondaryContainer = Color(0xFF0F172A),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "AMBER" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFF59E0B),
                onPrimary = Color(0xFF451A03),
                primaryContainer = Color(0xFF78350F),
                onPrimaryContainer = Color(0xFFFDE68A),
                secondary = Color(0xFFFB923C),
                onSecondary = Color(0xFF431407),
                secondaryContainer = Color(0xFF9A3412),
                onSecondaryContainer = Color(0xFFFFEDD5),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFD97706),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFEF3C7),
                onPrimaryContainer = Color(0xFF78350F),
                secondary = Color(0xFFB45309),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFFEDD5),
                onSecondaryContainer = Color(0xFF7C2D12),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "PURPLE" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFA78BFA),
                onPrimary = Color(0xFF2E1065),
                primaryContainer = Color(0xFF5B21B6),
                onPrimaryContainer = Color(0xFFEDE9FE),
                secondary = Color(0xFFC084FC),
                onSecondary = Color(0xFF3B0764),
                secondaryContainer = Color(0xFF6B21A8),
                onSecondaryContainer = Color(0xFFF3E8FF),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF7C3AED),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFEDE9FE),
                onPrimaryContainer = Color(0xFF4C1D95),
                secondary = Color(0xFF6D28D9),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFF3E8FF),
                onSecondaryContainer = Color(0xFF581C87),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "ROSE", "PINK" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFF472B6), // Sanftes Cyber-Pink
                onPrimary = Color(0xFF500030),
                primaryContainer = Color(0xFF700045),
                onPrimaryContainer = Color(0xFFFFD8EC),
                secondary = Color(0xFFA78BFA), // Helles Lila 💜
                onSecondary = Color(0xFF2E1065),
                secondaryContainer = Color(0xFF5B21B6),
                onSecondaryContainer = Color(0xFFEDE9FE),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFD946EF), // Kräftiges Pink / Magenta
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFDF4FF),
                onPrimaryContainer = Color(0xFF701A75),
                secondary = Color(0xFF8B5CF6), // Edles Lila 💜
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFF3E8FF),
                onSecondaryContainer = Color(0xFF3B0764),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "MYDOCANIZER", "STANDARD", "DEFAULT" -> { // "MYDOCANIZER" (Originale Logo-Farbwelt: Electric Cyan, Reines Lila 💜 & Cyber-Pink 💖)
            if (isDark) {
                darkColorScheme(
                    primary = Color(0xFF00E5FF), // Logo Electric Cyan (Primäre Aktionen, FAB, Highlights)
                    onPrimary = Color(0xFF00363F),
                    primaryContainer = Color(0xFF083344), // Subtiles Cyan-Blau (ruhiger Container)
                    onPrimaryContainer = Color(0xFFCCFBF1),
                    secondary = Color(0xFFA855F7), // Strahlendes Lila 💜 (Logo "Anizer" & Sekundär-Aktionen)
                    onSecondary = Color(0xFF1E053A),
                    secondaryContainer = Color(0xFF3B1A66), // Harmonisches Lila für Tonal-Buttons & Tags
                    onSecondaryContainer = Color(0xFFF3E8FF),
                    tertiary = Color(0xFFEC4899), // Akzent Cyber-Pink 💖 (Fristen, Wichtiges)
                    onTertiary = Color(0xFF500030),
                    tertiaryContainer = Color(0xFF700045),
                    onTertiaryContainer = Color(0xFFFFD8EC),
                    background = Color(0xFF070B14), // Deep Cyber Navy Canvas
                    onBackground = Color(0xFFF1F5F9),
                    surface = Color(0xFF0F172A), // Slate 900 für Karten & Leisten
                    onSurface = Color(0xFFF1F5F9),
                    surfaceVariant = Color(0xFF1E293B), // Slate 800 für Eingabefelder & Kacheln
                    onSurfaceVariant = Color(0xFF94A3B8), // Klar lesbare, abgestufte Metadaten & Labels
                    outline = Color(0xFF334155), // Ruhiger Slate-Rahmen (kein störendes Neon-Cyan!)
                    outlineVariant = Color(0xFF1E293B)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF0284C7), // Logo Deep Cyan-Blue (perfekter Kontrast auf Weiß)
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE0F2FE),
                    onPrimaryContainer = Color(0xFF0369A1),
                    secondary = Color(0xFF8B5CF6), // Edles Lila 💜
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFFEDE9FE), // Sanftes Flieder
                    onSecondaryContainer = Color(0xFF5B21B6),
                    tertiary = Color(0xFFEC4899), // Kräftiges Pink 💖
                    onTertiary = Color.White,
                    tertiaryContainer = Color(0xFFFCE7F3),
                    onTertiaryContainer = Color(0xFF831843),
                    background = Color(0xFFF8FAFC),
                    onBackground = Color(0xFF0F172A),
                    surface = Color.White,
                    onSurface = Color(0xFF0F172A),
                    surfaceVariant = Color(0xFFF1F5F9),
                    onSurfaceVariant = Color(0xFF64748B),
                    outline = Color(0xFFCBD5E1), // Subtile Begrenzungslinien
                    outlineVariant = Color(0xFFE2E8F0)
                )
            }
        }
        else -> { // Fallback auf Originale myDocAnizer Logo-Farbwelt
            getColorSchemeForSkin("MYDOCANIZER", isDark, highContrast)
        }
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSkin: String = "MYDOCANIZER",
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorSchemeForSkin(colorSkin, darkTheme, highContrast)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

