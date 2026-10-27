package ir.mohitban.app

import android.app.Application
import android.content.Context
import ir.mohitban.app.data.backup.BackupManager
import ir.mohitban.app.data.files.FileStore
import ir.mohitban.app.data.local.AppDatabase
import ir.mohitban.app.data.repository.FuelRepositoryImpl
import ir.mohitban.app.data.repository.ProfileRepositoryImpl
import ir.mohitban.app.data.repository.SettingsRepositoryImpl
import ir.mohitban.app.domain.repository.FuelRepository
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.domain.repository.SettingsRepository
import ir.mohitban.app.pdf.FuelFormRenderer
import ir.mohitban.app.pdf.QuarterReportRenderer
import ir.mohitban.app.ui.theme.AppFonts

/** Simple manual dependency container (no DI framework needed for an app of this size). */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val db: AppDatabase = AppDatabase.create(context)
    val files = FileStore(context)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(db, files)
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(db)
    val fuelRepository: FuelRepository = FuelRepositoryImpl(db, files)
    val fuelForm = FuelFormRenderer(context, files)
    val quarterReport = QuarterReportRenderer(context, files)
    val backup = BackupManager(context, db, files)
    val gasht = ir.mohitban.app.gasht.GashtRepository(db, files)
}

class MohitbanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppFonts.init(this)
        container = AppContainer(this)
        ir.mohitban.app.reminder.ReportReminder.schedule(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as MohitbanApp).container
