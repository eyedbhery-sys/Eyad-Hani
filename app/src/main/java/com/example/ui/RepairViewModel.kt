package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.RepairRepository
import com.example.data.RepairTicket
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RepairViewModel(private val repository: RepairRepository) : ViewModel() {

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Status filter ("الكل", "قيد الانتظار", "جاري الإصلاح", "تم الإصلاح", "استلم العميل")
    private val _statusFilter = MutableStateFlow("الكل")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    // Get tickets based on search and status filter
    val tickets: StateFlow<List<RepairTicket>> = combine(
        _searchQuery,
        _statusFilter,
        repository.allTickets
    ) { query, status, allList ->
        val filteredBySearch = if (query.isBlank()) {
            allList
        } else {
            allList.filter {
                it.customerName.contains(query, ignoreCase = true) ||
                it.phone.contains(query, ignoreCase = true) ||
                it.deviceModel.contains(query, ignoreCase = true) ||
                it.fault.contains(query, ignoreCase = true)
            }
        }

        if (status == "الكل") {
            filteredBySearch
        } else {
            filteredBySearch.filter { it.status == status }
        }
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current selected ticket for editing or viewing
    private val _selectedTicket = MutableStateFlow<RepairTicket?>(null)
    val selectedTicket: StateFlow<RepairTicket?> = _selectedTicket.asStateFlow()

    // Form inputs state
    var formCustomerName = MutableStateFlow("")
    var formPhone = MutableStateFlow("")
    var formDeviceModel = MutableStateFlow("")
    var formFault = MutableStateFlow("")
    var formPassword = MutableStateFlow("")
    var formTotalCost = MutableStateFlow("")
    var formPaid = MutableStateFlow("")
    var formStatus = MutableStateFlow("قيد الانتظار")
    var formNotes = MutableStateFlow("")

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setStatusFilter(filter: String) {
        _statusFilter.value = filter
    }

    // Set form fields for editing
    fun selectTicketForEdit(ticket: RepairTicket?) {
        _selectedTicket.value = ticket
        if (ticket != null) {
            formCustomerName.value = ticket.customerName
            formPhone.value = ticket.phone
            formDeviceModel.value = ticket.deviceModel
            formFault.value = ticket.fault
            formPassword.value = ticket.password
            formTotalCost.value = if (ticket.totalCost == 0.0) "" else ticket.totalCost.toString()
            formPaid.value = if (ticket.paid == 0.0) "" else ticket.paid.toString()
            formStatus.value = ticket.status
            formNotes.value = ticket.notes
        } else {
            clearForm()
        }
    }

    fun clearForm() {
        formCustomerName.value = ""
        formPhone.value = ""
        formDeviceModel.value = ""
        formFault.value = ""
        formPassword.value = ""
        formTotalCost.value = ""
        formPaid.value = ""
        formStatus.value = "قيد الانتظار"
        formNotes.value = ""
    }

    fun saveTicket(onSuccess: () -> Unit) {
        val name = formCustomerName.value.trim()
        val faultText = formFault.value.trim()

        if (name.isEmpty() || faultText.isEmpty()) {
            return
        }

        val phoneText = formPhone.value.trim()
        val model = formDeviceModel.value.trim()
        val passwordText = formPassword.value.trim()
        val total = formTotalCost.value.toDoubleOrNull() ?: 0.0
        val paidAmt = formPaid.value.toDoubleOrNull() ?: 0.0
        val remain = total - paidAmt
        val statusText = formStatus.value
        val notesText = formNotes.value.trim()

        viewModelScope.launch {
            val current = _selectedTicket.value
            if (current != null) {
                // Update
                val updatedTicket = current.copy(
                    customerName = name,
                    phone = phoneText,
                    deviceModel = model,
                    fault = faultText,
                    password = passwordText,
                    totalCost = total,
                    paid = paidAmt,
                    remaining = remain,
                    status = statusText,
                    notes = notesText
                )
                repository.updateTicket(updatedTicket)
            } else {
                // Insert
                val newTicket = RepairTicket(
                    customerName = name,
                    phone = phoneText,
                    deviceModel = model,
                    fault = faultText,
                    password = passwordText,
                    totalCost = total,
                    paid = paidAmt,
                    remaining = remain,
                    status = statusText,
                    notes = notesText
                )
                repository.insertTicket(newTicket)
            }
            clearForm()
            _selectedTicket.value = null
            onSuccess()
        }
    }

    fun updateTicketStatus(ticket: RepairTicket, newStatus: String) {
        viewModelScope.launch {
            repository.updateTicket(ticket.copy(status = newStatus))
        }
    }

    fun deleteTicket(ticket: RepairTicket) {
        viewModelScope.launch {
            repository.deleteTicket(ticket)
        }
    }

    // Dashboard calculations
    val dashboardStats: StateFlow<DashboardStats> = repository.allTickets.map { list ->
        val totalRepairs = list.size
        val pendingCount = list.count { it.status == "قيد الانتظار" || it.status == "جاري الإصلاح" }
        val repairedCount = list.count { it.status == "تم الإصلاح" }
        val deliveredCount = list.count { it.status == "استلم العميل" }
        
        val totalRevenue = list.sumOf { it.paid }
        val totalDues = list.sumOf { it.remaining }

        DashboardStats(
            totalRepairs = totalRepairs,
            pendingRepairs = pendingCount,
            repairedRepairs = repairedCount,
            deliveredRepairs = deliveredCount,
            totalRevenue = totalRevenue,
            totalDues = totalDues
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardStats()
    )
}

data class DashboardStats(
    val totalRepairs: Int = 0,
    val pendingRepairs: Int = 0,
    val repairedRepairs: Int = 0,
    val deliveredRepairs: Int = 0,
    val totalRevenue: Double = 0.0,
    val totalDues: Double = 0.0
)

class RepairViewModelFactory(private val repository: RepairRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RepairViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RepairViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
