package com.example.medtime

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// The real database inside the phone (file: medtime.db)
class MedTimeDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "medtime.db", null, 4) {

    // WAL mode: reading and writing do not block each other, so the app stays smooth
    init {
        setWriteAheadLoggingEnabled(true)
    }

    companion object {
        @Volatile
        private var instance: MedTimeDatabase? = null

        // ONE shared database object for the whole app (medicines AND accounts)
        fun get(context: Context): MedTimeDatabase {
            return instance ?: synchronized(this) {
                instance ?: MedTimeDatabase(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Table 1: the medicines
        db.execSQL(
            "CREATE TABLE medicines (" +
                    "id INTEGER PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "dosage TEXT NOT NULL, " +
                    "frequency TEXT NOT NULL DEFAULT 'custom', " +
                    "first_hour INTEGER NOT NULL DEFAULT 0, " +
                    "first_minute INTEGER NOT NULL DEFAULT 0, " +
                    "start_date TEXT NOT NULL, " +
                    "end_date TEXT NOT NULL DEFAULT '', " +
                    "dose_times TEXT NOT NULL DEFAULT '', " +
                    "notifications_on INTEGER NOT NULL, " +
                    "snooze_minutes INTEGER NOT NULL DEFAULT 0, " +
                    "sound INTEGER NOT NULL DEFAULT 0)"
        )
        // Table 2: which dose was taken, on which day
        db.execSQL(
            "CREATE TABLE dose_logs (" +
                    "medicine_id INTEGER NOT NULL, " +
                    "date_key TEXT NOT NULL, " +
                    "dose_index INTEGER NOT NULL, " +
                    "taken_at INTEGER NOT NULL, " +
                    "PRIMARY KEY (medicine_id, date_key, dose_index))"
        )
        // Table 3: the accounts
        createUsersTable(db)
    }

    // Table 3: the accounts. The email is saved in small letters and is UNIQUE.
    // The password is NEVER saved as text: only its hash (see PasswordHasher.kt).
    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "full_name TEXT NOT NULL, " +
                    "username TEXT NOT NULL, " +
                    "email TEXT NOT NULL UNIQUE, " +
                    "password_hash TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL)"
        )
    }

    // Old versions: add the new columns and keep every saved medicine
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE medicines ADD COLUMN end_date TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE medicines ADD COLUMN dose_times TEXT NOT NULL DEFAULT ''")
            // Old medicines get one dose time: their old First Take
            db.execSQL(
                "UPDATE medicines SET dose_times = CAST(first_hour * 60 + first_minute AS TEXT)"
            )
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE medicines ADD COLUMN sound INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 4) {
            // New: the accounts table (the saved medicines stay)
            createUsersTable(db)
        }
    }
}

// The functions the screens use to save and read data
object MedicineStorage {

    private fun db(context: Context): SQLiteDatabase {
        return MedTimeDatabase.get(context).writableDatabase
    }

    // Turn one row of the database into a Medicine
    private fun readMedicine(it: Cursor): Medicine {
        val oldFirst = it.getInt(it.getColumnIndexOrThrow("first_hour")) * 60 +
                it.getInt(it.getColumnIndexOrThrow("first_minute"))

        // The dose times are saved as text, for example "600,1080"
        val timesText = it.getString(it.getColumnIndexOrThrow("dose_times"))
        val times = timesText.split(",")
            .mapNotNull { part -> part.trim().toIntOrNull() }
            .sorted()

        return Medicine(
            id = it.getLong(it.getColumnIndexOrThrow("id")),
            name = it.getString(it.getColumnIndexOrThrow("name")),
            dosage = it.getString(it.getColumnIndexOrThrow("dosage")),
            doseTimes = if (times.isEmpty()) listOf(oldFirst) else times,
            startDate = it.getString(it.getColumnIndexOrThrow("start_date")),
            endDate = it.getString(it.getColumnIndexOrThrow("end_date")),
            notificationsOn = it.getInt(it.getColumnIndexOrThrow("notifications_on")) == 1,
            soundIndex = AlarmSounds.safeIndex(it.getInt(it.getColumnIndexOrThrow("sound")))
        )
    }

    // Turn a Medicine into the values we write in the database
    private fun valuesOf(medicine: Medicine): ContentValues {
        val sortedTimes = medicine.doseTimes.sorted()
        val first = sortedTimes.firstOrNull() ?: 0

        val values = ContentValues()
        values.put("id", medicine.id)
        values.put("name", medicine.name)
        values.put("dosage", medicine.dosage)
        values.put("frequency", "custom") // not used anymore
        values.put("first_hour", first / 60)
        values.put("first_minute", first % 60)
        values.put("start_date", medicine.startDate)
        values.put("end_date", medicine.endDate)
        values.put("dose_times", sortedTimes.joinToString(","))
        values.put("notifications_on", if (medicine.notificationsOn) 1 else 0)
        values.put("snooze_minutes", 0) // not used anymore
        values.put("sound", AlarmSounds.safeIndex(medicine.soundIndex))
        return values
    }

    // Read all medicines
    fun getAll(context: Context): List<Medicine> {
        val list = mutableListOf<Medicine>()
        val cursor = db(context).query(
            "medicines", null, null, null, null, null,
            "first_hour ASC, first_minute ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(readMedicine(it))
            }
        }
        return list
    }

    // Read one medicine by its id (null if it does not exist)
    fun getById(context: Context, id: Long): Medicine? {
        val cursor = db(context).query(
            "medicines", null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return readMedicine(it)
            }
        }
        return null
    }

    // Save one new medicine, and set its alarms
    fun add(context: Context, medicine: Medicine) {
        db(context).insert("medicines", null, valuesOf(medicine))
        AlarmScheduler.scheduleMedicine(context, medicine)
    }

    // Save the changes of an existing medicine, and set its alarms again
    fun update(context: Context, medicine: Medicine) {
        db(context).update(
            "medicines", valuesOf(medicine), "id = ?", arrayOf(medicine.id.toString())
        )
        AlarmScheduler.scheduleMedicine(context, medicine)
    }

    // Delete a medicine, its Taken history and its alarms
    fun delete(context: Context, id: Long) {
        AlarmScheduler.cancelMedicine(context, id)

        val database = db(context)
        database.delete("dose_logs", "medicine_id = ?", arrayOf(id.toString()))
        database.delete("medicines", "id = ?", arrayOf(id.toString()))
    }

    // Which doses were taken on one day? Answer looks like: "1700000000000:0"
    fun getTakenDoses(context: Context, dateKey: String): Set<String> {
        val result = mutableSetOf<String>()
        val cursor = db(context).query(
            "dose_logs", null, "date_key = ?", arrayOf(dateKey), null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                val medicineId = it.getLong(it.getColumnIndexOrThrow("medicine_id"))
                val doseIndex = it.getInt(it.getColumnIndexOrThrow("dose_index"))
                result.add("$medicineId:$doseIndex")
            }
        }
        return result
    }

    // Save that one dose was taken on one day, and remove its notification
    fun markDoseTaken(context: Context, medicineId: Long, dateKey: String, doseIndex: Int) {
        val values = ContentValues()
        values.put("medicine_id", medicineId)
        values.put("date_key", dateKey)
        values.put("dose_index", doseIndex)
        values.put("taken_at", System.currentTimeMillis())
        db(context).insertWithOnConflict(
            "dose_logs", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
        NotificationHelper.cancel(context, medicineId, doseIndex)
    }
}