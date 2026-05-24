package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameRepository
import com.example.data.InventoryItem
import com.example.data.PlayerState
import com.example.models.AuctionItem
import com.example.models.ChatMessage
import com.example.models.MultiplayerRival
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameViewModel(private val repository: GameRepository) : ViewModel() {

    // 1. Data flows from Room
    val playerState: StateFlow<PlayerState?> = repository.playerState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val inventory: StateFlow<List<InventoryItem>> = repository.inventory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Simulated Multiplayer State
    private val _rivals = MutableStateFlow<List<MultiplayerRival>>(emptyList())
    val rivals: StateFlow<List<MultiplayerRival>> = _rivals.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _activeAuction = MutableStateFlow<AuctionItem?>(null)
    val activeAuction: StateFlow<AuctionItem?> = _activeAuction.asStateFlow()

    // 3. UI Control States
    private val _showVipPaymentSheet = MutableStateFlow(false)
    val showVipPaymentSheet = _showVipPaymentSheet.asStateFlow()

    private val _showVipSuccessDialog = MutableStateFlow(false)
    val showVipSuccessDialog = _showVipSuccessDialog.asStateFlow()

    private val _recentNotifications = MutableStateFlow<List<String>>(emptyList())
    val recentNotifications = _recentNotifications.asStateFlow()

    private val _isRegisteringMuted = MutableStateFlow(false)
    val isRegisteringMuted = _isRegisteringMuted.asStateFlow()

    // Active Simulation Jobs
    private var customerSimulationJob: Job? = null
    private var rivalsSimulationJob: Job? = null
    private var auctionSimulationJob: Job? = null

    init {
        initRivals()
        initChat()
        generateNewAuction()
        viewModelScope.launch {
            repository.checkAndHydrate()
            startSimulationLoops()
        }
    }

    // --- Core Simulation Loops ---
    private fun startSimulationLoops() {
        // A. Customer Visits Simulation Loop (Every 6-9 seconds)
        customerSimulationJob = viewModelScope.launch {
            while (true) {
                delay(Random.nextLong(6000, 9500))
                simulateCustomerVisit()
            }
        }

        // B. Rivals Progression Simulation (Every 12-15 seconds)
        rivalsSimulationJob = viewModelScope.launch {
            while (true) {
                delay(Random.nextLong(11000, 15000))
                simulateRivalsActivity()
            }
        }

        // C. Live Auction Ticking Loop (Every 1 second)
        auctionSimulationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                tickAuction()
            }
        }
    }

    // --- 1. Customer Buy Simulator ---
    private suspend fun simulateCustomerVisit() {
        val player = playerState.value ?: return
        val currentInventory = inventory.value.filter { it.currentStock > 0 }

        if (currentInventory.isEmpty()) {
            addNotification("⚠️ زبون غادر غاضباً لعدم توفر أي بضائع بالرفوف!")
            repository.registerCustomerRatingChange(false)
            addSystemChatMessage("🗣️ أحد الزبائن", "ما هذا؟ الرفوف فارغة تماماً في هذا المحل! سأذهب لمنافس آخر.")
            return
        }

        // Select 1 to 2 products to buy
        val itemsToBuy = currentInventory.shuffled().take(Random.nextInt(1, 3))
        for (item in itemsToBuy) {
            // Check pricing reaction
            // Normal premium markup is ~1.5x up to 2.2x cost price.
            val cost = item.costPrice
            val sellPrice = item.sellingPrice
            val ratio = if (cost > 0) sellPrice / cost else 1.0

            when {
                sellPrice <= 0 -> {
                    // Item configured with no price
                    continue
                }
                ratio < 0.9 -> {
                    // Cheap sale! Customer loves it
                    val sold = repository.sellToCustomer(item.itemId, sellPrice)
                    if (sold) {
                        addNotification("❤️ زبون سعيد جداً بالسعر الرخيص واشترى ${item.nameArabic} بـ ${String.format("%.1f", sellPrice)}$")
                        repository.registerCustomerRatingChange(true)
                    }
                }
                ratio <= 2.2 -> {
                    // Normal pricing
                    val sold = repository.sellToCustomer(item.itemId, sellPrice)
                    if (sold) {
                        addNotification("🛒 زبون اشترى ${item.nameArabic} بسعر ${String.format("%.1f", sellPrice)}$")
                    }
                }
                ratio <= 3.5 -> {
                    // High Price - 40% chance of buying anyway, else complain
                    if (Random.nextFloat() < 0.40f) {
                        val sold = repository.sellToCustomer(item.itemId, sellPrice)
                        if (sold) {
                            addNotification("💰 زبون متردد قرر شراء ${item.nameArabic} بسعر مرتفع ${String.format("%.1f", sellPrice)}$")
                        }
                    } else {
                        addNotification("😡 زبون غاضب من سعر ${item.nameArabic} المرتفع (${String.format("%.1f", sellPrice)}$)")
                        repository.registerCustomerRatingChange(false)
                    }
                }
                else -> {
                    // Exorbitant price - no normal customer buys
                    addNotification("❌ زبون رفض تماماً شراء ${item.nameArabic} بسبب السعر الخيالي!")
                    repository.registerCustomerRatingChange(false)
                }
            }
        }

        // Automatic VIP Customer Visit (VIP bonus!)
        if (player.isVip && Random.nextFloat() < 0.5f) {
            simulateVipCustomerVisit(player)
        }
    }

    private suspend fun simulateVipCustomerVisit(player: PlayerState) {
        // VIP customer is wealthy and buys VIP luxury products (caviar/saffron) or regular items at 2x payout!
        val luxuryItems = inventory.value.filter { it.isVipOnly && it.currentStock > 0 }
        
        if (luxuryItems.isNotEmpty()) {
            val luxury = luxuryItems.random()
            // VIP customers pay full selling price with +50% extra bonus cash!
            val finalPrice = luxury.sellingPrice * 1.5
            val sold = repository.sellToCustomer(luxury.itemId, finalPrice)
            if (sold) {
                addNotification("⭐ زبون VIP ثري اشترى ${luxury.nameArabic} ودفع ضعف السعر: ${String.format("%.1f", finalPrice)}$!")
                addSystemChatMessage("👑 زبون VIP", "خدمة رائعة! بضائع الـ VIP متوفرة ولذيذة جداً.")
            }
        } else {
            // VIP buys milk/bread at 1.8x price
            val anyStock = inventory.value.filter { it.currentStock > 0 }
            if (anyStock.isNotEmpty()) {
                val item = anyStock.random()
                val finalPrice = item.sellingPrice * 1.8
                val sold = repository.sellToCustomer(item.itemId, finalPrice)
                if (sold) {
                    addNotification("⭐ زبون VIP اشترى منتج عادي [${item.nameArabic}] ودفع إكرامية إضافية: ${String.format("%.1f", finalPrice)}$!")
                }
            }
        }

        // Auto Restock simulation if active (VIP feature)
        if (player.isAutoRestock) {
            autoRestockVip()
        }
    }

    private suspend fun autoRestockVip() {
        val currentInv = inventory.value
        val player = playerState.value ?: return
        for (item in currentInv) {
            // If stock drops to 2 or less, auto buy 5 items if affordable
            if (item.currentStock <= 2) {
                val qtyToBuy = 6
                val totalCost = item.costPrice * qtyToBuy
                if (player.cash >= totalCost + 15.0) { // Keep safety cash margin of 15$
                    val success = repository.stockItem(item.itemId, qtyToBuy, item.costPrice)
                    if (success) {
                        addNotification("🛡️ المساعد التلقائي (VIP): أعاد تعبئة ${item.nameArabic} تلقائياً (+ $qtyToBuy)!")
                    }
                }
            }
        }
    }

    // --- 2. Live Rivals Simulator ---
    private fun initRivals() {
        _rivals.value = listOf(
            MultiplayerRival("rival_1", "سوبر ماركت الهدى", 2, 850.0, 4.6f, "🏢", "متصل - يراقب الأسعار بالخارج"),
            MultiplayerRival("rival_2", "بقالة السعادة ورضا", 1, 350.0, 4.2f, "🏪", "نشط - يقوم بترتيب الرفوف"),
            MultiplayerRival("rival_3", "هايبر البركة الإسلامي", 3, 2200.0, 4.8f, "🏬", "متصل - بطل المبيعات اليومية", isVip = true),
            MultiplayerRival("rival_4", "دكان أبو علي الذكي", 2, 600.0, 4.1f, "🏠", "أونلاين - يستعد للمزايدات الجملة"),
            MultiplayerRival("rival_5", "سوبر ماركت النجمة الذهبية", 4, 4500.0, 4.9f, "🏰", "نشط - يسيطر على سوق التجزئة", isVip = true)
        )
    }

    private suspend fun simulateRivalsActivity() {
        val currentRivals = _rivals.value.toMutableList()
        if (currentRivals.isEmpty()) return
        val index = Random.nextInt(currentRivals.size)
        val rival = currentRivals[index]

        // Rivals randomly earn cash or upgrade
        val cashGain = Random.nextDouble(50.0, 200.0)
        val nextCash = rival.cash + cashGain
        val levelUpChance = Random.nextFloat() < 0.15f
        val nextLevel = if (levelUpChance) rival.level + 1 else rival.level

        val statuses = listOf(
            "نشط - قام بشراء كمية من الحليب بالجملة",
            "متصل - قام بتعديل أسعار العصائر والمياه الغازية",
            "جاذب للزبائن - زوار المحل لديهم في تزايد مذهل!",
            "أونلاين - يتنافس بقوة على صدارة لوحة الشرف",
            "يقوم بتحديث المتجر وشراء رفوف جديدة"
        )
        val luckyStatus = if (levelUpChance) "🔝 ترقى إلى المستوى ${nextLevel}! مبروك!" else statuses.random()

        currentRivals[index] = rival.copy(
            cash = nextCash,
            level = nextLevel,
            statusArabic = luckyStatus
        )

        _rivals.value = currentRivals.sortedByDescending { it.cash + (it.level * 200) }

        // Trigger Random Rival Chat Comment to add dynamic feel
        if (Random.nextFloat() < 0.45f) {
            triggerRivalChat(rival)
        }
    }

    // --- 3. Chat Room Integration ---
    private fun initChat() {
        _chatMessages.value = listOf(
            ChatMessage(1L, "الدعم المالي للعبة", "🤖", "مرحباً بكم في سوبرماركت تشامبز! اللعبة مجانية بالكامل. تتوفر ميزة VIP مدفوعة لزيادة الأرباح خياليًا!", false, "12:00"),
            ChatMessage(2L, "سوبر ماركت النجمة الذهبية", "🏰", "أهلاً بالجميع في السيرفر الحي! بالتوفيق في المتجر الجديد.", true, "12:05"),
            ChatMessage(3L, "بقالة السعادة ورضا", "🏪", "يا شباب، المخبوزات والخبز البلدي يحقق مبيعات سريعة جداً أنصحكم بملء أرففكم!", false, "12:07")
        )
    }

    private fun triggerRivalChat(rival: MultiplayerRival) {
        val messages = if (rival.isVip) {
            listOf(
                "الكافيار وزعفران VIP عليه إقبال كبير وأرباحه مضاعفة جداً! ميزة الـ VIP تستحق الشراء.",
                "مساعد التعبئة التلقائي VIP يوفر الوقت جداً، يسعدني تفعيله!",
                "المنافسة قوية في المتصدرين، ميزة الـ VIP تعطيني تفوق حاسم.",
                "حققت أرباح خيالية اليوم بفضل كبار الشخصيات VIP ⭐!"
            )
        } else {
            listOf(
                "هل من مزاد جديد؟ أحتاج بضائع رخيصة لتعبئة مخازني.",
                "أسعاري تزيد بنسبة 2.0x وتجلب زبائن مستقرين.",
                "آمل أن أجمع قريباً تكلفة تفعيل VIP للحصول على الكافيار ومساعد التعبئة الرائع.",
                "أهلاً بكم يا رفقاء التجارة العادلة!",
                "لقد خسرت المزاد الأخير بـ 5$ فقط! المرة القادمة سأزايد أسرع."
            )
        }

        val chat = ChatMessage(
            id = System.currentTimeMillis(),
            senderName = rival.name,
            senderEmoji = rival.emoji,
            messageArabic = messages.random(),
            isVip = rival.isVip,
            timeString = "الآن"
        )

        val updated = _chatMessages.value.toMutableList()
        updated.add(chat)
        if (updated.size > 20) updated.removeAt(0)
        _chatMessages.value = updated
    }

    fun sendPlayerChat(messageText: String) {
        if (messageText.isBlank()) return
        val player = playerState.value ?: return

        val msg = ChatMessage(
            id = System.currentTimeMillis(),
            senderName = player.name + " (أنت)",
            senderEmoji = if (player.isVip) "👑" else "💼",
            messageArabic = messageText,
            isVip = player.isVip,
            timeString = "الآن"
        )

        val updated = _chatMessages.value.toMutableList()
        updated.add(msg)
        if (updated.size > 20) updated.removeAt(0)
        _chatMessages.value = updated

        // Simulated reply by other players
        viewModelScope.launch {
            delay(1500)
            val rival = _rivals.value.randomOrNull() ?: return@launch
            val responseText = listOf(
                "يا بطل! واصل التقدم!",
                "مرحباً بك في تواصل اللاعبين المباشر.",
                "دكان مذهل ومحيط أسعار متوازن للغابة.",
                "هل ستحضر مزاد الجملة القادم؟",
                "نعم! تشرفنا بحديثك يا رفيقي."
            ).random()

            val responseMsg = ChatMessage(
                id = System.currentTimeMillis(),
                senderName = rival.name,
                senderEmoji = rival.emoji,
                messageArabic = "@${player.name} $responseText",
                isVip = rival.isVip,
                timeString = "الآن"
            )
            val temp = _chatMessages.value.toMutableList()
            temp.add(responseMsg)
            if (temp.size > 20) temp.removeAt(0)
            _chatMessages.value = temp
        }
    }

    private fun addSystemChatMessage(sender: String, text: String, isVip: Boolean = false) {
        val msg = ChatMessage(
            id = System.currentTimeMillis(),
            senderName = sender,
            senderEmoji = "📣",
            messageArabic = text,
            isVip = isVip,
            timeString = "الآن"
        )
        val updated = _chatMessages.value.toMutableList()
        updated.add(msg)
        if (updated.size > 20) updated.removeAt(0)
        _chatMessages.value = updated
    }

    // --- 4. Wholesale Auctions House (Simulated Multiplayer Bidding) ---
    fun generateNewAuction() {
        val items = listOf(
            Triple("milk", "صندوق حليب بلدي x25", "🥛"),
            Triple("bread", "سلة خبز ساخن x30", "🍞"),
            Triple("cheese", "كرتونة جبن معتق x20", "🧀"),
            Triple("cola", "صندوق مشروبات باردة x40", "🥤")
        )
        val chosen = items.random()
        val baseCost = 25.0 // Market cost for a bundle is cheap!

        _activeAuction.value = AuctionItem(
            id = chosen.first,
            nameArabic = chosen.second,
            emoji = chosen.third,
            quantity = Random.nextInt(15, 31),
            normalCost = baseCost,
            currentHighBid = baseCost - 5.0, // Initial bid
            highestBidderName = "سوق الجملة المركزي",
            highestBidderIsPlayer = false,
            secondsRemaining = 25, // Quick bids!
            isActive = true,
            comment = "المزاد بدأ للتو على [${chosen.second}]! من سيفتتح المزايدة يا تجار؟ 😉",
            commenterEmoji = "📣"
        )
    }

    private suspend fun tickAuction() {
        val current = _activeAuction.value ?: return
        if (!current.isActive) return

        if (current.secondsRemaining <= 1) {
            // Auction Finished! Resolve
            _activeAuction.value = current.copy(secondsRemaining = 0, isActive = false)
            resolveAuction(current)
            return
        }

        // Rival bid chance (45% chance if seconds are counting down)
        var updatedAuction = current.copy(secondsRemaining = current.secondsRemaining - 1)

        val bidChance = Random.nextFloat() < 0.45f
        if (bidChance && !current.highestBidderIsPlayer && current.secondsRemaining > 3) {
            val rival = _rivals.value.randomOrNull()
            if (rival != null) {
                val nextBid = current.currentHighBid + Random.nextDouble(1.0, 5.0)
                
                // Realistic Arabic chatter from rivals during auctions
                val rivalComments = listOf(
                    "الصفقة دي بتاعتي محدش يزايد! 😎",
                    "دي بضاعة ممتازة وأنا أولى بيها! 😉",
                    "زود السعر يا معلم، المزاد لسه هيبدأ 🔥",
                    "الزبائن عندي بيموتوا في الحاجات دي، لازم أكسبها!",
                    "معلش يا جماعة، أنا محتاج البضاعة دي ضروري جداً! 🙌",
                    "دكاني محتاج البضاعة دي لملء الرفوف العريضة!"
                )
                val chosenComment = rivalComments.random()

                updatedAuction = updatedAuction.copy(
                    currentHighBid = nextBid,
                    highestBidderName = rival.name,
                    highestBidderIsPlayer = false,
                    comment = chosenComment,
                    commenterEmoji = rival.emoji
                )
                addNotification("⚡ ${rival.name} زايدَ بـ ${String.format("%.1f", nextBid)}$")
                
                // Post live to chat room
                addSystemChatMessage(rival.name, "🚨 [في المزاد]: $chosenComment سأدفع ${String.format("%.1f", nextBid)}$!", rival.isVip)
            }
        } else if (bidChance && current.highestBidderIsPlayer && current.secondsRemaining > 1) {
            // If player is winning, rivals try to steal at the last few seconds!
            val rival = _rivals.value.randomOrNull()
            if (rival != null && current.currentHighBid < 60.0) { // Safety ceiling for rivals
                val nextBid = current.currentHighBid + Random.nextDouble(2.0, 6.0)
                
                val theftComments = listOf(
                    "يا غالي معلش، المزاد في آخر الثواني حماس! 😅🏃‍♂️",
                    "بخطف الصدارة في ثواني! عرضي هو ${String.format("%.1f", nextBid)}$! 😉",
                    "محاولة إنقاذ المزاد في آخر ثانية! المزايدة حامية 🔥",
                    "لا لا المزاد ده مستحيل يفوتني بالسهولة دي! 😎",
                    "عذراً يا صديقي، اللعب على الكبير في المزاد! 💪"
                )
                val chosenComment = theftComments.random()

                updatedAuction = updatedAuction.copy(
                    currentHighBid = nextBid,
                    highestBidderName = rival.name,
                    highestBidderIsPlayer = false,
                    comment = chosenComment,
                    commenterEmoji = rival.emoji
                )
                addNotification("🔥 منافسة شديدة! ${rival.name} خطف الصدارة بـ ${String.format("%.1f", nextBid)}$!")
                addSystemChatMessage(rival.name, "⏰ [ثواني أخيرة]: $chosenComment لعيون الشحنة!", rival.isVip)
            }
        }

        _activeAuction.value = updatedAuction
    }

    fun makePlayerBid() {
        val current = _activeAuction.value ?: return
        val player = playerState.value ?: return
        if (!current.isActive) return

        val increment = 5.0
        val myBid = current.currentHighBid + increment

        if (player.cash >= myBid) {
            val playerRivalReactions = listOf(
                "أوووه! دخلت بثقلك المالي! 😱",
                "المنافسة ولعت، يا ترى من الذكي الذي سيكمل؟ 🤔",
                "والله هه، سأزايد عليك في اللحظة القادمة! 😉",
                "سعر ممتاز يا غالي، لكن المزاد لسه طويل!",
                "مبروك عليك مقدماً لو لم يزايد أحد.. بس أنا سأفكر! 😉"
            )
            val selectedReaction = playerRivalReactions.random()

            _activeAuction.value = current.copy(
                currentHighBid = myBid,
                highestBidderName = player.name,
                highestBidderIsPlayer = true,
                secondsRemaining = (current.secondsRemaining + 2).coerceAtMost(25), // Add a tiny extension like sniper rules!
                comment = "سأعرض ${String.format("%.1f", myBid)}$، البضائع دي من نصيبي! 💪😎",
                commenterEmoji = "💼"
            )
            addNotification("💰 قمت بالمزايدة! عرضك الحالي: ${String.format("%.1f", myBid)}$")

            // Real voice chatter animation trigger to chat room
            viewModelScope.launch {
                delay(300)
                addSystemChatMessage("🗣️ ${player.name}", "📢 [مزايدة حية]: رفعت السعر لـ ${String.format("%.1f", myBid)}$! أنا بحاجة لشحنة [${current.nameArabic}]!")
                delay(1200)
                val responder = _rivals.value.randomOrNull() ?: return@launch
                addSystemChatMessage(responder.name, "💬 @${player.name} $selectedReaction", responder.isVip)
            }
        } else {
            addNotification("⚠️ ليس لديك أموال كافية للمزايدة بـ ${String.format("%.1f", myBid)}$!")
        }
    }

    private suspend fun resolveAuction(item: AuctionItem) {
        val player = playerState.value ?: return

        if (item.highestBidderIsPlayer) {
            // Player won! Deduct cash and load stock
            val inventoryItem = inventory.value.find { it.itemId == item.id }
            if (inventoryItem != null) {
                // Deduct winning bid and add stock in room db
                repository.stockItem(item.id, item.quantity, 0.0) // Item quantity added with 0 cost since already paid in winning bid
                repository.deductAuctionPayment(item.currentHighBid, 40) // Persists negative payment & gives 40xp

                addNotification("🎉 مبروك! فزت بـ ${item.nameArabic} (${item.quantity} قطعة) مقابل ${String.format("%.1f", item.currentHighBid)}$!")
                addSystemChatMessage("🏆 المزاد المركزي", "تهانينا للمسؤول @${player.name} على الفوز بـ [${item.nameArabic}] ودعم متجره بقوة!")
                
                // Congratulatory remarks from rivals
                viewModelScope.launch {
                    delay(1000)
                    val r1 = _rivals.value.randomOrNull() ?: return@launch
                    val r1Comments = listOf(
                        "ألف مبروك يا غالي صفقة ممتازة! دكانك هينوّر بالبضائع دي 👍",
                        "تستاهل يا بطل! بس المرة الجاية المزاد بتاعي 😉",
                        "مبروك الفوز، السعر كان ناري الصراحة 🔥",
                        "بضاعة رابحة جداً، ألف مبروك عليك الشحنة يا صديقي."
                    ).random()
                    addSystemChatMessage(r1.name, "@${player.name} $r1Comments", r1.isVip)
                }
            }
        } else {
            // Rival won
            addNotification("🏆 فاز ${item.highestBidderName} بالمزاد بقيمة ${String.format("%.1f", item.currentHighBid)}$")
            val victoryComments = listOf(
                "يسسس! فزت بـ [${item.nameArabic}] بسعر رخيص جداً! حظاً أوفر في المزايدة القادمة يا أصدقاء.",
                "الحمد لله، الصفقة تمت ومحلي سيمتلئ بالكامل! شكراً ع المزايدة الحلوة 🤩",
                "أحلى صفقة لليوم! البضاعة هتوصل الرفوف على طول 🚚💨",
                "لقد كانت معركة شرسة في الثواني الأخيرة! حظاً سعيداً المرة القادمة للجميع."
            ).random()
            addSystemChatMessage(item.highestBidderName, "🏆 [المزاد]: $victoryComments", _rivals.value.find { it.name == item.highestBidderName }?.isVip ?: false)
        }

        // Prepare next auction in 12s
        viewModelScope.launch {
            delay(12000)
            generateNewAuction()
        }
    }

    // --- 5. Player Stock Operations ---
    fun restockProduct(itemId: String, qty: Int) {
        viewModelScope.launch {
            val item = inventory.value.find { it.itemId == itemId } ?: return@launch
            val player = playerState.value ?: return@launch
            val cost = item.costPrice * qty
            if (player.cash >= cost) {
                if (item.currentStock + qty <= item.maxStock) {
                    val success = repository.stockItem(itemId, qty, item.costPrice)
                    if (success) {
                        addNotification("📦 تم ملء الرفوف بـ $qty وحدات من ${item.nameArabic}!")
                    } else {
                        addNotification("❌ فشل ترتيب الرفوف!")
                    }
                } else {
                    addNotification("⚠️ المتجر ممتلئ! الرفوف لا تتحمل هذه الكمية.")
                }
            } else {
                addNotification("⚠️ رصيدك غير كافي! تكلفة الشراء: ${String.format("%.1f", cost)}$")
            }
        }
    }

    fun updatePrice(itemId: String, price: Double) {
        viewModelScope.launch {
            repository.updateSellingPrice(itemId, price)
            val name = inventory.value.find { it.itemId == itemId }?.nameArabic ?: ""
            addNotification("🏷️ تم تحديث سعر $name إلى ${String.format("%.1f", price)}$")
        }
    }

    fun changeStoreName(newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                repository.updatePlayerName(newName)
                addNotification("✏️ تم تغيير اسم السوبر ماركت إلى: $newName")
            }
        }
    }

    // --- 6. Simulated Play VIP Payments Support ---
    fun openVipCheckout() {
        _showVipPaymentSheet.value = true
    }

    fun closeVipCheckout() {
        _showVipPaymentSheet.value = false
    }

    fun purchaseVipConfirm() {
        viewModelScope.launch {
            _isRegisteringMuted.value = true
            delay(1800) // Realistic secure card processing delay
            val success = repository.buyVipUpgrade()
            _isRegisteringMuted.value = false
            if (success) {
                _showVipPaymentSheet.value = false
                _showVipSuccessDialog.value = true
                addNotification("👑 مبروك الترقية الأسطورية! تم تفعيل ميزات VIP بنجاح.")
                val player = playerState.value
                addSystemChatMessage("⭐ كبار الشخصيات", "🔥 رحبوا معنا بالعضو الذهبي الجديد [${player?.name ?: "تاجر المستقبل"}] في النادي الأسطوري VIP!", isVip = true)
            }
        }
    }

    fun dismissVipSuccess() {
        _showVipSuccessDialog.value = false
    }

    fun toggleAutoRestock() {
        viewModelScope.launch {
            val res = repository.toggleAutoRestock()
            if (res) {
                val player = playerState.value
                val status = if (player?.isAutoRestock == true) "مفعّل" else "ملغى"
                addNotification("⚙️ مساعد التعبئة التلقائي للرفوف: $status")
            }
        }
    }

    // --- Notifications helper ---
    private fun addNotification(text: String) {
        val current = _recentNotifications.value.toMutableList()
        current.add(0, text)
        if (current.size > 5) current.removeAt(5)
        _recentNotifications.value = current
    }

    override fun onCleared() {
        super.onCleared()
        customerSimulationJob?.cancel()
        rivalsSimulationJob?.cancel()
        auctionSimulationJob?.cancel()
    }
}
