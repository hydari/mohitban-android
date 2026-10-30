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
import androidx.compose.material.icons.outlined.CalendarMonth
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
import ir.mohitban.app.ui.components.SuccessDialog
import ir.mohitban.app.ui.profile.LocationSection
import ir.mohitban.app.ui.profile.PersonalSection
import ir.mohitban.app.ui.profile.ProfileForm
import ir.mohitban.app.ui.profile.VehicleForm
import ir.mohitban.app.ui.profile.VehicleSection
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.launch
import ir.mohitban.app.core.text.fa

class OnboardingViewModel(
    private val profiles: ProfileRepository,
    private val settings: ir.mohitban.app.domain.repository.SettingsRepository,
    @Suppress("unused") private val fuel: FuelRepository,
    @Suppress("unused") private val handle: SavedStateHandle,
) : ViewModel() {
    var step by mutableIntStateOf(0)
        private set
    var profile by mutableStateOf(ProfileForm())
    // shift pattern (required at registration)
    var shiftPreset by mutableIntStateOf(-2) // -2 = nothing chosen yet, -1 = custom
    var customWork by mutableStateOf("")
    var customRest by mutableStateOf("")
    var shiftStart by mutableStateOf(ir.mohitban.app.core.jalali.JalaliDate.today())
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

    fun shiftChanged() { if (showErrors) errors = currentErrors() }

    fun workRest(): Pair<Int, Int>? = when (shiftPreset) {
        -2 -> null
        -1 -> {
            val w = customWork.toIntOrNull(); val r = customRest.toIntOrNull()
            if (w == null || r == null || w !in 1..30 || r !in 1..30) null else w to r
        }
        else -> ir.mohitban.app.domain.model.ShiftPattern.PRESETS[shiftPreset]
    }

    private fun shiftErrors(): Map<String, String> = when {
        shiftPreset == -2 -> mapOf("shift" to "الگوی شیفت خود را انتخاب کنید")
        workRest() == null -> mapOf("shift" to "تعداد روز کار و استراحت باید بین ۱ تا ۳۰ باشد")
        else -> emptyMap()
    }

    private fun currentErrors() = when (step) {
        0 -> profile.personalErrors()
        1 -> profile.locationErrors()
        else -> shiftErrors()
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
                profiles.register(profile.toDomain())
                workRest()?.let { (w, r) -> settings.saveShift(ir.mohitban.app.domain.model.ShiftPattern(w, r, shiftStart.jdn)) }
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

private val TITLES = listOf("اطلاعات فردی", "محل خدمت", "شیفت کاری")
private val SUBTITLES = listOf(
    "لطفاً اطلاعات شخصی خود را وارد کنید",
    "این اطلاعات یک بار ثبت می‌شود و بعداً قابل ویرایش است",
    "الگوی شیفت و اولین روز شیفت خود را انتخاب کنید",
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val vm = appViewModel { c, h -> OnboardingViewModel(c.profileRepository, c.settingsRepository, c.fuelRepository, h) }
    if (vm.done) SuccessDialog("ثبت‌نام با موفقیت انجام شد", onFinished = onFinished)
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
                else -> ShiftStep(vm)
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

/** Registration step 3: the shift pattern is required (presets or custom) plus its first day. */
@Composable
private fun ShiftStep(vm: OnboardingViewModel) {
    var pick by androidx.compose.runtime.remember { mutableStateOf(false) }
    val presets = ir.mohitban.app.domain.model.ShiftPattern.PRESETS
    val labels = presets.map { (w, r) -> "${w.fa()} روز کار / ${r.fa()} روز استراحت" } + "الگوی دلخواه"
    labels.chunked(2).forEachIndexed { row, pair ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            pair.forEachIndexed { k, label ->
                val index = row * 2 + k
                val value = if (index == presets.size) -1 else index
                ir.mohitban.app.ui.gasht.ChoiceChip(label, vm.shiftPreset == value, Modifier.weight(1f).height(48.dp)) {
                    vm.shiftPreset = value; vm.shiftChanged()
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (vm.shiftPreset == -1) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            ir.mohitban.app.ui.components.MField(
                vm.customWork, { vm.customWork = it; vm.shiftChanged() }, Modifier.weight(1f), label = "روز کار",
                numeric = ir.mohitban.app.ui.components.NumericMode.Digits, maxLength = 2,
            )
            ir.mohitban.app.ui.components.MField(
                vm.customRest, { vm.customRest = it; vm.shiftChanged() }, Modifier.weight(1f), label = "روز استراحت",
                numeric = ir.mohitban.app.ui.components.NumericMode.Digits, maxLength = 2,
            )
        }
    }
    ir.mohitban.app.ui.components.MField(
        vm.shiftStart.formatLong().fa(), {}, Modifier.padding(top = 6.dp),
        label = "اولین روز شیفت (شروع دوره)", leadingIcon = Icons.Outlined.CalendarMonth, onClick = { pick = true },
    )
    vm.errors["shift"]?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MColors.Error, modifier = Modifier.padding(top = 6.dp)) }
    Text(
        "بعداً از بخش «شیفت کاری» می‌توانید الگو را عوض کنید یا روزهای جابه‌جا شده را در تقویم علامت بزنید.",
        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 8.dp),
    )
    if (pick) {
        ir.mohitban.app.ui.components.JalaliDatePickerDialog(
            initial = vm.shiftStart, maxDate = null, title = "اولین روز شیفت",
            onDismiss = { pick = false }, onConfirm = { vm.shiftStart = it; pick = false },
        )
    }
}
