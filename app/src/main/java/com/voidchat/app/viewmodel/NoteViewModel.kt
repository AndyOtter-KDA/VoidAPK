package com.voidchat.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.crypto.NoteCryptoManager
import com.voidchat.app.data.models.Note
import com.voidchat.app.data.remote.FirestoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface NoteUiState {
    object Idle : NoteUiState
    object Creating : NoteUiState
    data class Created(val shareCode: String) : NoteUiState
    object Reading : NoteUiState
    data class Read(val content: String) : NoteUiState
    object Destroyed : NoteUiState
    data class Error(val message: String) : NoteUiState
}

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<NoteUiState>(NoteUiState.Idle)
    val state = _state.asStateFlow()

    fun createNote(text: String, maxViews: Int, expiresAtMillis: Long) {
        if (text.trim().isEmpty()) {
            _state.value = NoteUiState.Error("Note content cannot be empty")
            return
        }
        viewModelScope.launch {
            _state.value = NoteUiState.Creating
            try {
                val cryptoResult = NoteCryptoManager.encryptNote(text)
                val noteId = "note_${UUID.randomUUID().toString().take(8)}"
                val note = Note(
                    noteId = noteId,
                    encryptedPayload = cryptoResult.encryptedPayload,
                    iv = cryptoResult.iv,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = expiresAtMillis,
                    maxViews = maxViews,
                    currentViews = 0,
                    destroyed = false
                )
                FirestoreManager.createNote(note)
                val shareCode = NoteCryptoManager.generateShareCode(noteId, cryptoResult.keyBase64)
                _state.value = NoteUiState.Created(shareCode)
            } catch (e: Exception) {
                _state.value = NoteUiState.Error("Failed to lock and seal note payload: ${e.localizedMessage}")
            }
        }
    }

    fun readNote(shareCode: String) {
        if (shareCode.trim().isEmpty()) {
            _state.value = NoteUiState.Error("Invalid share code pattern")
            return
        }
        _state.value = NoteUiState.Reading
        viewModelScope.launch {
            try {
                val parsed = NoteCryptoManager.parseShareCode(shareCode)
                if (parsed == null) {
                    _state.value = NoteUiState.Error("Corrupted or invalid quantum code link")
                    return@launch
                }
                
                val (noteId, keyBase64) = parsed
                val note = FirestoreManager.getNote(noteId)
                if (note == null || note.destroyed) {
                    _state.value = NoteUiState.Destroyed
                    return@launch
                }

                if (note.expiresAt > 0 && note.expiresAt < System.currentTimeMillis()) {
                    FirestoreManager.deleteNote(noteId)
                    _state.value = NoteUiState.Destroyed
                    return@launch
                }

                val decryptedResult = NoteCryptoManager.decryptNote(note.encryptedPayload, note.iv, keyBase64)
                decryptedResult.fold(
                    onSuccess = { plaintext ->
                        FirestoreManager.markNoteViewed(noteId)
                        _state.value = NoteUiState.Read(plaintext)
                    },
                    onFailure = {
                        _state.value = NoteUiState.Error("Payload decryption handshake keys mismatch.")
                    }
                )
            } catch (e: Exception) {
                _state.value = NoteUiState.Error("Read note error: ${e.localizedMessage}")
            }
        }
    }

    fun resetState() {
        _state.value = NoteUiState.Idle
    }
}
