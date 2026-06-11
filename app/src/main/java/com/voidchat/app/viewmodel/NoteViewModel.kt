package com.voidchat.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.crypto.NoteCryptoManager
import com.voidchat.app.data.models.Note
import com.voidchat.app.data.models.GroupChat
import com.voidchat.app.data.models.GroupMember
import com.voidchat.app.data.models.InviteLink
import com.voidchat.app.data.remote.FirestoreManager
import com.voidchat.app.data.remote.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface NoteUiState {
    object Idle : NoteUiState
    object Creating : NoteUiState
    data class Created(val code: String, val hasPassword: Boolean, val password: String? = null) : NoteUiState
    object Reading : NoteUiState
    data class Read(val content: String) : NoteUiState
    object Destroyed : NoteUiState
    data class Error(val message: String) : NoteUiState
}

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val db = com.voidchat.app.data.local.AppDatabase.getDatabase(application)
    private val _state = MutableStateFlow<NoteUiState>(NoteUiState.Idle)
    val state = _state.asStateFlow()

    private val _groupChats = MutableStateFlow<List<GroupChat>>(emptyList())
    val groupChats = _groupChats.asStateFlow()

    init {
        loadGroupChats()
    }

    fun loadGroupChats() {
        viewModelScope.launch {
            try {
                val identity = db.identityDao().getIdentity()
                val myDisplayId = identity?.displayId ?: "UNKNOWN"
                FirestoreManager.getGroups(myDisplayId)
                    .catch { error ->
                        android.util.Log.e("NoteViewModel", "loadGroupChats collect error: ${error.message}", error)
                    }
                    .collect { list ->
                        val myGroups = list.filter { group ->
                            group.createdBy == myDisplayId || group.members.split(",").contains(myDisplayId)
                        }
                        _groupChats.value = myGroups
                    }
            } catch (e: Exception) {
                android.util.Log.e("NoteViewModel", "loadGroupChats failed: ${e.message}", e)
            }
        }
    }

    fun createGroupInviteAndAppend(groupId: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val identity = db.identityDao().getIdentity()
                val myDisplayId = identity?.displayId ?: "UNKNOWN"
                val linkId = "inv_${UUID.randomUUID().toString().take(6)}"
                val invite = InviteLink(
                    linkId = linkId,
                    encryptedGroupKey = "ENC_GROUP_KEY_STUB",
                    inviteKeyBase64 = "INV_KEY_BASE64_STUB",
                    createdBy = myDisplayId,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = 0L,
                    maxUses = 100,
                    currentUses = 0,
                    active = true
                )
                FirestoreManager.createInviteLink(groupId, invite)
                val inviteUrl = "void://group/$groupId/$linkId"
                onComplete(inviteUrl)
            } catch (e: Exception) {
                android.util.Log.e("NoteViewModel", "createGroupInviteAndAppend failed: ${e.message}", e)
            }
        }
    }

    fun joinGroupFromNote(inviteUrl: String, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                if (!inviteUrl.startsWith("void://group/")) {
                    onComplete(null)
                    return@launch
                }
                val clean = inviteUrl.removePrefix("void://group/")
                val parts = clean.split("/")
                if (parts.size >= 2) {
                    val groupId = parts[0]
                    val identity = db.identityDao().getIdentity()
                    val myDisplayId = identity?.displayId ?: "UNKNOWN"
                    
                    val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val groupDoc = dbFirestore.collection("groups").document(groupId).get().await()
                    if (groupDoc.exists()) {
                        val bans = groupDoc.getString("bannedMembers") ?: ""
                        if (bans.split(",").contains(myDisplayId)) {
                            onComplete(null)
                            return@launch
                        }
                    }
                    
                    val grpMember = GroupMember(
                        displayId = myDisplayId,
                        publicKeyBase64 = "MOCK_EX_PUBKEY_B64",
                        role = "MEMBER",
                        joinedAt = System.currentTimeMillis()
                    )
                    FirestoreManager.joinGroup(groupId, grpMember)
                    onComplete(groupId)
                } else {
                    onComplete(null)
                }
            } catch (e: Exception) {
                android.util.Log.e("NoteViewModel", "joinGroupFromNote failed: ${e.message}", e)
                onComplete(null)
            }
        }
    }

    fun createNote(text: String, password: String?, expiryOptionSeconds: Int) {
        if (text.trim().isEmpty()) {
            _state.value = NoteUiState.Error("Note content cannot be empty")
            return
        }
        viewModelScope.launch {
            _state.value = NoteUiState.Creating
            try {
                // Generate a robust unique ID
                val noteId = "note_${UUID.randomUUID().toString().hashCode().let { if (it < 0) -it else it }.toString().take(8)}"
                val hasPassword = !password.isNullOrEmpty()

                // Encrypt payload using PBKDF2/AES-GCM
                val cryptoResult = NoteCryptoManager.encryptNote(text, password)

                // Generate simple share code
                val shareCode = NoteCryptoManager.generateNoteCode(noteId, cryptoResult.keyBase64, hasPassword)

                // Store in-memory mapping
                NoteCryptoManager.storeMapping(shareCode.take(5), noteId, cryptoResult.keyBase64)

                // Compute expiration time
                val expiresAtMillis = if (expiryOptionSeconds == 0) 0L else System.currentTimeMillis() + (expiryOptionSeconds * 1000L)

                // Prepare note record
                val note = Note(
                    noteId = noteId,
                    encryptedPayload = cryptoResult.encryptedPayload,
                    iv = cryptoResult.iv,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = expiresAtMillis,
                    maxViews = 1,
                    currentViews = 0,
                    destroyed = false,
                    salt = cryptoResult.saltBase64,
                    hasPassword = hasPassword,
                    keyBase64 = cryptoResult.keyBase64,
                    shortCode = noteId.take(5),
                    shortKey = if (hasPassword) null else cryptoResult.keyBase64.filter { it.isLetterOrDigit() }.take(6)
                )

                // Upload
                FirestoreManager.uploadNote(note) { uploadedId ->
                    if (uploadedId.isNotEmpty()) {
                        _state.value = NoteUiState.Created(
                            code = shareCode,
                            hasPassword = hasPassword,
                            password = password
                        )
                    } else {
                        _state.value = NoteUiState.Error("Failed to upload secure note package to Firestore")
                    }
                }
            } catch (e: Exception) {
                _state.value = NoteUiState.Error("Failed to lock and seal note payload: ${e.localizedMessage}")
            }
        }
    }

    fun readNote(shareCode: String, password: String?) {
        val cleanCode = shareCode.trim()
        if (cleanCode.isEmpty()) {
            _state.value = NoteUiState.Error("Decryption code cannot be empty")
            return
        }
        _state.value = NoteUiState.Reading
        viewModelScope.launch {
            try {
                val parts = cleanCode.split("-", limit = 2)
                val shortCode = parts[0].trim()

                FirestoreManager.getNoteByShortCode(shortCode) { note ->
                    if (note == null || note.destroyed) {
                        _state.value = NoteUiState.Destroyed
                        return@getNoteByShortCode
                    }

                    if (note.expiresAt > 0 && note.expiresAt < System.currentTimeMillis()) {
                        viewModelScope.launch {
                            FirestoreManager.deleteNote(note.noteId)
                        }
                        _state.value = NoteUiState.Destroyed
                        return@getNoteByShortCode
                    }

                    if (note.hasPassword && password.isNullOrEmpty()) {
                        _state.value = NoteUiState.Error("This note is protected with a cognitive password.")
                        return@getNoteByShortCode
                    }

                    val decryptedResult = NoteCryptoManager.decryptNote(
                        encryptedPayload = note.encryptedPayload,
                        iv = note.iv,
                        keyBase64 = note.keyBase64,
                        password = password,
                        saltBase64 = note.salt
                    )

                    decryptedResult.fold(
                        onSuccess = { plaintext ->
                            viewModelScope.launch {
                                FirestoreManager.markNoteViewed(note.noteId)
                            }
                            _state.value = NoteUiState.Read(plaintext)
                        },
                        onFailure = {
                            _state.value = NoteUiState.Error("Cognitive key decryption mismatch. Check code/password.")
                        }
                    )
                }
            } catch (e: Exception) {
                _state.value = NoteUiState.Error("Read note error: ${e.localizedMessage}")
            }
        }
    }

    fun resetState() {
        _state.value = NoteUiState.Idle
    }
}
