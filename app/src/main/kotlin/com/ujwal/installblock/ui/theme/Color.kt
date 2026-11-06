// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.theme

import androidx.compose.ui.graphics.Color

object InstallBlockColors {
    // ── Light Theme (Crisp, modern, Apple-inspired) ───────────────────────────
    val LightBackground       = Color(0xFFF8FAFC) // Clean light slate-white
    val LightSurface          = Color(0xFFFFFFFF) // Pure white card surface
    val LightSurfaceSubtle    = Color(0xFFF1F5F9) // Soft tinted container
    val LightGlass            = Color(0xF2FFFFFF) // Frosted white glass
    val LightOnBackground     = Color(0xFF0F172A) // Deep slate black
    val LightOnSurface        = Color(0xFF0F172A) // Deep slate black
    val LightMuted            = Color(0xFF64748B) // Slate secondary
    val LightPrimary          = Color(0xFF0F172A) // Sleek dark primary accent
    val LightOnPrimary        = Color(0xFFFFFFFF) // White text on primary
    val LightBorder           = Color(0x0F0F172A) // Ultra-subtle hairline border (6%)
    val LightBorderStrong     = Color(0x1F0F172A) // Subtle border (12%)

    // Status colors (Light mode - pastel containers with rich text)
    val LightBlockedBg        = Color(0xFFECFDF5) // Emerald-50
    val LightBlockedText      = Color(0xFF059669) // Emerald-600
    val LightInstalledBg      = Color(0xFFFFFBEB) // Amber-50
    val LightInstalledText    = Color(0xFFD97706) // Amber-600
    val LightNotBlockedBg    = Color(0xFFF1F5F9) // Slate-100
    val LightNotBlockedText  = Color(0xFF64748B) // Slate-500
    val LightBlockingBg       = Color(0xFFEFF6FF) // Blue-50
    val LightBlockingText     = Color(0xFF2563EB) // Blue-600

    // ── Dark Theme (Deep obsidian, sleek, Apple-inspired) ─────────────────────
    val DarkBackground        = Color(0xFF09090B) // Deep true dark obsidian
    val DarkSurface           = Color(0xFF18181B) // Zinc-900 surface
    val DarkSurfaceSubtle     = Color(0xFF27272A) // Zinc-800 container
    val DarkGlass             = Color(0xF018181B) // Frosted dark glass
    val DarkOnBackground      = Color(0xFFF8FAFC) // Crisp white
    val DarkOnSurface         = Color(0xFFF8FAFC) // Crisp white
    val DarkMuted             = Color(0xFF94A3B8) // Slate muted
    val DarkPrimary           = Color(0xFFFFFFFF) // Crisp white primary
    val DarkOnPrimary         = Color(0xFF09090B) // Dark text on primary
    val DarkBorder            = Color(0x1AFFFFFF) // Subtle white hairline border (10%)
    val DarkBorderStrong      = Color(0x33FFFFFF) // 20% white border

    // Status colors (Dark mode)
    val DarkBlockedBg         = Color(0x2610B981) // 15% emerald
    val DarkBlockedText       = Color(0xFF34D399) // Emerald-400
    val DarkInstalledBg       = Color(0x26F59E0B) // 15% amber
    val DarkInstalledText     = Color(0xFFFBBF24) // Amber-400
    val DarkNotBlockedBg     = Color(0x2694A3B8) // 15% slate
    val DarkNotBlockedText   = Color(0xFFCBD5E1) // Slate-300
    val DarkBlockingBg        = Color(0x263B82F6) // 15% blue
    val DarkBlockingText      = Color(0xFF60A5FA) // Blue-400

    // Semantic tokens
    val Success               = Color(0xFF10B981)
    val Warning               = Color(0xFFF59E0B)
    val Error                 = Color(0xFFEF4444)
    val BlueAccent            = Color(0xFF0A84FF)

    // Backward compatibility aliases
    val Background            = LightBackground
    val Surface               = LightSurface
    val SurfaceVariant        = LightSurfaceSubtle
    val Glass                 = LightGlass
    val OnBackground          = LightOnBackground
    val OnSurface             = LightOnSurface
    val Muted                 = LightMuted
    val Accent                = LightPrimary
    val AccentSoft            = LightSurfaceSubtle
    val Border                = LightBorder
}
