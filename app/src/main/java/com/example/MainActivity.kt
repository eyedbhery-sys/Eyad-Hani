package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.RepairRepository
import com.example.data.RepairTicket
import com.example.ui.DashboardStats
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.RepairViewModel
import com.example.ui.RepairViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = RepairRepository(database.repairDao())
        val viewModelFactory = RepairViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                // Force Right-to-Left (RTL) Layout Direction for perfect Arabic interface
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        RepairAppScreen(viewModelFactory)
                    }
                }
            }
        }
    }
}

@Composable
fun RepairAppScreen(
    factory: RepairViewModelFactory,
    viewModel: RepairViewModel = viewModel(factory = factory)
) {
    val context = LocalContext.current
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val selectedTicket by viewModel.selectedTicket.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Tickets, 1: Add/Edit, 2: Analytics & Info
    var ticketToShowDetails by remember { mutableStateOf<RepairTicket?>(null) }
    var ticketToDeleteConfirm by remember { mutableStateOf<RepairTicket?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                tonalElevation = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Filled.List, contentDescription = "قائمة الأجهزة") },
                    label = { Text("الأجهزة النشطة", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = {
                        viewModel.selectTicketForEdit(null) // Setup for new insert
                        activeTab = 1
                    },
                    icon = { Icon(Icons.Filled.AddCircle, contentDescription = "جهاز جديد") },
                    label = { Text("إضافة جهاز", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Filled.Analytics, contentDescription = "التقارير") },
                    label = { Text("التحليلات والتقارير", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Banner
            HeaderBanner(stats = stats)

            // Dynamic Content depending on current active tab
            Crossfade(
                targetState = activeTab,
                modifier = Modifier.weight(1f),
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> TicketsListTab(
                        tickets = tickets,
                        searchQuery = searchQuery,
                        statusFilter = statusFilter,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onFilterChange = { viewModel.setStatusFilter(it) },
                        onTicketClick = { ticketToShowDetails = it },
                        onTicketEdit = { ticket ->
                            viewModel.selectTicketForEdit(ticket)
                            activeTab = 1
                        },
                        onTicketDelete = { ticketToDeleteConfirm = it },
                        onStatusChange = { ticket, newStatus ->
                            viewModel.updateTicketStatus(ticket, newStatus)
                            Toast.makeText(context, "تم تغيير حالة الجهاز بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    )
                    1 -> AddEditTicketTab(
                        viewModel = viewModel,
                        onSaved = {
                            activeTab = 0
                            Toast.makeText(context, "تم حفظ بيانات الجهاز بنجاح", Toast.LENGTH_LONG).show()
                        },
                        onCancel = {
                            viewModel.clearForm()
                            activeTab = 0
                        }
                    )
                    2 -> AnalyticsTab(stats = stats, tickets = tickets)
                }
            }
        }
    }

    // Details Modal Dialog
    if (ticketToShowDetails != null) {
        TicketDetailsDialog(
            ticket = ticketToShowDetails!!,
            onDismiss = { ticketToShowDetails = null },
            onShare = { ticket ->
                shareReceiptText(context, ticket)
            },
            onWhatsApp = { ticket ->
                sendWhatsAppNotification(context, ticket)
            },
            onEdit = { ticket ->
                ticketToShowDetails = null
                viewModel.selectTicketForEdit(ticket)
                activeTab = 1
            }
        )
    }

    // Delete Confirmation Dialog
    if (ticketToDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { ticketToDeleteConfirm = null },
            title = { Text("تأكيد حذف الجهاز", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("هل أنت متأكد من رغبتك في حذف جهاز العميل \"${ticketToDeleteConfirm?.customerName}\"؟ هذا الإجراء لا يمكن التراجع عنه.") },
            confirmButton = {
                Button(
                    onClick = {
                        ticketToDeleteConfirm?.let { viewModel.deleteTicket(it) }
                        ticketToDeleteConfirm = null
                        Toast.makeText(context, "تم حذف الجهاز بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف نهائي", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { ticketToDeleteConfirm = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun HeaderBanner(stats: DashboardStats) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "مركز الصيانة الذكي",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "تتبع فواتير الإصلاح والمدفوعات والمتبقي للعملاء",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = "صيانة",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats overview row inside the header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatHeaderItem(
                        title = "الأجهزة",
                        value = stats.totalRepairs.toString(),
                        icon = Icons.Filled.PhoneAndroid
                    )
                    StatHeaderItem(
                        title = "جاري صيانته",
                        value = stats.pendingRepairs.toString(),
                        icon = Icons.Filled.PendingActions,
                        iconColor = Color(0xFFFFD166)
                    )
                    StatHeaderItem(
                        title = "إجمالي الدخل",
                        value = "${stats.totalRevenue.toInt()} ج.م",
                        icon = Icons.Filled.TrendingUp,
                        iconColor = Color(0xFF06D6A0)
                    )
                    StatHeaderItem(
                        title = "ديون متبقية",
                        value = "${stats.totalDues.toInt()} ج.م",
                        icon = Icons.Filled.AccountBalanceWallet,
                        iconColor = Color(0xFFEF476F)
                    )
                }
            }
        }
    }
}

@Composable
fun StatHeaderItem(title: String, value: String, icon: ImageVector, iconColor: Color = Color.White) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TicketsListTab(
    tickets: List<RepairTicket>,
    searchQuery: String,
    statusFilter: String,
    onSearchChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onTicketClick: (RepairTicket) -> Unit,
    onTicketEdit: (RepairTicket) -> Unit,
    onTicketDelete: (RepairTicket) -> Unit,
    onStatusChange: (RepairTicket, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("search_bar"),
            placeholder = { Text("ابحث باسم العميل، الهاتف، أو نوع الجهاز...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "بحث") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "مسح")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        // Filter chips row
        val filterStates = listOf("الكل", "قيد الانتظار", "جاري الإصلاح", "تم الإصلاح", "استلم العميل")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            items(filterStates) { state ->
                val isSelected = statusFilter == state
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterChange(state) },
                    label = { Text(state, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // List Content
        if (tickets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SearchOff,
                        contentDescription = "لا توجد أجهزة",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "لم يتم العثور على أجهزة صيانة",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "جرب تغيير نص البحث أو حدد فلتر حالة آخر، أو اضغط على \"إضافة جهاز\" لإدخال كشف جديد.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("tickets_list")
            ) {
                items(tickets, key = { it.id }) { ticket ->
                    RepairTicketItem(
                        ticket = ticket,
                        onClick = { onTicketClick(ticket) },
                        onEdit = { onTicketEdit(ticket) },
                        onDelete = { onTicketDelete(ticket) },
                        onStatusChange = { newStatus -> onStatusChange(ticket, newStatus) }
                    )
                }
            }
        }
    }
}

@Composable
fun RepairTicketItem(
    ticket: RepairTicket,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showStatusDropdown by remember { mutableStateOf(false) }

    val statusColor = when (ticket.status) {
        "قيد الانتظار" -> Color(0xFFF7B500)
        "جاري الإصلاح" -> Color(0xFF00A8E8)
        "تم الإصلاح" -> Color(0xFF9B5DE5)
        "استلم العميل" -> Color(0xFF00F5D4)
        else -> MaterialTheme.colorScheme.outline
    }

    val statusTextColor = when (ticket.status) {
        "قيد الانتظار" -> Color(0xFF5E4100)
        "جاري الإصلاح" -> Color.White
        "تم الإصلاح" -> Color.White
        "استلم العميل" -> Color(0xFF00382F)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val formattedDate = remember(ticket.dateCreated) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
        sdf.format(Date(ticket.dateCreated))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("ticket_item_${ticket.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Client Name, Ticket ID & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ticket.customerName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ticket.deviceModel.ifBlank { "جهاز غير محدد" },
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.2f))
                            .clickable { showStatusDropdown = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ticket.status,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "تغيير الحالة",
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Dropdown for changing state
                        DropdownMenu(
                            expanded = showStatusDropdown,
                            onDismissRequest = { showStatusDropdown = false }
                        ) {
                            val statuses = listOf("قيد الانتظار", "جاري الإصلاح", "تم الإصلاح", "استلم العميل")
                            statuses.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        onStatusChange(s)
                                        showStatusDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تذكرة #${ticket.id}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Description / Defect & Contact
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1.5f)) {
                    Text(
                        text = "العيب المذكور:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = ticket.fault,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "الحساب المالي:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (ticket.remaining > 0) "باقي: ${ticket.remaining.toInt()} ج.م" else "خالص",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = if (ticket.remaining > 0) Color(0xFFEF476F) else Color(0xFF06D6A0)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom bar with actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // WhatsApp
                    IconButton(
                        onClick = { sendWhatsAppNotification(context, ticket) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Chat,
                            contentDescription = "واتساب",
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Share
                    IconButton(
                        onClick = { shareReceiptText(context, ticket) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "مشاركة",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Edit
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "تعديل",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditTicketTab(
    viewModel: RepairViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val selectedTicket by viewModel.selectedTicket.collectAsStateWithLifecycle()

    val name by viewModel.formCustomerName.collectAsStateWithLifecycle()
    val phone by viewModel.formPhone.collectAsStateWithLifecycle()
    val deviceModel by viewModel.formDeviceModel.collectAsStateWithLifecycle()
    val fault by viewModel.formFault.collectAsStateWithLifecycle()
    val password by viewModel.formPassword.collectAsStateWithLifecycle()
    val totalCost by viewModel.formTotalCost.collectAsStateWithLifecycle()
    val paid by viewModel.formPaid.collectAsStateWithLifecycle()
    val status by viewModel.formStatus.collectAsStateWithLifecycle()
    val notes by viewModel.formNotes.collectAsStateWithLifecycle()

    // Real-time calculated remaining
    val remaining = remember(totalCost, paid) {
        val total = totalCost.toDoubleOrNull() ?: 0.0
        val paidAmt = paid.toDoubleOrNull() ?: 0.0
        val diff = total - paidAmt
        if (diff < 0) 0.0 else diff
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag("repair_form"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (selectedTicket != null) "تعديل بيانات تذكرة #${selectedTicket!!.id}" else "تسجيل جهاز صيانة جديد",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Customer Name
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.formCustomerName.value = it },
                label = { Text("اسم العميل (مطلوب) *") },
                placeholder = { Text("أدخل الاسم الكامل للعميل") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("form_name"),
                singleLine = true,
                isError = name.trim().isEmpty(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Phone Number
        item {
            OutlinedTextField(
                value = phone,
                onValueChange = { viewModel.formPhone.value = it },
                label = { Text("رقم الهاتف") },
                placeholder = { Text("مثال: 01012345678") },
                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().testTag("form_phone"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Device Model
        item {
            OutlinedTextField(
                value = deviceModel,
                onValueChange = { viewModel.formDeviceModel.value = it },
                label = { Text("نوع / موديل الجهاز") },
                placeholder = { Text("مثال: Samsung S23, iPhone 14") },
                leadingIcon = { Icon(Icons.Filled.Smartphone, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("form_model"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Fault / Problem Description
        item {
            OutlinedTextField(
                value = fault,
                onValueChange = { viewModel.formFault.value = it },
                label = { Text("العيب المطلوب إصلاحه (مطلوب) *") },
                placeholder = { Text("مثال: شاشة مكسورة، تالفة شحن، عيب باور") },
                leadingIcon = { Icon(Icons.Filled.Build, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("form_fault"),
                isError = fault.trim().isEmpty(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Password / Screen Pattern
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.formPassword.value = it },
                label = { Text("كلمة سر الجهاز أو النمط") },
                placeholder = { Text("أدخل رمز المرور لتمكين الفحص") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("form_password"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Costs layout: Total Cost, Paid, Auto Remaining
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = totalCost,
                    onValueChange = { viewModel.formTotalCost.value = it },
                    label = { Text("التكلفة (ج.م)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("form_total"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = paid,
                    onValueChange = { viewModel.formPaid.value = it },
                    label = { Text("المدفوع (ج.م)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("form_paid"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Giant Outstanding Dues Indicator (Automatic Calculation)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (remaining > 0.0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else Color(0xFFE8F5E9)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (remaining > 0.0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "المبلغ المتبقي للتحصيل",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (remaining > 0.0) "${remaining.toInt()} جنيهاً مصرياً" else "تم سداد الحساب بالكامل ✓",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (remaining > 0.0) MaterialTheme.colorScheme.error else Color(0xFF1B5E20)
                        )
                    }

                    Icon(
                        imageVector = if (remaining > 0.0) Icons.Filled.MoneyOff else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (remaining > 0.0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Status Choice
        item {
            Text("حالة الإصلاح الحالية:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val statuses = listOf("قيد الانتظار", "جاري الإصلاح", "تم الإصلاح", "استلم العميل")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statuses.forEach { s ->
                    val isSelected = status == s
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { viewModel.formStatus.value = s }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Form Notes
        item {
            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.formNotes.value = it },
                label = { Text("ملاحظات الفحص / مكملات الجهاز") },
                placeholder = { Text("مثال: الجهاز يشتمل على جراب، بدون خط، يوجد خدش في الخلف") },
                leadingIcon = { Icon(Icons.Filled.EditNote, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("form_notes"),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Action Buttons: Save & Cancel
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.saveTicket(onSuccess = onSaved)
                    },
                    modifier = Modifier.weight(1.5f).height(54.dp).testTag("save_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = name.trim().isNotEmpty() && fault.trim().isNotEmpty()
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ تذكرة الصيانة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("إلغاء", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AnalyticsTab(stats: DashboardStats, tickets: List<RepairTicket>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "التقارير والإيرادات المالية",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "نظرة تفصيلية على أداء المحل والأموال المعلقة",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Revenue Card Split
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "الميزانية العامة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Column {
                            Text("المحصل الفعلي", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                "${stats.totalRevenue.toInt()} ج.م",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF06D6A0)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("الديون المستحقة", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                "${stats.totalDues.toInt()} ج.م",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFEF476F)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    val totalExpected = stats.totalRevenue + stats.totalDues
                    val collectionRate = if (totalExpected > 0) (stats.totalRevenue / totalExpected).toFloat() else 0f

                    LinearProgressIndicator(
                        progress = { collectionRate },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = Color(0xFF06D6A0),
                        trackColor = Color(0xFFEF476F).copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(
                            text = "معدل التحصيل: ${(collectionRate * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مجموع العمليات: ${stats.totalRepairs} جهاز",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Job Status breakdown
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("إحصائيات الأجهزة وحالتها", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    StatusAnalyticsRow("قيد الانتظار", stats.pendingRepairs, Color(0xFFF7B500), stats.totalRepairs)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusAnalyticsRow("تم الإصلاح", stats.repairedRepairs, Color(0xFF9B5DE5), stats.totalRepairs)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusAnalyticsRow("استلم العميل", stats.deliveredRepairs, Color(0xFF00F5D4), stats.totalRepairs)
                }
            }
        }

        // Tips Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.TipsAndUpdates,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "نصيحة الصيانة الذكية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "يمكنك إرسال إشعارات سريعة للعملاء بنقرة زر واحدة عبر أيقونة الواتساب بجوار كل جهاز في القائمة الرئيسية لتذكيرهم بالاستلام أو بمبلغ الحساب المتبقي.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusAnalyticsRow(statusName: String, count: Int, color: Color, total: Int) {
    val percentage = if (total > 0) (count.toFloat() / total.toFloat()) else 0f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(statusName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text("$count جهاز (${(percentage * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun TicketDetailsDialog(
    ticket: RepairTicket,
    onDismiss: () -> Unit,
    onShare: (RepairTicket) -> Unit,
    onWhatsApp: (RepairTicket) -> Unit,
    onEdit: (RepairTicket) -> Unit
) {
    val formattedDate = remember(ticket.dateCreated) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
        sdf.format(Date(ticket.dateCreated))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تفاصيل تذكرة الصيانة",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Customer details block
                DetailRow(label = "رقم التذكرة", value = "#${ticket.id}", valueColor = MaterialTheme.colorScheme.primary)
                DetailRow(label = "اسم العميل", value = ticket.customerName)
                DetailRow(label = "رقم الهاتف", value = ticket.phone.ifBlank { "غير مسجل" })
                DetailRow(label = "نوع الجهاز", value = ticket.deviceModel.ifBlank { "غير مسجل" })
                DetailRow(label = "العيب المذكور", value = ticket.fault, valueColor = MaterialTheme.colorScheme.error)
                DetailRow(label = "كلمة مرور الجهاز", value = ticket.password.ifBlank { "بدون كلمة سر" })
                DetailRow(label = "تاريخ الدخول", value = formattedDate)

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Finance block
                DetailRow(label = "التكلفة الإجمالية", value = "${ticket.totalCost.toInt()} ج.م")
                DetailRow(label = "المبلغ المدفوع", value = "${ticket.paid.toInt()} ج.م")
                DetailRow(
                    label = "المتبقي للتحصيل",
                    value = "${ticket.remaining.toInt()} ج.م",
                    valueColor = if (ticket.remaining > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                )
                DetailRow(label = "حالة الإصلاح", value = ticket.status, valueColor = MaterialTheme.colorScheme.primary)

                if (ticket.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ملاحظات الفحص والملحقات:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = ticket.notes,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Dialog action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Send WhatsApp Notification
                    Button(
                        onClick = { onWhatsApp(ticket) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("واتساب", color = Color.White, fontSize = 12.sp)
                    }

                    // Share plain receipt text
                    Button(
                        onClick = { onShare(ticket) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة", color = Color.White, fontSize = 12.sp)
                    }

                    // Edit
                    Button(
                        onClick = { onEdit(ticket) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تعديل", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.End
        )
    }
}

// Global functions for integrations

fun sendWhatsAppNotification(context: Context, ticket: RepairTicket) {
    val phone = ticket.phone.trim()
    if (phone.isEmpty()) {
        Toast.makeText(context, "لا يوجد رقم هاتف مسجل لهذا العميل لإرسال واتساب", Toast.LENGTH_SHORT).show()
        return
    }

    // Format phone to international structure if needed
    val formattedPhone = if (phone.startsWith("0")) "2$phone" else phone

    val messageText = when (ticket.status) {
        "تم الإصلاح" -> {
            "مرحباً يا ${ticket.customerName}، يسعدنا إعلامك أن جهازك (${ticket.deviceModel}) قد تم إصلاحه بنجاح وهو جاهز للاستلام الآن في محل الصيانة. المتبقي للدفع هو: ${ticket.remaining.toInt()} ج.م. شكراً لتعاملك معنا!"
        }
        "استلم العميل" -> {
            "مرحباً يا ${ticket.customerName}، نشكرك لتعاملك معنا واستلام جهازك (${ticket.deviceModel}). يسعدنا دوماً خدمتك في مركز الصيانة لدينا!"
        }
        else -> {
            "مرحباً يا ${ticket.customerName}، نود إخطارك بأنه تم استلام جهازك (${ticket.deviceModel}) تحت تذكرة صيانة رقم #${ticket.id}. جاري فحص العيب (${ticket.fault}). سنوافيك بالتطورات قريباً."
        }
    }

    val url = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(messageText)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: copy to clipboard or standard share
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$messageText\n\n(رقم الهاتف: $phone)")
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة تفاصيل الفاتورة"))
    }
}

fun shareReceiptText(context: Context, ticket: RepairTicket) {
    val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar")).format(Date(ticket.dateCreated))
    val statusEmoji = when (ticket.status) {
        "تم الإصلاح" -> "✅"
        "استلم العميل" -> "📦"
        "جاري الإصلاح" -> "⚙️"
        else -> "⏳"
    }

    val text = """
        🧾 تذكرة صيانة رقم #${ticket.id}
        ------------------------------------
        👤 اسم العميل: ${ticket.customerName}
        📱 نوع الجهاز: ${ticket.deviceModel.ifBlank { "غير مسجل" }}
        📞 الهاتف: ${ticket.phone.ifBlank { "غير مسجل" }}
        🛠️ العيب المكتوب: ${ticket.fault}
        🔐 كلمة المرور: ${ticket.password.ifBlank { "لا يوجد" }}
        📅 التاريخ: $dateStr
        ------------------------------------
        💰 إجمالي التكلفة: ${ticket.totalCost.toInt()} ج.م
        💵 المبلغ المدفوع: ${ticket.paid.toInt()} ج.م
        💳 المتبقي للتحصيل: ${ticket.remaining.toInt()} ج.م
        $statusEmoji حالة الجهاز الحالية: ${ticket.status}
        ------------------------------------
        ملاحظات إضافية: ${ticket.notes.ifBlank { "لا يوجد" }}
        ------------------------------------
        شكراً لتعاملكم معنا في مركز الصيانة الذكية!
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة إيصال الصيانة"))
}
