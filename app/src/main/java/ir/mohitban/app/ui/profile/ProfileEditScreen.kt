package ir.mohitban.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.core.text.rial
import ir.mohitban.app.domain.model.Vehicle
import ir.mohitban.app.domain.model.VehicleType
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.domain.repository.SettingsRepository
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.ConfirmDialog
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.PlateView
import ir.mohitban.app.ui.components.SectionTitle
import ir.mohitban.app.ui.components.SuccessDialog
import ir.mohitban.app.ui.components.TagBadge
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/* ======================= personal + duty station ======================= */

class ProfileEditViewModel(private val repo: ProfileRepository) : ViewModel() {
    var profile by mutableStateOf(ProfileForm())
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var loaded by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    private var showErrors = false

    init {
        viewModelScope.launch {
            repo.getProfile()?.let { profile = ProfileForm.from(it) }
            loaded = true
        }
    }

    private fun currentErrors() = profile.personalErrors() + profile.locationErrors()

    fun update(p: ProfileForm) { profile = p; if (showErrors) errors = currentErrors() }

    fun save() {
        val e = currentErrors()
        errors = e
        showErrors = true
        if (e.isNotEmpty()) return
        viewModelScope.launch {
            repo.saveProfile(profile.toDomain())
            saved = true
        }
    }
}

@Composable
fun ProfileEditScreen(onBack: () -> Unit) {
    val vm = appViewModel(key = "edit_profile") { c, _ -> ProfileEditViewModel(c.profileRepository) }
    val context = LocalContext.current
    if (vm.saved) SuccessDialog("تغییرات ذخیره شد", onFinished = onBack)
    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("ویرایش اطلاعات فردی", onBack = onBack)
        if (!vm.loaded) return@Column
        Column(
            Modifier
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            SectionTitle("اطلاعات فردی")
            PersonalSection(vm.profile, vm.errors, vm::update)
            SectionTitle("محل خدمت")
            LocationSection(vm.profile, vm.errors, vm::update)
            Spacer(Modifier.height(22.dp))
            PillButton("ذخیره تغییرات", onClick = vm::save, icon = Icons.Outlined.Check)
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/* ======================= saved vehicles (plates) ======================= */

class VehiclesViewModel(private val repo: ProfileRepository, private val settings: SettingsRepository) : ViewModel() {
    val vehicles = repo.vehicles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val defaultId = settings.defaultVehicleId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDefault(id: Long) = viewModelScope.launch { settings.setDefaultVehicleId(id) }
    fun delete(id: Long, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(repo.deleteVehicle(id)) }
}

@Composable
fun VehiclesScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = appViewModel(key = "vehicles") { c, _ -> VehiclesViewModel(c.profileRepository, c.settingsRepository) }
    val list by vm.vehicles.collectAsStateWithLifecycle()
    val defaultId by vm.defaultId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<Vehicle?>(null) }

    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("وسایل نقلیه و پلاک‌ها", onBack = onBack)
        Text(
            "خودروها و موتورسیکلت‌هایی که با آن‌ها سوخت‌گیری می‌کنید را اینجا ذخیره کنید تا در فرم سوخت قابل انتخاب باشند.",
            style = MaterialTheme.typography.bodySmall, color = MColors.Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list.orEmpty(), key = { it.id }) { v ->
                val isDefault = v.id == (defaultId ?: list?.firstOrNull()?.id)
                AppCard(Modifier.fillMaxWidth(), onClick = { onEdit(v.id) }) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircleIcon(
                                if (v.type == VehicleType.CAR) Icons.Outlined.DirectionsCar else Icons.Outlined.TwoWheeler,
                                MColors.GreenLight, MColors.Green, size = 42.dp,
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(v.title, style = MaterialTheme.typography.titleSmall)
                                    if (isDefault) TagBadge("پیش‌فرض", MColors.GreenLight, MColors.Green, Modifier.padding(start = 6.dp))
                                }
                                Text(
                                    listOf(v.type.title, v.kind).filter { it.isNotBlank() }.joinToString(" | ") + " | کیلومتر ${v.odometer.rial()}",
                                    style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            PlateView(v.plate, height = 40.dp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallAction(if (isDefault) Icons.Outlined.Star else Icons.Outlined.StarBorder, "پیش‌فرض", Modifier.weight(1f)) { vm.setDefault(v.id) }
                            SmallAction(Icons.Outlined.Edit, "ویرایش", Modifier.weight(1f)) { onEdit(v.id) }
                            SmallAction(Icons.Outlined.Delete, "حذف", Modifier.weight(1f), danger = true) { toDelete = v }
                        }
                    }
                }
            }
        }
        PillButton(
            "افزودن خودرو یا موتورسیکلت", onClick = { onEdit(0L) }, icon = Icons.Outlined.Add,
            modifier = Modifier.padding(16.dp).navigationBarsPadding(),
        )
    }

    toDelete?.let { v ->
        ConfirmDialog(
            title = "حذف وسیله",
            text = "«${v.title}» از فهرست حذف شود؟ ثبت‌های سوخت قبلی آن باقی می‌مانند.",
            confirm = "حذف", danger = true,
            onDismiss = { toDelete = null },
            onConfirm = {
                toDelete = null
                vm.delete(v.id) { ok ->
                    if (!ok) Toast.makeText(context, "دست‌کم یک وسیله باید باقی بماند", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun SmallAction(icon: ImageVector, text: String, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (danger) MColors.ErrorBg else MColors.Bg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (danger) MColors.Error else MColors.Green
        Icon(icon, null, tint = c, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = c, modifier = Modifier.padding(start = 4.dp))
    }
}

class VehicleEditViewModel(
    private val repo: ProfileRepository,
    private val settings: SettingsRepository,
    private val vehicleId: Long,
) : ViewModel() {
    var form by mutableStateOf(VehicleForm())
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var showErrors by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var savedId by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch {
            if (vehicleId != 0L) repo.getVehicle(vehicleId)?.let { form = VehicleForm.from(it) }
            loaded = true
        }
    }

    fun update(v: VehicleForm) { form = v; if (showErrors) errors = v.errors() }

    fun save() {
        val e = form.errors()
        errors = e
        showErrors = true
        if (e.isNotEmpty()) return
        viewModelScope.launch {
            val id = repo.saveVehicle(form.toDomain())
            if (settings.getDefaultVehicleId() == null) settings.setDefaultVehicleId(id)
            savedId = id
        }
    }
}

@Composable
fun VehicleEditScreen(vehicleId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel(key = "vehicle_$vehicleId") { c, _ -> VehicleEditViewModel(c.profileRepository, c.settingsRepository, vehicleId) }
    val context = LocalContext.current
    vm.savedId?.let { id -> SuccessDialog("اطلاعات وسیله ذخیره شد", onFinished = { onSaved(id) }) }
    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar(if (vehicleId == 0L) "افزودن وسیله نقلیه" else "ویرایش وسیله نقلیه", onBack = onBack)
        if (!vm.loaded) return@Column
        Column(
            Modifier
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            VehicleSection(vm.form, vm.errors, vm::update, vm.showErrors)
            Spacer(Modifier.height(22.dp))
            PillButton("ذخیره", onClick = vm::save, icon = Icons.Outlined.Check)
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

