package com.example.pulseguard.emergency

import com.example.pulseguard.model.EmergencyContact
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class EmergencyContactRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun userContactsCollection() =
        firestore.collection("users")
            .document(auth.currentUser?.uid ?: throw IllegalStateException("User not logged in"))
            .collection("emergencyContacts")

    suspend fun getContacts(): List<EmergencyContact> {
        val snapshot = userContactsCollection().get().await()
        return snapshot.documents.mapNotNull { doc ->
            val name = doc.getString("name") ?: return@mapNotNull null
            val phoneNumber = doc.getString("phoneNumber") ?: return@mapNotNull null

            EmergencyContact(
                id = doc.id,
                name = name,
                phoneNumber = phoneNumber
            )
        }
    }

    suspend fun addContact(name: String, phoneNumber: String) {
        val data = mapOf(
            "name" to name,
            "phoneNumber" to phoneNumber
        )
        userContactsCollection().add(data).await()
    }

    suspend fun deleteContact(contactId: String) {
        userContactsCollection().document(contactId).delete().await()
    }
}