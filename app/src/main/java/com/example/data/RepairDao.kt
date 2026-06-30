package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RepairDao {
    @Query("SELECT * FROM repair_tickets ORDER BY dateCreated DESC")
    fun getAllTickets(): Flow<List<RepairTicket>>

    @Query("SELECT * FROM repair_tickets WHERE id = :id LIMIT 1")
    suspend fun getTicketById(id: Int): RepairTicket?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: RepairTicket): Long

    @Update
    suspend fun updateTicket(ticket: RepairTicket)

    @Delete
    suspend fun deleteTicket(ticket: RepairTicket)

    @Query("SELECT * FROM repair_tickets WHERE customerName LIKE :query OR phone LIKE :query OR deviceModel LIKE :query ORDER BY dateCreated DESC")
    fun searchTickets(query: String): Flow<List<RepairTicket>>
}
