package com.example.data

import kotlinx.coroutines.flow.Flow

class RepairRepository(private val repairDao: RepairDao) {
    val allTickets: Flow<List<RepairTicket>> = repairDao.getAllTickets()

    suspend fun getTicketById(id: Int): RepairTicket? {
        return repairDao.getTicketById(id)
    }

    suspend fun insertTicket(ticket: RepairTicket): Long {
        return repairDao.insertTicket(ticket)
    }

    suspend fun updateTicket(ticket: RepairTicket) {
        repairDao.updateTicket(ticket)
    }

    suspend fun deleteTicket(ticket: RepairTicket) {
        repairDao.deleteTicket(ticket)
    }

    fun searchTickets(query: String): Flow<List<RepairTicket>> {
        val searchQuery = "%$query%"
        return repairDao.searchTickets(searchQuery)
    }
}
