package app.klean.core.data.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the committed fixture `src/test/resources/backup-v2.sqlite` (a schema-v2 database with one
 * row in every v2 table) for [MigrationV3Test]. It builds the file from the exported `schemas/…/2.json`
 * rather than from the live entities, so it still produces v2 after later schema bumps.
 *
 * Skipped unless asked for: `KLEAN_WRITE_V2_FIXTURE=1 ./gradlew :core:data:testDebugUnitTest --tests '*V2FixtureWriter*'`.
 */
@RunWith(AndroidJUnit4::class)
class V2FixtureWriter {
    @Test fun writeV2Fixture() {
        assumeTrue(System.getenv("KLEAN_WRITE_V2_FIXTURE") == "1")
        val schema = JSONObject(File("schemas/app.klean.core.data.db.KleanDatabase/2.json").readText()).getJSONObject("database")
        val out = File("src/test/resources/backup-v2.sqlite")
        listOf("", "-wal", "-shm", "-journal").forEach { File(out.path + it).delete() }
        val c = BundledSQLiteDriver().open(out.path)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                c.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    c.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) c.execSQL(setup.getString(i))
            c.execSQL("PRAGMA user_version = 2")
            ROWS.forEach(c::execSQL)
        } finally {
            c.close()
        }
    }

    private companion object {
        const val S = "1762700000000, 1762700000000, 'dev-v2', 0"
        val ROWS = listOf(
            "INSERT INTO change_counter VALUES (1, 12)",
            "INSERT INTO food_entry VALUES ('fe-1', 'porridge', 1762700000000, 'Asia/Dubai', NULL, 'DASHBOARD', 0, NULL, NULL, 'NOT_REQUESTED', $S, 1)",
            "INSERT INTO setting VALUES ('set-1', 'diet.gapHours', '4', $S, 2)",
            "INSERT INTO task_list VALUES ('list-1', 'Personal', 'PERSONAL', 0, $S, 3)",
            "INSERT INTO task VALUES ('task-1', 'list-1', 'buy milk', NULL, '2026-11-10', NULL, 0, NULL, NULL, 0, 'MANUAL', $S, 4)",
            "INSERT INTO task VALUES ('task-2', 'list-1', 'call the bank', NULL, NULL, NULL, 0, NULL, 1762700100000, 1, 'CAPTURE', $S, 5)",
            "INSERT INTO reminder VALUES ('rem-1', 'task-1', 'buy milk', 1762710000000, 'Asia/Dubai', 0, NULL, 'SCHEDULED', NULL, NULL, $S, 6)",
            "INSERT INTO event VALUES ('ev-1', 'dentist', 1762710000000, 1762713600000, 0, 'Asia/Dubai', NULL, NULL, NULL, 30, NULL, $S, 7)",
            "INSERT INTO contact VALUES ('c-1', 'Ahmed', '[]', '[]', NULL, 3, 14, NULL, '0,3', NULL, $S, 8)",
            "INSERT INTO workout_template VALUES ('wt-1', 'Morning', '[]', 0, $S, 9)",
            "INSERT INTO workout_session VALUES ('ws-1', 'wt-1', 1762700000000, 1762701800000, NULL, 'MANUAL', $S, 10)",
            "INSERT INTO workout_set VALUES ('wset-1', 'ws-1', 'pushups', 'STRENGTH', 0, 15, NULL, NULL, NULL, $S, 11)",
            "INSERT INTO sleep_session VALUES ('sl-1', 1762650000000, 1762678800000, 'MANUAL', NULL, $S, 12)",
        )
    }
}
