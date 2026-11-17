package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirestoreProvider {
    private val configuredDbs = mutableSetOf<String>()

    @Synchronized
    fun getInstance(context: Context): FirebaseFirestore {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        if (!configuredDbs.contains(databaseId)) {
            configuredDbs.add(databaseId)
            try {
                firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(100L * 1024L * 1024L)
                            .build()
                    )
                    .build()
            } catch (_: IllegalStateException) {
                // Firestore instance was already started; ignore safely.
            }
        }
        return firestore
    }
}
