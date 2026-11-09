package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromStatus(status: TicketStatus?): String {
        return status?.name ?: TicketStatus.PENDING.name
    }

    @TypeConverter
    fun toStatus(value: String?): TicketStatus {
        return try {
            if (value != null) TicketStatus.valueOf(value) else TicketStatus.PENDING
        } catch (e: Exception) {
            TicketStatus.PENDING
        }
    }
}

@Database(entities = [LotteryTicket::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ticketDao(): TicketDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "second_chance_lottery.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
