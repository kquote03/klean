package app.klean.core.data.backup

import android.content.Context
import app.klean.core.data.db.KleanDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** What a backup file contains, read without restoring it. */
data class BackupInfo(
    val exportedAt: Long,
    val schemaVersion: Int,
    val appVersion: String,
    val entryCount: Int,
    val photoCount: Int,
    val hasSecrets: Boolean,
)

/** A file that can't be restored. [reason] maps to a calm message in the UI. */
class BackupInvalid(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Not a SQLite file, or not one Klean wrote. */
        NOT_A_BACKUP,
        /** Written by a newer app version with a newer schema. */
        NEWER_VERSION,
        /** Looks like a Klean backup but parts are missing or unreadable. */
        DAMAGED,
    }
}

/**
 * Plain-text access to secrets (API keys) for backups. The Keystore-backed SecretStore arrives in M10
 * and replaces [NoSecretBackup] as the binding.
 */
interface SecretBackup {
    suspend fun exportPlain(): Map<String, String>
    suspend fun importPlain(secrets: Map<String, String>)
}

/** No secrets exist before M10. */
class NoSecretBackup @Inject constructor() : SecretBackup {
    override suspend fun exportPlain(): Map<String, String> = emptyMap()
    override suspend fun importPlain(secrets: Map<String, String>) = Unit
}

/** App build info for backup metadata. Bound in `:app`, which owns BuildConfig. */
interface BuildConfigProvider {
    val versionName: String
}

/** Restarts the app process so every singleton is rebuilt on the restored database. Bound in `:app`. */
fun interface ProcessRestarter {
    fun restart()
}

/** The live database file. Its own qualifier so tests can point it at a test-specific file. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DatabaseFile

@Module
@InstallIn(SingletonComponent::class)
object DatabaseFileModule {
    @Provides
    @DatabaseFile
    fun dbFile(@ApplicationContext ctx: Context): File = ctx.getDatabasePath(KleanDatabase.FILE_NAME)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecretBackupModule {
    @Binds abstract fun secretBackup(impl: NoSecretBackup): SecretBackup
}
