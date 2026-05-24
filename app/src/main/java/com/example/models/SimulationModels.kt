package com.example.models

// 1. Live Multiplayer Rival
data class MultiplayerRival(
    val id: String,
    val name: String,
    val level: Int,
    val cash: Double,
    val rating: Float,
    val emoji: String,
    val statusArabic: String,
    val isVip: Boolean = false
)

// 2. Chat Message
data class ChatMessage(
    val id: Long,
    val senderName: String,
    val senderEmoji: String,
    val messageArabic: String,
    val isVip: Boolean = false,
    val timeString: String
)

// 3. Wholesale Auction Item
data class AuctionItem(
    val id: String,
    val nameArabic: String,
    val emoji: String,
    val quantity: Int,
    val normalCost: Double,
    val currentHighBid: Double,
    val highestBidderName: String,
    val highestBidderIsPlayer: Boolean,
    val secondsRemaining: Int,
    val isActive: Boolean,
    val minBidIncrement: Double = 1.0,
    val comment: String? = null,
    val commenterEmoji: String? = null
)
