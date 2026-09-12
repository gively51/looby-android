package com.viple.looby.ui.util

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val priceFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.FRANCE).apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }

fun Double.formatPrice(): String = priceFormat.format(this)

fun String?.initials(): String {
    if (this.isNullOrBlank()) return "?"
    return trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
}

fun String?.toInstantOrNull(): Instant? = this?.let { s ->
    runCatching { Instant.parse(if (s.endsWith("Z") || s.contains('+')) s else "${s}Z") }.getOrNull()
}

/** "à l'instant", "il y a 5 min", "hier", "12 mars"… */
fun String?.relativeTime(): String {
    val instant = toInstantOrNull() ?: return ""
    val now = Instant.now()
    val d = Duration.between(instant, now)
    return when {
        d.toMinutes() < 1 -> "à l'instant"
        d.toMinutes() < 60 -> "il y a ${d.toMinutes()} min"
        d.toHours() < 24 -> "il y a ${d.toHours()} h"
        d.toDays() < 2 -> "hier"
        d.toDays() < 7 -> "il y a ${d.toDays()} j"
        else -> instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE))
    }
}

fun String?.formatTime(): String = toInstantOrNull()?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""

fun String?.formatDate(): String =
    toInstantOrNull()?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)) ?: ""

fun String?.formatDateTime(): String =
    toInstantOrNull()?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.FRANCE)) ?: ""

/** Libellé de jour pour les séparateurs dans une conversation. */
fun String?.dayLabel(): String {
    val instant = toInstantOrNull() ?: return ""
    val zdt = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    return when (zdt) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> zdt.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)).replaceFirstChar { it.uppercase() }
    }
}
