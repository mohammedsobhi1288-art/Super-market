package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.InventoryItem
import com.example.data.PlayerState
import com.example.models.AuctionItem
import com.example.models.ChatMessage
import com.example.models.MultiplayerRival
import com.example.ui.theme.*
import com.example.viewmodel.GameViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val player by viewModel.playerState.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val rivals by viewModel.rivals.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val activeAuction by viewModel.activeAuction.collectAsState()
    val notifications by viewModel.recentNotifications.collectAsState()

    val showVipCheckout by viewModel.showVipPaymentSheet.collectAsState()
    val showVipSuccess by viewModel.showVipSuccessDialog.collectAsState()
    val isRegisteringVip by viewModel.isRegisteringMuted.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Shelves, 1: Co-op Auctions & Chat, 2: Rivals Leaderboard
    var showEditNameDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Interactive notification ticker animation
    val currentNotification = notifications.firstOrNull() ?: "🏪 أهلاً بك! محلك مفتوح الآن وجاهز لاستقبال الزبائن."

    // 1. Theme Configuration based on VIP level
    val isPlayerVip = player?.isVip ?: false
    val backgroundBrush = if (isPlayerVip) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E1428),
                Color(0xFF120E1C),
                Color(0xFF0F0B18)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF6F1F8),
                Color(0xFFEDE7F0),
                Color(0xFFE7E0EB)
            )
        )
    }

    val contentColor = if (isPlayerVip) Color.White else Color(0xFF1C1B1F)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            // Notifications / Live Sales Ticker Bar
            Surface(
                tonalElevation = 6.dp,
                color = if (isPlayerVip) VipDarkSurface else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Notification Ticker",
                        tint = if (isPlayerVip) VipGold else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    AnimatedContent(
                        targetState = currentNotification,
                        transitionSpec = {
                            slideInVertically { height -> height } + fadeIn() with
                                    slideOutVertically { height -> -height } + fadeOut()
                        },
                        modifier = Modifier.weight(1f)
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isPlayerVip) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            textAlign = TextAlign.Right,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                state = rememberLazyListState()
            ) {
                // Header Space
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // 2. Playable Stats Dashboard Section
                item {
                    player?.let { p ->
                        DashboardCard(
                            player = p,
                            onEditNameClick = { showEditNameDialog = true },
                            onUpgradeVipClick = { viewModel.openVipCheckout() }
                        )
                    }
                }

                // 3. Game Tab Navigation
                item {
                    TabNavigationRow(
                        selectedTab = activeTab,
                        onTabSelected = { activeTab = it },
                        isVip = isPlayerVip
                    )
                }

                if (!isPlayerVip) {
                    item {
                        VipPromoCard(onUpgradeClick = { viewModel.openVipCheckout() })
                    }
                }

                // 4. Tab Contents
                when (activeTab) {
                    0 -> {
                        // SHELVES & CUSTOM STOCKING
                        item {
                            SectionHeader(
                                title = "الرفوف والأسعار الحرة 🥛",
                                subtitle = "تحكم بأسعار البيع، واملأ البضائع قبل نفاذها لتجنب خسارة الزبائن."
                            )
                        }

                        // VIP Privilege quick toggle
                        if (isPlayerVip) {
                            item {
                                AutoRestockToggleCard(
                                    isAutoRestockEnabled = player?.isAutoRestock ?: false,
                                    onToggle = { viewModel.toggleAutoRestock() }
                                )
                            }
                        }

                        // Lists Inventory items
                        val itemsList = inventory
                        if (itemsList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = if (isPlayerVip) VipGold else MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else {
                            items(itemsList) { item ->
                                ProductRowCard(
                                    item = item,
                                    playerCash = player?.cash ?: 0.0,
                                    isVip = isPlayerVip,
                                    onRestock = { count -> viewModel.restockProduct(item.itemId, count) },
                                    onPriceChange = { newPrice -> viewModel.updatePrice(item.itemId, newPrice) },
                                    onVipLockedClick = { viewModel.openVipCheckout() }
                                )
                            }
                        }
                    }

                    1 -> {
                        // CO-OP AUCTION ROOM & MULTIPLAYER CHAT
                        item {
                            SectionHeader(
                                title = "مزاد الجملة المباشر ⚡",
                                subtitle = "نافس التجار الآخرين في المزايدة للفوز بصناديق البضائع بنصف سعرها!"
                            )
                        }

                        activeAuction?.let { auction ->
                            item {
                                AuctionBiddingCard(
                                    auction = auction,
                                    playerCash = player?.cash ?: 0.0,
                                    isVip = isPlayerVip,
                                    onBid = { viewModel.makePlayerBid() },
                                    onTriggerNew = { viewModel.generateNewAuction() }
                                )
                            }
                        }

                        item {
                            SectionHeader(
                                title = "مجموعة دردشة السيرفر المباشرة 💬",
                                subtitle = "محادثات تفاعلية حية بينك وبين التجار لتبادل النصائح والمباربكات."
                            )
                        }

                        item {
                            ServerChatWidget(
                                messages = chatMessages,
                                onSendMessage = { text -> viewModel.sendPlayerChat(text) },
                                isVip = isPlayerVip
                            )
                        }
                    }

                    2 -> {
                        // LIVE MULTIPLAYER LEADERBOARD & RIVALS LIST
                        item {
                            SectionHeader(
                                title = "لوحة شرف التجار المنافسين 🏆",
                                subtitle = "تحديث فوري لترتيب السوبرماركتس المحيطة بك بناءً على رأس المال والمبيعات المستمرة."
                            )
                        }

                        items(rivals) { rival ->
                            RivalLeaderboardCard(rival = rival, isVip = isPlayerVip)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            // --- Floating Gold Sparkles if VIP is Active ---
            if (isPlayerVip) {
                VipBackgroundSparkles()
            }
        }
    }

    // --- MODALS & DIALOGS ---

    // A. Edit Studio/Store Name Dialog
    if (showEditNameDialog) {
        var tempName by remember { mutableStateOf(player?.name ?: "") }
        Dialog(onDismissRequest = { showEditNameDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isPlayerVip) VipDarkSurface else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "تعديل اسم المتجر ✏️",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isPlayerVip) VipGold else MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_name_input"),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                        label = { Text("اسم السوبر ماركت", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isPlayerVip) VipGold else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isPlayerVip) Color.Gray else MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel.changeStoreName(tempName)
                                showEditNameDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlayerVip) VipGoldMedium else MaterialTheme.colorScheme.primary,
                                contentColor = if (isPlayerVip) Color.Black else Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_store_name_button")
                        ) {
                            Text("حفظ", fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { showEditNameDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء", color = if (isPlayerVip) Color.LightGray else Color.Gray)
                        }
                    }
                }
            }
        }
    }

    // B. Real simulation check checkout Payment Sheet for Gold VIP Upgrade
    if (showVipCheckout) {
        VipCheckoutSheet(
            isRegistering = isRegisteringVip,
            onClose = { viewModel.closeVipCheckout() },
            onConfirmPurchase = { viewModel.purchaseVipConfirm() }
        )
    }

    // C. VIP Upgrade Splash/Success Dialog (Celebrative Confetti flow)
    if (showVipSuccess) {
        VipUpgradeSuccessSplash(
            onDismiss = { viewModel.dismissVipSuccess() }
        )
    }
}

