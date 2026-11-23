package com.thanhvan.cotuongluyenky.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.thanhvan.cotuongluyenky.data.database.dao.GameDao
import com.thanhvan.cotuongluyenky.data.database.dao.MoveDao
import com.thanhvan.cotuongluyenky.data.database.entity.GameEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveEntity

@Database(
    entities = [
        GameEntity::class,
        MoveEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class CoTuongDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun moveDao(): MoveDao

    companion object {
        const val DATABASE_NAME = "cotuong_luyenky.db"

        fun create(context: Context): CoTuongDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CoTuongDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}
