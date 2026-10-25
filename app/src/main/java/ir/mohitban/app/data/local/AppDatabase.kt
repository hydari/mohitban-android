package ir.mohitban.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, VehicleEntity::class, SettingsEntity::class, FuelEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun settingsDao(): SettingsDao
    abstract fun fuelEntryDao(): FuelEntryDao

    companion object {
        const val NAME = "mohitban.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
