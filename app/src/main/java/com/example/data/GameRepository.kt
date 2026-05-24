package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class GameRepository(private val dao: GameDao) {

    val playerState: Flow<PlayerState?> = dao.getPlayerFlow()
    val inventory: Flow<List<InventoryItem>> = dao.getInventoryFlow()

    // 1. Initial State Hydration
    suspend fun checkAndHydrate() {
        // Hydrate player if missing
        val existingPlayer = dao.getPlayer()
        if (existingPlayer == null) {
            val initialPlayer = PlayerState(
                id = 1,
                name = "تاجر المستقبل",
                cash = 120.0, // Friendly starting capital
                level = 1,
                xp = 0,
                rating = 4.5f,
                isVip = false,
                isAutoRestock = false,
                score = 120
            )
            dao.insertPlayer(initialPlayer)
        }

        // Hydrate default items if missing or empty
        val existingItems = dao.getInventoryList()
        if (existingItems.isEmpty()) {
            val defaultItems = listOf(
                InventoryItem("milk", "حليب طازج", "Fresh Milk", "Dairy", 50, 500, 1.5, 3.0, false, "🥛"),
                InventoryItem("bread", "خبز بلدي", "Fresh Bread", "Bakery", 60, 500, 0.8, 1.5, false, "🍞"),
                InventoryItem("cola", "مياه غازية", "Soda Pop", "Beverages", 70, 600, 1.0, 2.0, false, "🥤"),
                InventoryItem("apple", "تفاح بلدي", "Red Apple", "Fruits", 40, 400, 1.2, 2.5, false, "🍎"),
                InventoryItem("cheese", "جبنة بيضاء", "White Cheese", "Dairy", 30, 400, 2.0, 4.0, false, "🧀"),
                InventoryItem("coffee", "بن مطحون", "Ground Coffee", "Beverages", 20, 300, 3.5, 7.0, false, "☕"),
                // VIP Luxury Items
                InventoryItem("caviar", "كافيار أسود VIP", "VIP Black Caviar", "Luxury", 0, 200, 25.0, 75.0, true, "🐟"),
                InventoryItem("saffron", "زعفران أصلي VIP", "VIP Premium Saffron", "Luxury", 0, 150, 15.0, 45.0, true, "🌸")
            )
            dao.insertItems(defaultItems)
        } else {
            // Guarantee existing items in database get the new expanded capacities
            for (item in existingItems) {
                val targetMax = when (item.itemId) {
                    "milk" -> 500
                    "bread" -> 500
                    "cola" -> 600
                    "apple" -> 400
                    "cheese" -> 400
                    "coffee" -> 300
                    "caviar" -> 200
                    "saffron" -> 150
                    else -> item.maxStock
                }
                if (item.maxStock != targetMax) {
                    dao.insertItem(item.copy(maxStock = targetMax))
                }
            }
        }
    }

    // 2. Play Actions
    suspend fun updateSellingPrice(itemId: String, price: Double) {
        dao.updateSellingPrice(itemId, price)
    }

    suspend fun stockItem(itemId: String, count: Int, cost: Double): Boolean {
        val player = dao.getPlayer() ?: return false
        val item = dao.getInventoryItem(itemId) ?: return false

        val totalCost = cost * count
        if (player.cash >= totalCost && item.currentStock + count <= item.maxStock) {
            // Deduct cash
            dao.insertPlayer(player.copy(
                cash = player.cash - totalCost,
                score = (player.cash - totalCost + (player.level * 200)).toInt()
            ))
            // Increase stock
            dao.updateStock(itemId, item.currentStock + count)
            return true
        }
        return false
    }

    suspend fun buyVipUpgrade(): Boolean {
        val player = dao.getPlayer() ?: return false
        // VIP enables golden theme and high margin products!
        dao.insertPlayer(player.copy(
            isVip = true,
            isAutoRestock = true, // Auto Restock is a premium VIP benefit
            cash = player.cash + 1000.0 // VIP welcoming bonus!
        ))
        
        // Ensure starting stock for VIP items is unlocked and hydrated
        val caviar = dao.getInventoryItem("caviar")
        if (caviar != null && caviar.currentStock == 0) {
            dao.insertItem(caviar.copy(currentStock = 5))
        }
        val saffron = dao.getInventoryItem("saffron")
        if (saffron != null && saffron.currentStock == 0) {
            dao.insertItem(saffron.copy(currentStock = 4))
        }
        return true
    }

    suspend fun toggleAutoRestock(): Boolean {
        val player = dao.getPlayer() ?: return false
        if (player.isVip) {
            dao.insertPlayer(player.copy(isAutoRestock = !player.isAutoRestock))
            return true
        }
        return false
    }

    suspend fun sellToCustomer(itemId: String, finalPrice: Double): Boolean {
        val player = dao.getPlayer() ?: return false
        val item = dao.getInventoryItem(itemId) ?: return false

        if (item.currentStock > 0) {
            // Earn cash & XP
            val xpGain = (finalPrice * 2).toInt().coerceAtLeast(1)
            val nextXp = player.xp + xpGain
            val newLevel = if (nextXp >= player.level * 150) player.level + 1 else player.level
            val isLevelUp = newLevel > player.level

            val nextXpFinal = if (isLevelUp) 0 else nextXp
            val newCash = player.cash + finalPrice

            dao.insertPlayer(player.copy(
                cash = newCash,
                xp = nextXpFinal,
                level = newLevel,
                rating = (player.rating + 0.02f).coerceAtMost(5.0f),
                score = (newCash + (newLevel * 200)).toInt()
            ))

            // Decrease stock
            dao.updateStock(itemId, item.currentStock - 1)
            return true
        }
        return false
    }

    suspend fun registerCustomerRatingChange(increase: Boolean) {
        val player = dao.getPlayer() ?: return
        val modifier = if (increase) 0.05f else -0.1f
        val newRating = (player.rating + modifier).coerceIn(1.0f, 5.0f)
        dao.insertPlayer(player.copy(rating = newRating))
    }

    suspend fun updatePlayerName(newName: String) {
        val player = dao.getPlayer() ?: return
        dao.insertPlayer(player.copy(name = newName))
    }

    suspend fun deductAuctionPayment(amount: Double, xpBonus: Int) {
        val player = dao.getPlayer() ?: return
        val nextXp = player.xp + xpBonus
        val newLevel = if (nextXp >= player.level * 150) player.level + 1 else player.level
        val finalXp = if (newLevel > player.level) 0 else nextXp
        val newCash = (player.cash - amount).coerceAtLeast(0.0)
        dao.insertPlayer(player.copy(
            cash = newCash,
            xp = finalXp,
            level = newLevel,
            score = (newCash + (newLevel * 200)).toInt()
        ))
    }
}
