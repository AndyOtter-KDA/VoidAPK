package com.voidchat.app.data.local

import androidx.room.*
import com.voidchat.app.data.models.LocalIdentity

@Dao
interface IdentityDao {
    @Query("SELECT * FROM local_identity LIMIT 1")
    suspend fun getIdentity(): LocalIdentity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentity(identity: LocalIdentity)

    @Query("DELETE FROM local_identity")
    suspend fun deleteIdentity()
}