@Composable
fun VipGoldMediumThemeColor(): Color {
    return Color(0xFFDAA520)
}

// --- Dynamic Sparkles for VIP ---
@Composable
fun VipBackgroundSparkles() {
    val infiniteTransition = rememberInfiniteTransition()
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        // Draw 8 tiny gold stars as visual accent
        val randomStars = listOf(
            Offset(width * 0.15f, height * 0.25f),
            Offset(width * 0.85f, height * 0.15f),
            Offset(width * 0.50f, height * 0.08f),
            Offset(width * 0.08f, height * 0.70f),
            Offset(width * 0.90f, height * 0.65f),
            Offset(width * 0.75f, height * 0.40f)
        )
        for (star in randomStars) {
            drawCircle(
                color = VipGold.copy(alpha = sparkleAlpha),
                radius = 5.dp.toPx(),
                center = star
            )
        }
    }
}

// --- Custom Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCard(
    player: PlayerState,
    onEditNameClick: () -> Unit,
    onUpgradeVipClick: () -> Unit
) {
    val isVip = player.isVip
    val cardBackground = if (isVip) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF2C2513),
                Color(0xFF1E190D),
                Color(0xFF2D2513)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF6750A4),
                Color(0xFF4F378B)
            )
        )
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isVip) 16.dp else 4.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = if (isVip) VipGold else Color.Black,
                spotColor = if (isVip) VipGold else Color.Black
            )
            .border(
                width = if (isVip) 2.dp else 0.dp,
                brush = Brush.linearGradient(listOf(VipGold, Color(0xFFFFA000))),
                shape = RoundedCornerShape(24.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(cardBackground)
                .padding(20.dp)
        ) {
            Column {
                // Top row: Store Name & VIP Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isVip) {
                        Surface(
                            shape = CircleShape,
                            color = VipGold,
                            modifier = Modifier.shadow(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "مالك ذهبي VIP",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = "VIP Icon",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = onUpgradeVipClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VipGold,
                                contentColor = Color.Black
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                            shape = CircleShape,
                            modifier = Modifier
                                .testTag("vip_purchase_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تفعيل كبار الشخصيات VIP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Star, contentDescription = "VIP Unlock", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onEditNameClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Name",
                            tint = if (isVip) VipGold else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = player.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rating
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("تقييم الزبائن ⭐", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(
                            text = String.format("%.2f", player.rating),
                            color = if (player.rating >= 4.0f) MarketGreenLight else ItemRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    // Level Indicator
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("مستوى المتجر", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Surface(
                            shape = CircleShape,
                            color = if (isVip) VipGold else Color.White,
                            modifier = Modifier.size(40.dp),
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = player.level.toString(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }

                    // Store Cash (Arabic currency model)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("رأس مال المتجر 💵", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(
                            text = "${String.format("%,.1f", player.cash)}$",
                            color = if (isVip) VipGold else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // XP Progress Bar
                val xpNeeded = player.level * 150
                val progress = (player.xp.toFloat() / xpNeeded.toFloat()).coerceIn(0f, 1f)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${player.xp}/$xpNeeded XP",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "الترقية للمستوى التالي",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (isVip) VipGold else Color(0xFF4CAF50),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun TabNavigationRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isVip: Boolean
) {
    val tabs = listOf(
        "الرفوف والأسعار" to Icons.Default.Layers,
        "المزادات والدردشة" to Icons.Default.Forum,
        "المنافسون" to Icons.Default.Leaderboard
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isVip) VipDarkSurface else Color(0xFFF3EDF7))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { index, (label, icon) ->
            val active = selectedTab == index
            val tabBg = when {
                active && isVip -> Brush.horizontalGradient(listOf(VipGold, Color(0xFFFFA000)))
                active -> Brush.horizontalGradient(
                    listOf(
                        Color(0xFF6750A4),
                        Color(0xFF4F378B)
                    )
                )
                else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tabBg)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        color = if (active) (if (isVip) Color.Black else Color.White) else (if (isVip) Color.LightGray else Color.Gray),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) (if (isVip) Color.Black else Color.White) else (if (isVip) Color.LightGray else Color.Gray),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = if (MaterialTheme.colorScheme.background == Color.Black) Color.White else MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Right
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF49454F)
            ),
            textAlign = TextAlign.Right
        )
    }
}

@Composable
fun AutoRestockToggleCard(
    isAutoRestockEnabled: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2513)),
        border = BorderStroke(1.dp, VipGold.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Switch(
                checked = isAutoRestockEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VipGold,
                    checkedTrackColor = VipGold.copy(alpha = 0.5f)
                )
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "مساعد التعبئة التلقائي 🛡️ (ميزة VIP)",
                    fontWeight = FontWeight.Bold,
                    color = VipGold,
                    fontSize = 13.sp
                )
                Text(
                    "يقوم بشراء البضائع تلقائياً عندما توشك على الانتهاء من الرفوف.",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Right
                )
            }
        }
    }
}

