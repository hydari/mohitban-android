package ir.mohitban.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.SectionTitle
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.launch

enum class EditSection { PROFILE, VEHICLE }

class ProfileEditViewModel(private val repo: ProfileRepository, val section: EditSection) : ViewModel() {
    var profile by mutableStateOf(ProfileForm())
    var vehicle by mutableStateOf(VehicleForm())
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var loaded by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var showErrors by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            repo.getProfile()?.let { profile = ProfileForm.from(it) }
            repo.getVehicle()?.let { vehicle = VehicleForm.from(it) }
            loaded = true
        }
    }

    private fun currentErrors() = when (section) {
        EditSection.PROFILE -> profile.personalErrors() + profile.locationErrors()
        EditSection.VEHICLE -> vehicle.errors()
    }

    fun updateProfile(p: ProfileForm) { profile = p; if (showErrors) errors = currentErrors() }
    fun updateVehicle(v: VehicleForm) { vehicle = v; if (showErrors) errors = currentErrors() }

    fun save() {
        val e = currentErrors()
        errors = e
        showErrors = true
        if (e.isNotEmpty()) return
        viewModelScope.launch {
            when (section) {
                EditSection.PROFILE -> repo.saveProfile(profile.toDomain())
                EditSection.VEHICLE -> repo.saveVehicle(vehicle.toDomain())
            }
            saved = true
        }
    }
}

@Composable
fun ProfileEditScreen(section: EditSection, onBack: () -> Unit) {
    val vm = appViewModel(key = "edit_$section") { c, _ -> ProfileEditViewModel(c.profileRepository, section) }
    val context = LocalContext.current
    LaunchedEffect(vm.saved) {
        if (vm.saved) {
            Toast.makeText(context, "تغییرات ذخیره شد", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }
    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar(if (section == EditSection.PROFILE) "ویرایش اطلاعات فردی" else "اطلاعات خودرو", onBack = onBack)
        if (!vm.loaded) return@Column
        Column(
            Modifier
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            when (section) {
                EditSection.PROFILE -> {
                    SectionTitle("اطلاعات فردی")
                    PersonalSection(vm.profile, vm.errors, vm::updateProfile)
                    SectionTitle("محل خدمت")
                    LocationSection(vm.profile, vm.errors, vm::updateProfile)
                }
                EditSection.VEHICLE -> VehicleSection(vm.vehicle, vm.errors, vm::updateVehicle, vm.showErrors)
            }
            Spacer(Modifier.height(22.dp))
            PillButton("ذخیره تغییرات", onClick = vm::save, icon = Icons.Outlined.Check)
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
