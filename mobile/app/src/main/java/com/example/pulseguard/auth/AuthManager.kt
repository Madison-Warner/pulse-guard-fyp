package com.example.pulseguard.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    fun currentUser() = auth.currentUser

    suspend fun signIn(email: String, password: String): Result<Unit> {
        return try{
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> {
        return try{
            auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}