@Composable
fun ProductRowCard(
    item: InventoryItem,
    playerCash: Double,
    isVip: Boolean,
    onRestock: (Int) -> Unit,
    onPriceChange: (Double) -> Unit,
    onVipLockedClick: () -> Unit
) {
    val isLocked = item.isVipOnly && !isVip

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isVip) VipDarkSurface else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        border = if (item.isVipOnly) BorderStroke(1.dp, VipGold) else null
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                // Top header: Product Emoji + Arabic name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isVipOnly) {
                        Surface(
                            color = VipGold,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "خاص VIP",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = item.nameArabic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isVip) Color.White else Color.Black
                            )
                            Text(
                                text = item.itemType,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isVip) VipDarkBack else Color(0xFFF0F4F8),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(item.iconEmoji, fontSize = 22.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mid state: Stock level meter
                val stockRatio = item.currentStock.toFloat() / item.maxStock.toFloat()
                val progressBarColor = when {
                    stockRatio <= 0.2f -> ItemRed
                    stockRatio <= 0.5f -> ItemOrange
                    else -> MarketGreen
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "السعة: ${item.currentStock} / ${item.maxStock}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isVip) Color.LightGray else Color.Black
                    )
                    Text(
                        "حالة المخزون بالمتجر",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = stockRatio,
                    color = progressBarColor,
                    trackColor = if (isVip) Color.White.copy(alpha = 0.1f) else Color(0xFFEEEEEE),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom State: Controls for pricing and Restocking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Restock buttons
                    Button(
                        onClick = { onRestock(6) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVip) VipGold else MaterialTheme.colorScheme.primary,
                            contentColor = if (isVip) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f),
                        enabled = !isLocked && playerCash >= (item.costPrice * 6) && (item.currentStock + 6 <= item.maxStock)
                    ) {
                        Text(
                            "ملء +6 (${String.format("%.1f", item.costPrice * 6)}$)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Price Setter free slider or step controls
                    Column(
                        modifier = Modifier.weight(1.5f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("سعر البيع للزبائن", fontSize = 10.sp, color = Color.Gray)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(
                                onClick = { onPriceChange((item.sellingPrice + 0.5).coerceAtMost(item.costPrice * 5)) },
                                enabled = !isLocked,
                                colors = ButtonColors(
                                    containerColor = if (isVip) Color.DarkGray else Color(0xFFF0F2F5),
                                    contentColor = if (isVip) VipGold else MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = Color.Transparent,
                                    disabledContentColor = Color.LightGray
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold)
                            }

                            Text(
                                "${String.format("%.1f", item.sellingPrice)}$",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isVip) VipGold else MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )

                            TextButton(
                                onClick = { onPriceChange((item.sellingPrice - 0.5).coerceAtLeast(0.5)) },
                                enabled = !isLocked && item.sellingPrice > 0.5,
                                colors = ButtonColors(
                                    containerColor = if (isVip) Color.DarkGray else Color(0xFFF0F2F5),
                                    contentColor = if (isVip) VipGold else MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = Color.Transparent,
                                    disabledContentColor = Color.LightGray
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Lock Overlay if VIP Only product item
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable { onVipLockedClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked VIP",
                            tint = VipGold,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "مفتوح لكبار الشخصيات VIP فقط 👑",
                            color = VipGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "اضغط هنا لفتح الاشتراك والحصول على المنتجات الفاخرة!",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuctionBiddingCard(
    auction: AuctionItem,
    playerCash: Double,
    isVip: Boolean,
    onBid: () -> Unit,
    onTriggerNew: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (isVip) VipDarkSurface else Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp)),
        border = BorderStroke(2.dp, if (auction.isActive) Color(0xFFFF9100) else Color.Gray)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (auction.isActive) {
                    Surface(
                        color = Color(0xFFFFE0B2),
                        shape = CircleShape
                    ) {
                        Text(
                            "مزايدة حية ⚡",
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        color = Color.LightGray,
                        shape = CircleShape
                    ) {
                        Text(
                            "انتهى المزاد",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "دار المزايدة العالمية",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isVip) Color.White else Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Gavel, contentDescription = "Gavel Auction", tint = Color(0xFFE65100))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary action: Request next auction
                if (!auction.isActive) {
                    Button(
                        onClick = onTriggerNew,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("طلب مزاد فوري جديد")
                    }
                } else {
                    // Timer remaining
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("الوقت المتبقي", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            "${auction.secondsRemaining} ثانية",
                            color = if (auction.secondsRemaining <= 5) ItemRed else ItemOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                }

                // Item description
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            auction.nameArabic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isVip) Color.White else Color.Black
                        )
                        Text("الكمية بالجملة: ${auction.quantity} وحدة", fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (isVip) Color.Black else Color(0xFFFFF3E0),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(auction.emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // High Bid Info Box
            Surface(
                color = if (auction.highestBidderIsPlayer) MarketGreenLight.copy(alpha = 0.4f) else (if (isVip) VipDarkBack else Color(0xFFF5F5F5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        if (auction.highestBidderIsPlayer) MarketGreen else Color.Transparent,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("صاحب المزايدة الكبرى", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = if (auction.highestBidderIsPlayer) "أنت (صاحب العرض الأقوى)" else auction.highestBidderName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (auction.highestBidderIsPlayer) MarketGreenDark else (if (isVip) Color.White else Color.Black)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("أعلى سعر معروض حالياً", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            "${String.format("%.1f", auction.currentHighBid)}$",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = if (auction.highestBidderIsPlayer) MarketGreen else Color(0xFFE65100)
                        )
                    }
                }
            }

            // Live Verbal Comment Bubble inside the Auction Card itself
            auction.comment?.let { comment ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp))
                        .background(
                            if (auction.highestBidderIsPlayer) MarketGreenLight.copy(alpha = 0.25f)
                            else if (isVip) Color(0xFF2A281E)
                            else Color(0xFFFFF3E0)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = auction.commenterEmoji ?: "💬",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = if (auction.highestBidderIsPlayer) "أنت" else auction.highestBidderName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVip) VipGold else Color(0xFFE65100)
                        )
                        Text(
                            text = comment,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isVip) Color.White else Color.Black
                        )
                    }
                }
            }

            if (auction.isActive) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onBid,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (auction.highestBidderIsPlayer) Color.Gray else (if (isVip) VipGold else Color(0xFFFF9100)),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("bid_auction_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add Bid")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "زايد بـ +5$ عاجلاً!",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServerChatWidget(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    isVip: Boolean
) {
    var chatText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isVip) VipDarkSurface else Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Live Message board
            val scrollState = rememberLazyListState()
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    scrollState.animateScrollToItem(messages.size - 1)
                }
            }

            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg.timeString,
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (msg.isVip) {
                                    Surface(
                                        color = VipGold,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "VIP",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = msg.senderName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (msg.isVip) VipGold else (if (isVip) Color.White else Color.Black)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                                color = if (msg.isVip) Color(0xFF332A15) else (if (isVip) Color.Black else Color(0xFFF0F4F8)),
                                modifier = Modifier.widthIn(max = 240.dp)
                            ) {
                                Text(
                                    text = msg.messageArabic,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (msg.isVip) VipGold else (if (isVip) Color.White else Color.Black)
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    textAlign = TextAlign.Right
                                )
                            }
                        }

                        // Sender Avatar / Emoji icon
                        Surface(
                            shape = CircleShape,
                            color = if (msg.isVip) Color(0xFF332A15) else (if (isVip) VipDarkBack else Color(0xFFE8ECEF)),
                            modifier = Modifier.size(32.dp),
                            border = if (msg.isVip) BorderStroke(1.dp, VipGold) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(msg.senderEmoji, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // Input bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (chatText.isNotBlank()) {
                            onSendMessage(chatText)
                            chatText = ""
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier
                        .background(if (isVip) VipGold else MaterialTheme.colorScheme.primary, CircleShape)
                        .size(38.dp)
                        .testTag("send_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Message",
                        tint = if (isVip) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextField(
                    value = chatText,
                    onValueChange = { chatText = it },
                    placeholder = { Text("أرسل رسالة للجروب العام للمنافسين...", fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("chat_input"),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right, fontSize = 12.sp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(onSend = {
                        if (chatText.isNotBlank()) {
                            onSendMessage(chatText)
                            chatText = ""
                            keyboardController?.hide()
                        }
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isVip) VipDarkBack else Color(0xFFF1F3F5),
                        unfocusedContainerColor = if (isVip) VipDarkBack else Color(0xFFF1F3F5),
                        focusedTextColor = if (isVip) Color.White else Color.Black,
                        unfocusedTextColor = if (isVip) Color.White else Color.Black,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
fun RivalLeaderboardCard(rival: MultiplayerRival, isVip: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isVip) VipDarkSurface else Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        border = if (rival.isVip) BorderStroke(1.dp, VipGold) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left state: Total Worth
            Column(horizontalAlignment = Alignment.Start) {
                Text("رأس المال + الاستثمارات", fontSize = 11.sp, color = Color.Gray)
                Text(
                    "${String.format("%,.0f", rival.cash)}$",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (rival.isVip) VipGold else (if (isVip) Color.White else Color.Black)
                )
            }

            // Right state: Profile
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (rival.isVip) {
                            Surface(
                                color = VipGold,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "VIP",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = rival.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (rival.isVip) VipGold else (if (isVip) Color.White else Color.Black)
                        )
                    }
                    Text(text = rival.statusArabic, fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Right)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = CircleShape,
                    color = if (rival.isVip) Color(0xFF332A15) else (if (isVip) VipDarkBack else Color(0xFFF0F4F8)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(rival.emoji, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

// --- FANCY VIP PAYMENT SHEET ---
@Composable
fun VipCheckoutSheet(
    isRegistering: Boolean,
    onClose: () -> Unit,
    onConfirmPurchase: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = VipDarkSurface),
            border = BorderStroke(2.dp, VipGold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .shadow(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Gold Icon Header
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF332A15),
                    modifier = Modifier
                        .size(72.dp)
                        .border(2.dp, VipGold, CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = "VIP Premium",
                            tint = VipGold,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ترقية العضوية الأسطورية VIP ⭐",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = VipGold
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "افتح المتجر الذهبي وتجاوز الجميع في السيرفر!",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                    textAlign = TextAlign.Center
                )

                Divider(
                    color = Color.Gray.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // List vip perks
                val perks = listOf(
                    "💵 بونص كاش ترحيبي لمرة واحدة بقيمة +1,000$",
                    "🐟 توفير الكافيار والزعفران (هامش ربح خيالي!)",
                    "⭐ زبائن VIP أثرياء بدفعات مضاعفة ومستقرة",
                    "🛡️ تفعيل المساعد الآلي لملء أرففك تلقائياً مجاناً",
                    "👑 سمة ذهبية براقة للمحل وتواصل ملون بجروب العام"
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    perks.forEach { perk ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = perk,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Right
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Check Perk",
                                tint = VipGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Divider(
                    color = Color.Gray.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Simulated Price box
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "4.99$ شهرياً",
                            fontWeight = FontWeight.Light,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "عرض التجربة المجانية الخيالية",
                                color = VipGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Test Store Billing", tint = VipGold, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isRegistering) {
                    CircularProgressIndicator(color = VipGold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("جاري معالجة بطاقة الدفع الآمنة محاكاة...", color = Color.LightGray, fontSize = 11.sp)
                } else {
                    Button(
                        onClick = onConfirmPurchase,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VipGold,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_vip_payment_btn")
                    ) {
                        Text(
                            "فتح الاشتراك (محاكاة خيالية مجانية الآن)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onClose) {
                        Text("إلغاء المتابعة حالياً", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// --- CELEBRATION VIP MODAL ---
@Composable
fun VipUpgradeSuccessSplash(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val sparklesTransition = rememberInfiniteTransition()
        val bounceOffset by sparklesTransition.animateFloat(
            initialValue = -10f,
            targetValue = 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = VipDarkSurface),
            border = BorderStroke(2.dp, VipGold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .shadow(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .offset(y = bounceOffset.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("👑", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "تهانينا الحارة! 🌟",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = VipGold
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "لقد أصبحت الآن تاجر كبار الشخصيات VIP رسمي في السيرفر! تم إيداع 1,000$ ترحيبية في حسابك، وفتح المنتجات الفاخرة وخاصية مساعد التعبئة الآلي الأوتوماتيكي.",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VipGold,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_vip_success_btn")
                ) {
                    Text("ابدأ اللعب بالميزات الأسطورية!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VipPromoCard(onUpgradeClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD8E4)),
        border = BorderStroke(2.dp, Color(0xFFFFB2BE)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUpgradeClick() }
            .shadow(4.dp, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFBA1A1A),
                    contentColor = Color.White
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "اشتراك",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "مميزات الـ VIP 👑",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF31111D),
                        fontSize = 14.sp
                    )
                    Text(
                        "دخل مضاعف 2x وهدايا يومية حصرية ومساعد آلي",
                        color = Color(0xFF31111D).copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFBA1A1A), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "VIP Badge",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

