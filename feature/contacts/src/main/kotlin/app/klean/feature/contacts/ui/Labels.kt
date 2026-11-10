package app.klean.feature.contacts.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.klean.feature.contacts.R
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.LocalDate

/** "On the day", "1 day before", "3 days before". */
@Composable
fun reminderLabel(days: Int): String =
    if (days == 0) stringResource(R.string.birthdays_reminder_on_day) else pluralStringResource(R.plurals.birthdays_reminder_days_before, days, days)

/** "Today", "Tomorrow", or "Mar 3". */
@Composable
fun whenLabel(date: LocalDate, daysUntil: Int): String = when (daysUntil) {
    0 -> stringResource(R.string.birthdays_today)
    1 -> stringResource(R.string.birthdays_tomorrow)
    else -> shortDate(date)
}

fun shortDate(d: LocalDate): String =
    java.time.LocalDate.of(d.year, d.month.ordinal + 1, d.day).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

fun monthName(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())

/** "March 3" or "March 3, 1985". */
fun birthdayText(month: Int, day: Int, year: Int?): String = monthName(month) + " " + day + (year?.let { ", $it" } ?: "")

/** The labels the editor offers for numbers and emails (stored in English, shown translated). */
val PHONE_LABELS = listOf("mobile", "home", "work", "")
val EMAIL_LABELS = listOf("home", "work", "")

@Composable
fun labelText(label: String): String = when (label.lowercase()) {
    "mobile" -> stringResource(R.string.contacts_label_mobile)
    "home" -> stringResource(R.string.contacts_label_home)
    "work" -> stringResource(R.string.contacts_label_work)
    "" -> stringResource(R.string.contacts_label_other)
    else -> label
}
