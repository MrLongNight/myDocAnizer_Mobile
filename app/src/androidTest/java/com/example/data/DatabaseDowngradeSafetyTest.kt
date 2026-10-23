package com.example.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseDowngradeSafetyTest {

    @Test
    fun unsupportedDowngradeFailsWithoutDeletingExistingDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "database_downgrade_safety_test"
        val databaseFile = context.getDatabasePath(databaseName)
        context.deleteDatabase(databaseName)

        var initialDatabase: AppDatabase? = null
        var downgradedDatabase: AppDatabase? = null
        try {
            val initial = AppDatabase.buildDatabase(context, databaseName)
            initialDatabase = initial
            val writable = initial.openHelper.writableDatabase
            writable.execSQL("CREATE TABLE downgrade_sentinel (value TEXT NOT NULL)")
            writable.execSQL("INSERT INTO downgrade_sentinel(value) VALUES ('KEEP_AFTER_DOWNGRADE')")
            // Simulate a newer schema version without modifying the production database.
            writable.execSQL("PRAGMA user_version = 10")
            initial.close()
            initialDatabase = null

            val downgraded = AppDatabase.buildDatabase(context, databaseName)
            downgradedDatabase = downgraded
            var downgradeRejected = false
            try {
                downgraded.openHelper.writableDatabase
            } catch (_: IllegalStateException) {
                downgradeRejected = true
            }
            assertTrue("An unsupported downgrade must fail closed", downgradeRejected)
            runCatching { downgraded.close() }
            downgradedDatabase = null

            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { sqlite ->
                assertEquals(
                    "Unsupported downgrade must not change the original schema version",
                    10,
                    sqlite.version
                )
                sqlite.rawQuery("SELECT value FROM downgrade_sentinel", null).use { cursor ->
                    assertTrue("Original sentinel row must remain present", cursor.moveToFirst())
                    assertEquals("KEEP_AFTER_DOWNGRADE", cursor.getString(0))
                }
            }
        } finally {
            runCatching { initialDatabase?.close() }
            runCatching { downgradedDatabase?.close() }
            context.deleteDatabase(databaseName)
        }
    }
}
