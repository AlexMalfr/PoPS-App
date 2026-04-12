package com.malfreyt.alexandre.pops_app.credentials

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential

data class OasisPasswordCredential(
    val login: String,
    val password: String,
)

object OasisCredentialManager {
    suspend fun getPasswordCredential(
        activity: ComponentActivity,
        loginHint: String?,
    ): OasisPasswordCredential {
        val request = GetCredentialRequest(
            credentialOptions = listOf(
                if (loginHint.isNullOrBlank()) {
                    GetPasswordOption()
                } else {
                    GetPasswordOption(allowedUserIds = setOf(loginHint.trim()))
                }
            )
        )
        val response = CredentialManager.create(activity).getCredential(activity, request)
        val credential = response.credential as? PasswordCredential
            ?: throw IllegalStateException("Unsupported credential type returned")
        return OasisPasswordCredential(login = credential.id, password = credential.password)
    }

    suspend fun savePasswordCredential(
        activity: ComponentActivity,
        login: String,
        password: String,
    ) {
        if (login.isBlank() || password.isBlank()) {
            return
        }

        CredentialManager.create(activity).createCredential(
            context = activity,
            request = CreatePasswordRequest(
                id = login.trim(),
                password = password,
            ),
        )
    }
}

tailrec fun Context.findComponentActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
}