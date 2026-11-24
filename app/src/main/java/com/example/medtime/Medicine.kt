package com.example.medtime

// One medicine: what, how much, the time of each dose, and the days to take it
data class Medicine(
    val id: Long,
    val name: String,
    val dosage: String,
    val doseTimes: List<Int>,   // minutes of the day, for example 10:00 AM = 600
    val startDate: String,      // MM/dd/yyyy
    val endDate: String,        // MM/dd/yyyy, or "" when there is no end date
    val notificationsOn: Boolean,
    val soundIndex: Int = 0     // 0 = Chime, 1 = Bell, 2 = Beep, 3 = Melody, 4 = Gentle
) {
    fun dosageText(): String = dosage.ifBlank { "1 dose" }
}
