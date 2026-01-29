package com.example.pulseguard.emergency

import com.example.pulseguard.model.EmergencyContact

class EmergencyContactManager {
    private val contacts = mutableListOf<EmergencyContact>()

    fun getContacts(): List<EmergencyContact> = contacts.toList()

    fun addContact(contact: EmergencyContact) {
        contacts.add(contact)
    }

    fun removeContact(contactId: String) {
       contacts.removeAll { it.id == contactId }
    }

    fun clearAll() {
        contacts.clear()
    }
}