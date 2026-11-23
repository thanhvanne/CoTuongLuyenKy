package com.thanhvan.cotuongluyenky.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thanhvan.cotuongluyenky.data.database.dao.AnalysisDao
import com.thanhvan.cotuongluyenky.data.database.dao.GameDao
import com.thanhvan.cotuongluyenky.data.database.dao.MoveDao
import com.thanhvan.cotuongluyenky.data.database.entity.AnalysisVariationEntity
import com.thanhvan.cotuongluyenky.data.database.entity.GameEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveAnalysisEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveEntity

@Database(
    entities = [
        GameEntity::class,
        MoveEntity::class,
        MoveAnalysisEntity::class,
        AnalysisVariationEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class CoTuongDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun moveDao(): MoveDao
    abstract fun analysisDao(): AnalysisDao

    companion object {
        const val DATABASE_NAME = "cotuong_luyenky.db"

        /**
         * Explicit non-destructive migration from Phase 1 (v1) to Phase 2 (v2),
         * preserving all user games and move histories (Section 20.1 & 29).
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `move_analysis` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `plyIndex` INTEGER NOT NULL,
                        `engineName` TEXT NOT NULL,
                        `engineVersion` TEXT NOT NULL,
                        `analysisConfig` TEXT NOT NULL,
                        `scoreBeforeCp` INTEGER,
                        `scoreAfterCp` INTEGER,
                        `scorePerspective` TEXT NOT NULL,
                        `mateBefore` INTEGER,
                        `mateAfter` INTEGER,
                        `bestMove` TEXT,
                        `playedMove` TEXT NOT NULL,
                        `principalVariation` TEXT NOT NULL,
                        `depth` INTEGER NOT NULL,
                        `searchTimeMs` INTEGER NOT NULL,
                        `classification` TEXT NOT NULL,
                        `commentary` TEXT NOT NULL,
                        `isTurningPoint` INTEGER NOT NULL,
                        `isQuickLiveEval` INTEGER NOT NULL,
                        `analysisVersion` INTEGER NOT NULL,
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_move_analysis_gameId_plyIndex` ON `move_analysis` (`gameId`, `plyIndex`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_move_analysis_gameId` ON `move_analysis` (`gameId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `analysis_variations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `analysisId` INTEGER NOT NULL,
                        `rank` INTEGER NOT NULL,
                        `scoreCp` INTEGER,
                        `mateIn` INTEGER,
                        `moves` TEXT NOT NULL,
                        FOREIGN KEY(`analysisId`) REFERENCES `move_analysis`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_analysis_variations_analysisId` ON `analysis_variations` (`analysisId`)"
                )
            }
        }

        fun create(context: Context): CoTuongDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CoTuongDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
