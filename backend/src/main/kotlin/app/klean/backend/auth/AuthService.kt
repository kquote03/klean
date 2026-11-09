package app.klean.backend.auth

import app.klean.backend.ApiException
import app.klean.backend.KleanConfig
import app.klean.backend.b64Url
import app.klean.backend.db.DevicesTable
import app.klean.backend.db.KleanDb
import app.klean.backend.db.PairCodesTable
import app.klean.backend.randomBytes
import app.klean.backend.secureRandom
import app.klean.backend.sha256Hex
import app.klean.shared.id.UuidV7
import app.klean.backend.db.selectSetting
import app.klean.backend.db.upsertSetting
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.time.Clock

@Serializable data class LoginRequest(val password: String, val deviceName: String = "web")
@Serializable data class PairStartResponse(val code: String, val qr: String, val expiresAt: Long)
@Serializable data class PairClaimRequest(val code: String, val deviceName: String, val platform: String = "ANDROID")
@Serializable data class PairClaimResponse(val deviceId: String, val token: String)
@Serializable data class AppPasswordRequest(val name: String)
@Serializable data class AppPasswordResponse(val deviceId: String, val username: String, val token: String)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    val platform: String,
    val admin: Boolean,
    val createdAt: Long,
    val lastSeenAt: Long,
    /** True for the device making the request. */
    val current: Boolean = false,
)

/** The authenticated caller of a device-token route. */
data class DevicePrincipal(val id: String, val name: String, val platform: String, val admin: Boolean)

val PLATFORMS = setOf("ANDROID", "WEAR", "WEB", "DAV")

/**
 * Admin password, pairing codes and device tokens.
 * - The admin password comes from `KLEAN_ADMIN_PASSWORD` and is stored argon2id-hashed. If none is ever set, one
 *   is generated on first start and written to `<data>/admin-password.txt` (0600); it is never logged.
 * - Tokens are 32 random bytes (base64url); only their sha256 is stored.
 * - Pair codes are 8 characters from an unambiguous alphabet, single use, valid for [KleanConfig.pairCodeTtlMs].
 */
