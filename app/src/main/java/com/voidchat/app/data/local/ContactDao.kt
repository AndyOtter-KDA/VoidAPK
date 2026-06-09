package com.voidchat.app.data.local

import androidx.room.*
import com.voidchat.app.data.models.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM local_contacts ORDER BY nickname ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM local_contacts WHERE displayId = :displayId LIMIT 1")
    suspend fun getContact(displayId: String): Contact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Update
    suspend fun updateContact(contact: Contact)

    @Query("DELETE FROM local_contacts WHERE displayId = :displayId")
    suspend fun deleteContact(displayId: String)
}
