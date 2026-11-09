package app.klean.feature.settings.backup

import android.content.res.Resources
import app.klean.feature.settings.R

/** The restore dialog's text (PLAN.md §M9 Task 9.4), with the counts pluralised. */
internal fun restoreConfirmText(
    res: Resources,
    currentEntries: Int,
    date: String,
    backupEntries: Int,
    backupPhotos: Int,
): String = res.getString(
    R.string.backup_confirm_text,
    res.getQuantityString(R.plurals.backup_confirm_entries, currentEntries, currentEntries),
    date,
    res.getQuantityString(R.plurals.backup_confirm_entries, backupEntries, backupEntries),
    res.getQuantityString(R.plurals.backup_confirm_photos, backupPhotos, backupPhotos),
)
