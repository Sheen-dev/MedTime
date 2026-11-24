package com.example.medtime

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// A background worker. The database and the password check run here,
// so the screen never freezes while they work.
object AppExecutors {
    val io: ExecutorService = Executors.newSingleThreadExecutor()
}