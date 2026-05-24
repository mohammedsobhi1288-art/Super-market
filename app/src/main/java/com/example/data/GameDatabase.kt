package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Entities
@Entity(tableName = "player_state")
data class PlayerState(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val cash: Double,
    val level: Int,
    val xp: Int,
    val rating: Float,
    val isVip: Boolean,
    val isAutoRestock: Boolean,
    val score: Int
)

@Entity(tableName = "inventory")
data class InventoryItem(
    @PrimaryKey val itemId: String,
    val nameArabic: String,
    val nameEnglish: String,
    val itemType: String, // e.g., "Dairy", "Beverage", "Bakery", "Fruit", "Luxury"
    val currentStock: Int,
    val maxStock: Int,
    val costPrice: Double,
    val sellingPrice: Double,
    val isVipOnly: Boolean,
    val iconEmoji: String
)

// 2. DAOs
@Dao
interface GameDao {
    @Query("SELECT * FROM player_state WHERE id = 1")
    fun getPlayerFlow(): Flow<PlayerState?>

    @Query("SELECT * FROM player_state WHERE id = 1")
    suspend fun getPlayer(): PlayerState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(state: PlayerState)

    @Query("SELECT * FROM inventory ORDER BY isVipOnly ASC, itemId ASC")
    fun getInventoryFlow(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory")
    suspend fun getInventoryList(): List<InventoryItem>

    @Query("SELECT * FROM inventory WHERE itemId = :id")
    suspend fun getInventoryItem(id: String): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InventoryItem>)

    @Query("UPDATE inventory SET currentStock = :stock WHERE itemId = :id")
    suspend fun updateStock(id: String, stock: Int)

    @Query("UPDATE inventory SET sellingPrice = :price WHERE itemId = :id")
    suspend fun updateSellingPrice(id: String, price: Double)
}

// 3. App Database
@Database(entities = [PlayerState::class, InventoryItem::class], version = 1, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun dao(): GameDao
}
