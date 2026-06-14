package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MensaDao {

    @Query("SELECT * FROM mensa_menu_days WHERE site = :site")
    fun getMensaMenuForSite(site: String): Flow<List<CachedMensaMenuDay>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMensaMenuDays(days: List<CachedMensaMenuDay>)

    @Query("DELETE FROM mensa_menu_days WHERE site = :site")
    fun deleteMensaMenuDaysForSite(site: String): Int

    @Transaction
    fun refreshMensaMenuForSite(site: String, days: List<CachedMensaMenuDay>) {
        deleteMensaMenuDaysForSite(site)
        insertMensaMenuDays(days)
    }
}
