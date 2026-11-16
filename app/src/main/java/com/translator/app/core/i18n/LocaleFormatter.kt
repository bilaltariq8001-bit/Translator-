package com.translator.app.core.i18n

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/**
 * Utility functions for locale-safe formatting, casing, and bidirectional text handling
 * adhering to the app's native Android/Compose localization architecture (AppLocale).
 */
object LocaleFormatter {

    /** Formats a number according to the active app locale. */
    fun formatNumber(number: Number, locale: Locale = AppLocale.locale): String {
        return NumberFormat.getNumberInstance(locale).format(number)
    }

    /** Formats a currency amount according to the active app locale and currency code. */
    fun formatCurrency(amount: Number, currencyCode: String = "USD", locale: Locale = AppLocale.locale): String {
        return NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(currencyCode)
        }.format(amount)
    }

    /** Formats a date according to the active app locale. */
    fun formatDate(date: LocalDate, style: FormatStyle = FormatStyle.MEDIUM, locale: Locale = AppLocale.locale): String {
        return date.format(DateTimeFormatter.ofLocalizedDate(style).withLocale(locale))
    }

    /** Formats date and time according to the active app locale. */
    fun formatDateTime(dateTime: LocalDateTime, style: FormatStyle = FormatStyle.MEDIUM, locale: Locale = AppLocale.locale): String {
        return dateTime.format(DateTimeFormatter.ofLocalizedDateTime(style).withLocale(locale))
    }

    /** Locale-safe uppercase conversion (handles Turkish/Azeri dotted/dotless I correctly). */
    fun safeToUpperCase(text: String, locale: Locale = AppLocale.locale): String {
        return text.uppercase(locale)
    }

    /** Locale-safe lowercase conversion. */
    fun safeToLowerCase(text: String, locale: Locale = AppLocale.locale): String {
        return text.lowercase(locale)
    }

    /** Wraps mixed-direction content with Unicode bidi formatting marks (LTR/RTL isolation). */
    fun isolateBidi(text: String, isRtl: Boolean): String {
        val lrm = "\u200E"
        val rlm = "\u200F"
        return if (isRtl) "$rlm$text$rlm" else "$lrm$text$lrm"
    }
}
