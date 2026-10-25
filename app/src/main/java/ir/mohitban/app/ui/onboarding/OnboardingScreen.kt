package ir.mohitban.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.R
import ir.mohitban.app.domain.repository.FuelRepository
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.StepIndicator
import ir.mohitban.app.ui.profile.LocationSection
import ir.mohitban.app.ui.profile.PersonalSection
import ir.mohitban.app.ui.profile.ProfileForm
import ir.mohitban.app.ui.profile.VehicleForm
import ir.mohitban.app.ui.profile.VehicleSection
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val profiles: ProfileRepository,
    @Suppress("unused") private val fuel: FuelRepository,
    @Suppress("unused") private val handle: SavedStateHandle,
) : ViewModel() {
    var step by mutableIntStateOf(0)
        private set
    var profile by mutableStateOf(ProfileForm())
    var vehicle by mutableStateOf(VehicleForm())
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var showErrors by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    fun updateProfile(p: ProfileForm) {
        profile = p
        if (showErrors) errors = currentErrors()
    }

    fun updateVehicle(v: VehicleForm) {
        vehicle = v
        if (showErrors) errors = currentErrors()
    }

    private fun currentErrors() = when (step) {
        0 -> profile.personalErrors()
        1 -> profile.locationErrors()
        else -> vehicle.errors()
    }

    fun next() {
        val e = currentErrors()
        errors = e
        if (e.isNotEmpty()) {
            showErrors = true
            return
        }
        showErrors = false
        if (step < 2) {
            step++
        } else {
            saving = true
            viewModelScope.launch {
                profiles.save(profile.toDomain(), vehicle.toDomain())
                saving = false
                done = true
            }
        }
    }

    fun back(): Boolean {
        if (step == 0) return false
        step--
        errors = emptyMap()
        showErrors = false
        return true
    }
}

private val TITLES = listOf("اطلاعات فردی", "محل خدمت", "اطلاعات خودرو")
private val SUBTITLES = listOf(
    "لطفاً اطلاعات شخصی خود را وارد کنید",
    "این اطلاعات یک بار ثبت می‌شود و بعداً قابل ویرایش است",
    "لطفاً اطلاعات خودروی سازمانی را وارد کنید",
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val vm = appViewModel { c, h -> OnboardingViewModel(c.profileRepository, c.fuelRepository, h) }
    LaunchedEffect(vm.done) { if (vm.done) onFinished() }
    BackHandler(enabled = vm.step > 0) { vm.back() }
    val scroll = rememberScrollState()
    LaunchedEffect(vm.step) { scroll.animateScrollTo(0) }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Image(
            painterResource(R.drawable.bg_hills), null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            StepIndicator(count = 3, current = vm.step)
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(TITLES[vm.step], style = MaterialTheme.typography.headlineSmall, color = MColors.Ink)
                    Text(SUBTITLES[vm.step], style = MaterialTheme.typography.bodySmall, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Image(painterResource(R.drawable.doe_emblem), null, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(10.dp))
            when (vm.step) {
                0 -> PersonalSection(vm.profile, vm.errors, vm::updateProfile)
                1 -> LocationSection(vm.profile, vm.errors, vm::updateProfile)
                else -> VehicleSection(vm.vehicle, vm.errors, vm::updateVehicle, vm.showErrors)
            }
            Spacer(Modifier.height(24.dp))
            PillButton(
                text = if (vm.step < 2) "مرحله بعد" else "تکمیل ثبت‌نام",
                icon = if (vm.step < 2) Icons.AutoMirrored.Outlined.ArrowForward else Icons.Outlined.Check,
                loading = vm.saving,
                onClick = vm::next,
            )
            if (vm.step > 0) {
                Text(
                    "بازگشت به مرحله قبل",
                    style = MaterialTheme.typography.labelLarge,
                    color = MColors.Muted,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp)
                        .clickable { vm.back() }
                        .padding(12.dp),
                )
            }
            Spacer(Modifier.height(140.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
