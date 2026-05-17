package com.example.pulseguard.logs

import com.example.pulseguard.model.HrLogBucket
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class HrLogRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun logsCollection() = firestore.collection("users")
        .document(auth.currentUser?.uid?:error("User not logged in"))
        .collection("hrLogs")

    suspend fun saveBucket(bucket: HrLogBucket) {
        logsCollection().add(bucket).await()
    }

    suspend fun getLast24Hours(): List<HrLogBucket> {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L

        val snapshot = logsCollection()
            .whereGreaterThanOrEqualTo("bucketEnd", cutoff)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->  doc.toObject(HrLogBucket::class.java)
        }.sortedBy { it.bucketStart }
    }

    suspend fun deleteOlderThan24Hours() {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L

        val snapshot = logsCollection()
            .whereLessThan("bucketEnd", cutoff)
            .get()
            .await()

        val batch = firestore.batch()
        snapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }
}