package app.klean.core.data.settings

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.SettingEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.diet.DeliveryMatcher
import app.klean.shared.id.nameUuid
import app.klean.shared.time.QuietHours
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A synced setting: stored as JSON in the `setting` table under [name]. */
class SettingKey<T>(val name: String, val default: T, val serializer: KSerializer<T>)

/** Every synced setting (plan §3). Device-only preferences live in DevicePrefs instead. */
object SyncedKeys {
    val QuietHoursKey = SettingKey("core.quietHours", QuietHours.DEFAULT, QuietHours.serializer())
    val DisabledModules = SettingKey("core.modules.disabled", emptySet<String>(), SetSerializer(String.serializer()))
    val DietPromptsEnabled = SettingKey("diet.promptsEnabled", true, Boolean.serializer())
    val DietCadenceHours = SettingKey("diet.cadenceHours", 6, Int.serializer())
    val DietLearnMealTimes = SettingKey("diet.learnMealTimes", true, Boolean.serializer())
    val DietUnlockSheet = SettingKey("diet.unlockSheet", true, Boolean.serializer())
    val DietStatusNotification = SettingKey("diet.statusNotification", true, Boolean.serializer())
    val DietShowCalories = SettingKey("diet.showCalories", false, Boolean.serializer())
    /** Daily calorie goal for the Diet progress bar; 0 = no goal. */
    val DietCalorieGoal = SettingKey("diet.calorieGoal", 0, Int.serializer())
    val DietGapThresholdMinutes = SettingKey("diet.gapThresholdMinutes", 240, Int.serializer())
    val DietDeliveryApps = SettingKey("diet.deliveryApps", DeliveryMatcher.DEFAULT_APPS, ListSerializer(String.serializer()))
    /** Extra words that mark a delivery notification as delivered. Empty = built-in matcher only. */
    val DietDeliveryMatchWords = SettingKey("diet.deliveryMatchWords", emptyList<String>(), ListSerializer(String.serializer()))
}

@Singleton
class SettingsRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun <T> observe(key: SettingKey<T>): Flow<T> = db.settingDao().observe(key.name)
        .map { e -> e?.takeUnless { it.sync.deleted }?.let { decode(key, it.valueJson) } ?: key.default }
        .distinctUntilChanged()

    suspend fun <T> get(key: SettingKey<T>): T = observe(key).first()

    suspend fun <T> set(key: SettingKey<T>, value: T) = update(key) { value }

    /** Read-modify-write in one transaction, so concurrent updates never lose each other. */
    suspend fun <T> update(key: SettingKey<T>, transform: (T) -> T) {
        db.transact {
            val prev = db.settingDao().get(key.name)
            val current = prev?.takeUnless { it.sync.deleted }?.let { decode(key, it.valueJson) } ?: key.default
            val value = transform(current)
            db.settingDao().upsert(
                SettingEntity(
                    id = nameUuid(key.name),
                    key = key.name,
                    valueJson = json.encodeToString(key.serializer, value),
                    sync = stamper.stamp(prev?.sync, deleted = false),
                ),
            )
        }
    }

    /** A value written by a newer app version may not parse; fall back to the default instead of crashing. */
    private fun <T> decode(key: SettingKey<T>, raw: String): T =
        runCatching { json.decodeFromString(key.serializer, raw) }.getOrDefault(key.default)
}
