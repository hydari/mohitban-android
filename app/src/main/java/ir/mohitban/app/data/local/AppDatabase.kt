package ir.mohitban.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class, VehicleEntity::class, SettingsEntity::class, FuelEntryEntity::class,
        TrackEntity::class, TrackPointEntity::class, WaypointEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun settingsDao(): SettingsDao
    abstract fun fuelEntryDao(): FuelEntryDao
    abstract fun gashtDao(): GashtDao

    companion object {
        const val NAME = "mohitban.db"

        /** v1 had a single vehicle; v2 keeps many vehicles (saved plates) and links entries to them. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `kind` TEXT NOT NULL, `model` TEXT NOT NULL, `year` TEXT NOT NULL, " +
                        "`plate` TEXT NOT NULL, `odometer` INTEGER NOT NULL, `photoPath` TEXT, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `vehicles` (`id`, `type`, `kind`, `model`, `year`, `plate`, `odometer`, `photoPath`, `createdAt`) " +
                        "SELECT `id`, `type`, `kind`, `model`, `year`, `plate`, `odometer`, `photoPath`, 0 FROM `vehicle`",
                )
                db.execSQL("DROP TABLE IF EXISTS `vehicle`")
                db.execSQL("ALTER TABLE `fuel_entry` ADD COLUMN `vehicleId` INTEGER")
                db.execSQL("UPDATE `fuel_entry` SET `vehicleId` = (SELECT MIN(`id`) FROM `vehicles`)")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `defaultVehicleId` INTEGER")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `reportedSeasonKey` INTEGER")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `lastReminderJdn` INTEGER")
            }
        }

        /** v3 remembers when an entry was edited after saving. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `fuel_entry` ADD COLUMN `editedAt` INTEGER")
            }
        }

        /** v4 stores the ranger's shift pattern. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `shiftWork` INTEGER")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `shiftRest` INTEGER")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `shiftStartJdn` INTEGER")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `shiftOverrides` TEXT")
            }
        }

        /** v5 adds گشت‌یار: patrol tracks, their points and marked places. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `distance` REAL NOT NULL, `duration` INTEGER NOT NULL, " +
                        "`pointCount` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackId` INTEGER NOT NULL, " +
                        "`seq` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `ele` REAL, `time` INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_points_trackId` ON `track_points` (`trackId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `waypoints` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `ele` REAL, `note` TEXT NOT NULL, " +
                        "`photoPath` TEXT, `createdAt` INTEGER NOT NULL, `source` TEXT NOT NULL)",
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