class AuthService(private val db: KleanDb, private val config: KleanConfig, private val clock: Clock) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    fun ensureAdminPassword() {
        val stored = db.blocking { selectSetting(ADMIN_HASH_KEY) }
        val fromEnv = config.adminPassword
        when {
            fromEnv != null && (stored == null || !Passwords.verify(fromEnv, stored)) -> {
                db.blocking { upsertSetting(ADMIN_HASH_KEY, Passwords.hash(fromEnv)) }
                log.info("Admin password set from KLEAN_ADMIN_PASSWORD")
            }
            fromEnv == null && stored == null -> {
                val generated = randomBytes(18).b64Url()
                val file = File(config.dataDir, "admin-password.txt")
                file.writeText(generated + "\n")
                runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
                db.blocking { upsertSetting(ADMIN_HASH_KEY, Passwords.hash(generated)) }
                log.warn("No KLEAN_ADMIN_PASSWORD set; generated one into {}", file.absolutePath)
            }
        }
    }

    suspend fun login(request: LoginRequest): PairClaimResponse {
        val hash = db.setting(ADMIN_HASH_KEY) ?: throw ApiException(HttpStatusCode.Unauthorized, "not set up")
        if (!Passwords.verify(request.password, hash)) throw ApiException(HttpStatusCode.Unauthorized, "wrong password")
        return createDevice(request.deviceName.take(200).ifBlank { "web" }, "WEB", admin = true)
    }

    suspend fun startPairing(): PairStartResponse {
        val code = newCode()
        val expiresAt = clock.now().toEpochMilliseconds() + config.pairCodeTtlMs
        db.write {
            PairCodesTable.deleteWhere { PairCodesTable.expiresAt less clock.now().toEpochMilliseconds() }
            PairCodesTable.insert {
                it[codeHash] = sha256Hex(normalise(code).toByteArray())
                it[PairCodesTable.expiresAt] = expiresAt
            }
        }
        val url = URLEncoder.encode(config.publicUrl.trimEnd('/'), Charsets.UTF_8)
        return PairStartResponse(code, "klean://pair?url=$url&code=$code", expiresAt)
    }

    suspend fun claim(request: PairClaimRequest): PairClaimResponse {
        val platform = request.platform.uppercase().takeIf { it in PLATFORMS && it != "DAV" }
            ?: throw ApiException(HttpStatusCode.BadRequest, "unknown platform")
        val hash = sha256Hex(normalise(request.code).toByteArray())
        val now = clock.now().toEpochMilliseconds()
        val ok = db.write {
            PairCodesTable.update({
                (PairCodesTable.codeHash eq hash) and (PairCodesTable.used eq false) and (PairCodesTable.expiresAt greaterEq now)
            }) { it[used] = true } == 1
        }
        if (!ok) throw ApiException(HttpStatusCode.Unauthorized, "invalid or expired code")
        return createDevice(request.deviceName.take(200).ifBlank { "device" }, platform, admin = false)
    }

    suspend fun createAppPassword(request: AppPasswordRequest): AppPasswordResponse {
        val d = createDevice(request.name.take(200).ifBlank { "DAV" }, "DAV", admin = false)
        return AppPasswordResponse(d.deviceId, DAV_USERNAME, d.token)
    }

    /** Resolves a bearer token; also bumps `last_seen_at` at most once a minute. */
    suspend fun authenticate(token: String): DevicePrincipal? {
        if (token.isBlank() || token.length > 200) return null
        val hash = sha256Hex(token.toByteArray())
        val row = db.read {
            DevicesTable.selectAll().where { (DevicesTable.tokenHash eq hash) and (DevicesTable.revoked eq false) }.singleOrNull()
        } ?: return null
        val now = clock.now().toEpochMilliseconds()
        if (now - row[DevicesTable.lastSeenAt] > 60_000) {
            db.write { DevicesTable.update({ DevicesTable.id eq row[DevicesTable.id] }) { it[lastSeenAt] = now } }
        }
        return DevicePrincipal(row[DevicesTable.id], row[DevicesTable.name], row[DevicesTable.platform], row[DevicesTable.admin])
    }

    suspend fun devices(current: String?): List<DeviceDto> = db.read {
        DevicesTable.selectAll().where { DevicesTable.revoked eq false }
            .orderBy(DevicesTable.createdAt to SortOrder.ASC)
            .map { it.toDto(current) }
    }

    /** Admins revoke anything; other devices only themselves. */
    suspend fun revoke(caller: DevicePrincipal, id: String) {
        if (!caller.admin && caller.id != id) throw ApiException(HttpStatusCode.Forbidden, "admin only")
        val n = db.write {
            DevicesTable.update({ (DevicesTable.id eq id) and (DevicesTable.revoked eq false) }) { it[revoked] = true }
        }
        if (n == 0) throw ApiException(HttpStatusCode.NotFound, "no such device")
    }

    private suspend fun createDevice(name: String, platform: String, admin: Boolean): PairClaimResponse {
        val token = randomBytes(32).b64Url()
        val id = UuidV7.generate(clock.now().toEpochMilliseconds())
        val now = clock.now().toEpochMilliseconds()
        db.write {
            DevicesTable.insert {
                it[DevicesTable.id] = id
                it[DevicesTable.name] = name
                it[DevicesTable.platform] = platform
                it[tokenHash] = sha256Hex(token.toByteArray())
                it[DevicesTable.admin] = admin
                it[createdAt] = now
                it[lastSeenAt] = now
            }
        }
        log.info("Device created: {} ({}, admin={})", id, platform, admin)
        return PairClaimResponse(id, token)
    }

    private fun ResultRow.toDto(current: String?) = DeviceDto(
        id = this[DevicesTable.id], name = this[DevicesTable.name], platform = this[DevicesTable.platform],
        admin = this[DevicesTable.admin], createdAt = this[DevicesTable.createdAt], lastSeenAt = this[DevicesTable.lastSeenAt],
        current = this[DevicesTable.id] == current,
    )

    companion object {
        const val ADMIN_HASH_KEY = "admin.passwordHash"
        const val DAV_USERNAME = "klean"
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

        fun newCode(): String {
            val chars = (1..8).map { ALPHABET[secureRandom.nextInt(ALPHABET.length)] }.joinToString("")
            return chars.substring(0, 4) + "-" + chars.substring(4)
        }

        fun normalise(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }
    }
}
