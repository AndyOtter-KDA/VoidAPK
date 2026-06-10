package com.voidchat.app.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.local.PreferencesManager
import com.voidchat.app.data.models.SupportTicket
import com.voidchat.app.data.models.SupportTicketReply
import com.voidchat.app.data.remote.FirestoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class SupportTicketViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val prefs = PreferencesManager(application)

    private val _tickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    val tickets: StateFlow<List<SupportTicket>> = _tickets.asStateFlow()

    private val _myId = MutableStateFlow("")
    val myId: StateFlow<String> = _myId.asStateFlow()

    private val _myUsername = MutableStateFlow("")
    val myUsername: StateFlow<String> = _myUsername.asStateFlow()

    init {
        viewModelScope.launch {
            val identity = db.identityDao().getIdentity()
            val resolvedId = identity?.displayId ?: "VOID-NODE-NULL"
            val resolvedUser = identity?.username ?: prefs.username ?: "void_operative"
            _myId.value = resolvedId
            _myUsername.value = resolvedUser

            if (resolvedId != "VOID-NODE-NULL" && resolvedId.isNotEmpty()) {
                FirestoreManager.getUserTickets(resolvedId).collectLatest { list ->
                    _tickets.value = list
                }
            }
        }
    }

    fun submitTicket(subject: String, message: String, onComplete: (Boolean, String?) -> Unit) {
        if (subject.isBlank() || message.isBlank()) {
            onComplete(false, "Subject and Message fields are required.")
            return
        }

        val ticketId = UUID.randomUUID().toString().substring(0, 8)
        val deviceInfo = "App Version: v0.0.9-TERMINAL, OS: Android ${Build.VERSION.RELEASE}, Model: ${Build.MODEL}"

        val ticket = SupportTicket(
            ticketId = ticketId,
            displayId = _myId.value,
            username = _myUsername.value,
            subject = subject.trim(),
            message = message.trim(),
            deviceInfo = deviceInfo,
            status = "open",
            createdAt = System.currentTimeMillis(),
            replies = emptyList()
        )

        viewModelScope.launch {
            try {
                FirestoreManager.submitTicket(ticket)
                onComplete(true, ticketId)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Unknown connection failure")
            }
        }
    }
}
