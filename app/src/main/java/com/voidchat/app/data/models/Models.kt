package com.voidchat.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_identity")
data class LocalIdentity(
    @PrimaryKey val id: String,
    val keyPairAlias: String,
    val publicKeyBase64: String,
    val displayId: String,
    val username: String,
    val recoveryPhraseHash: String,
    val createdAt: Long,
    val deviceName: String
)

@Entity(tableName = "local_messages")
data class Message(
    @PrimaryKey val messageId: String,
    val chatId: String,
    val senderId: String,
    val encryptedPayload: String,
    val iv: String,
    val timestamp: Long,
    val selfDestructSeconds: Int,
    val destroyed: Boolean,
    val readAt: Long,
    val isRead: Boolean,
    val decryptedText: String = ""
)

@Entity(tableName = "local_chats")
data class Chat(
    @PrimaryKey val chatId: String,
    val participantA: String,
    val participantB: String,
    val publicKeyA: String,
    val publicKeyB: String,
    val keyExchangeComplete: Boolean,
    val createdAt: Long,
    val lastMessageAt: Long,
    val backgroundTheme: String
)

@Entity(tableName = "local_contacts")
data class Contact(
    @PrimaryKey val displayId: String,
    val nickname: String,
    val publicKeyBase64: String,
    val lastSeen: Long,
    val isFavorite: Boolean
)

@Entity(tableName = "local_notes")
data class Note(
    @PrimaryKey val noteId: String,
    val encryptedPayload: String,
    val iv: String,
    val createdAt: Long,
    val expiresAt: Long,
    val maxViews: Int,
    val currentViews: Int,
    val destroyed: Boolean,
    val salt: String? = null,
    val hasPassword: Boolean = false,
    val keyBase64: String? = null,
    val shortCode: String = "",
    val shortKey: String? = null
)

@Entity(tableName = "local_groups")
data class GroupChat(
    @PrimaryKey val groupId: String,
    val name: String,
    val createdBy: String,
    val createdAt: Long,
    val members: String, // Stored as CSV or serialized string
    val currentGroupKeyGeneration: Int,
    val defaultSelfDestructSeconds: Int,
    val description: String = "",
    val pinnedMessageId: String = "",
    val pinnedMessageText: String = "",
    val bannedMembers: String = ""
)

data class GroupMember(
    val displayId: String,
    val publicKeyBase64: String,
    val role: String,
    val joinedAt: Long
)

@Entity(tableName = "local_group_messages")
data class GroupMessage(
    @PrimaryKey val messageId: String,
    val groupId: String,
    val senderId: String,
    val encryptedPayload: String,
    val iv: String,
    val keyGeneration: Int,
    val timestamp: Long,
    val selfDestructSeconds: Int,
    val destroyed: Boolean
)

data class InviteLink(
    val linkId: String,
    val encryptedGroupKey: String,
    val inviteKeyBase64: String,
    val createdBy: String,
    val createdAt: Long,
    val expiresAt: Long,
    val maxUses: Int,
    val currentUses: Int,
    val active: Boolean
)

data class DonationConfig(
    val btcAddress: String = "bc1qxy2kg3ut78dhb6277gjsseq77dk6889sgv7889",
    val ethAddress: String = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
    val xmrAddress: String = "44AFFq5kSiGbUAtThX...",
    val lightningAddress: String = "void@ln.tips",
    val monthlyCost: Double = 120.0,
    val monthlyDonationsFiat: Double = 35.0,
    val fundingPercentage: Int = 29,
    val donationMessage: String = "Keep the E2E channels operational!"
)

data class KeyPairExport(
    val privateKeyBase64: String,
    val publicKeyBase64: String,
    val displayId: String
)

data class UsernameRegistration(
    val username: String,
    val displayId: String,
    val registeredAt: Long
)

data class AppSettings(
    val soundEnabled: Boolean,
    val defaultSelfDestruct: Int,
    val biometricLock: Boolean,
    val theme: String,
    val pinCode: String? = null
)

data class BackupPayload(
    val version: Int,
    val identityKeyPair: KeyPairExport,
    val chatKeys: Map<String, String>,
    val contacts: List<String>,
    val settings: AppSettings,
    val messageHistory: List<String>
)

data class SupportTicketReply(
    val replyId: String = "",
    val senderId: String = "",
    val message: String = "",
    val createdAt: Long = 0L
)

data class SupportTicket(
    val ticketId: String = "",
    val displayId: String = "",
    val username: String = "",
    val subject: String = "",
    val message: String = "",
    val deviceInfo: String = "",
    val status: String = "open",
    val createdAt: Long = 0L,
    val replies: List<SupportTicketReply> = emptyList()
)
