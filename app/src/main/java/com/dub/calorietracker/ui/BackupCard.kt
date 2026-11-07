package com.dub.calorietracker.ui

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** Settings → Backup: pick a file outside the app that every change is saved to. */
@Composable
fun BackupCard(vm: AppViewModel) {
    val uri by vm.backupUri.collectAsStateWithLifecycle()
    val at by vm.lastBackupAt.collectAsStateWithLifecycle()
    val problem by vm.backupProblem.collectAsStateWithLifecycle()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::setBackupFile) }
    val open = rememberRestoreLauncher(vm)

    SectionCard {
        Text("Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Updating the app keeps your data, but deleting it erases everything stored inside it. So after every " +
                "change the app also saves your plan, every food you've logged, your check-ins and saved foods to a backup file " +
                "outside the app (Documents/CalorieTracker on your phone, unless you pick another file, e.g. on " +
                "Google Drive). After reinstalling, tap Restore and pick that file. API keys aren't included.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                uri == null -> "No backup file yet."
                at == 0L -> "Backup file chosen."
                System.currentTimeMillis() - at < 60_000 -> "Backed up just now."
                else -> "Backed up " + DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) + "."
            },
            fontWeight = FontWeight.Medium,
            color = if (uri == null || problem != null) OverColor else MaterialTheme.colorScheme.primary,
        )
        problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OverColor) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { create.launch("calorie-tracker-backup-${LocalDate.now()}.json") }) {
                Text(if (uri == null) "Choose backup file" else "Use another file")
            }
            if (uri != null) OutlinedButton(onClick = vm::backupNow) { Text("Back up now") }
        }
        TextButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }) {
            Text("Restore from a backup…")
        }
    }
}

@Composable
fun rememberRestoreLauncher(vm: AppViewModel) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> u?.let(vm::openBackup) }

/** "Replace everything with this backup?" — shown wherever a backup was opened. */
@Composable
fun RestoreDialog(vm: AppViewModel) {
    val b by vm.restoreCandidate.collectAsStateWithLifecycle()
    val backup = b ?: return
    val days = backup.entries.map { it.date }.distinct().sorted()
    AlertDialog(
        onDismissRequest = vm::cancelRestore,
        title = { Text("Restore this backup?") },
        text = {
            Text(
                "${backup.entries.size} logged foods" +
                    (when {
                        days.isEmpty() -> ""
                        days.size == 1 -> " (${days.first()})"
                        else -> " (${days.first()} to ${days.last()})"
                    }) +
                    ", ${backup.dayLogs.size} check-ins, ${backup.savedFoods.size} saved foods" +
                    (if (backup.plan != null) " and your plan" else "") + ".\n\n" +
                    "This adds the backup to what's in the app now; nothing already in the app is deleted. " +
                    "From then on, changes keep saving to this file.",
            )
        },
        confirmButton = { Button(onClick = vm::confirmRestore) { Text("Restore") } },
        dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Cancel") } },
    )
}
