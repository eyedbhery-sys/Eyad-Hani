package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repair_tickets")
data class RepairTicket(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val phone: String,
    val deviceModel: String,
    val fault: String,
    val password: String = "",
    val totalCost: Double = 0.0,
    val paid: Double = 0.0,
    val remaining: Double = 0.0,
    val status: String = "قيد الانتظار", // "قيد الانتظار", "جاري الإصلاح", "تم الإصلاح", "استلم العميل"
    val dateCreated: Long = System.currentTimeMillis(),
    val notes: String = ""
)
