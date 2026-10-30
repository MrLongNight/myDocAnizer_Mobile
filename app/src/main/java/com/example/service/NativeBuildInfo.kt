package com.example.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.PowerManager
import android.os.Process

/**
 * Diagnose-Hilfe: macht sichtbar, WIE die native llama.cpp-Bibliothek gebaut wurde
 * (Optimierung, CPU-Features) und unter welchen Bedingungen sie läuft
 * (Energiesparmodus, Thermik, Prozess-Bitbreite). Nur für den LLM-Diagnosebericht.
 */
object NativeBuildInfo {
    private external fun nativeGetBuildInfo(): String

    fun describe(context: Context): String {
        val parts = mutableListOf<String>()
        parts += "Prozess64bit=" + Process.is64Bit()
        parts += "debuggable=" + ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        parts += "Gerät=" + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.HARDWARE + ")"
        if (Build.VERSION.SDK_INT >= 31) parts += "SoC=" + Build.SOC_MODEL
        parts += "Android-API=" + Build.VERSION.SDK_INT
        parts += "CPU-Kerne=" + Runtime.getRuntime().availableProcessors()
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power != null) {
            parts += "Energiesparmodus=" + power.isPowerSaveMode
            if (Build.VERSION.SDK_INT >= 29) parts += "ThermalStatus=" + power.currentThermalStatus
        }
        parts += "nativeLibraryDir=" + context.applicationInfo.nativeLibraryDir
        parts += runCatching {
            System.loadLibrary("llama_android")
            nativeGetBuildInfo()
        }.getOrElse { "native Build-Info nicht verfügbar (" + it.javaClass.simpleName + ")" }
        return parts.joinToString("; ")
    }
}
