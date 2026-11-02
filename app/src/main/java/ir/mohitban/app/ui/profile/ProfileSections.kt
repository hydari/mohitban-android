package ir.mohitban.app.ui.profile

import androidx.compose.foundation.Image
import ir.mohitban.app.ui.theme.Corners
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.container
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.domain.model.VehicleType
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.TagBadge
import ir.mohitban.app.ui.components.CarPlateInput
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.ComboField
import ir.mohitban.app.ui.components.FieldLabel
import ir.mohitban.app.ui.components.FieldMessage
import ir.mohitban.app.ui.components.MField
import ir.mohitban.app.ui.components.MotoPlateInput
import ir.mohitban.app.ui.components.NumericMode
import ir.mohitban.app.ui.components.rememberImagePicker
import ir.mohitban.app.ui.components.rememberStoredImage
import ir.mohitban.app.ui.theme.MColors
import ir.mohitban.app.ui.components.clay
import ir.mohitban.app.ui.components.clayInset

@Composable
fun PersonalSection(form: ProfileForm, errors: Map<String, String>, onChange: (ProfileForm) -> Unit) {
    // camera or gallery → crop to the round frame → confirm → saved at the profile size
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var cropSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val picker = rememberImagePicker("profile", onImported = { onChange(form.copy(photoPath = it)) }, rawCameraFile = { f ->
        scope.launch {
            cropSource = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { context.container.files.decodeFile(f, 2000) }
        }
    })
    cropSource?.let { src ->
        ir.mohitban.app.ui.components.PhotoCropDialog(src, onDismiss = { cropSource = null }, onCropped = { out ->
            cropSource = null
            scope.launch {
                val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { context.container.files.saveJpeg(out, "profile", 92) }
                onChange(form.copy(photoPath = path))
            }
        })
    }
    val photo by rememberStoredImage(form.photoPath, 400)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 6.dp).clickable { picker.openSheet() }) {
            Box(
                Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(MColors.GreenLight)
                    .border(2.dp, MColors.Green2, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val p = photo
                if (p != null) Image(p, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Image(androidx.compose.ui.res.painterResource(ir.mohitban.app.R.drawable.avatar_default), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MColors.Green)
                    .border(3.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
        if (photo == null) {
            Text(
                "لطفاً حتماً عکس پرسنلی خود را انتخاب کنید",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = MColors.Amber, modifier = Modifier.padding(top = 6.dp),
            )
            Text("تا آن زمان این تصویر پیش‌فرض نمایش داده می‌شود.", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
        } else {
            Text("عکس پرسنلی — برای تغییر بزنید", style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
    MField(form.fullName, { onChange(form.copy(fullName = it)) }, label = "نام و نام خانوادگی", leadingIcon = Icons.Outlined.Person, placeholder = "محمدرضا حیدری", error = errors["fullName"])
    MField(form.fatherName, { onChange(form.copy(fatherName = it)) }, label = "نام پدر", leadingIcon = Icons.Outlined.Person, placeholder = "سعید", error = errors["fatherName"])
    // national ID is checked live: as soon as 10 digits are typed an invalid code shows an error
    val idCheck = ir.mohitban.app.core.text.Validators.nationalId(form.nationalId)
    val idLiveError = if (form.nationalId.length == 10) idCheck else null
    val idOk = form.nationalId.length == 10 && idCheck == null
    MField(
        form.nationalId, { onChange(form.copy(nationalId = it)) }, label = "کد ملی", leadingIcon = Icons.Outlined.Badge,
        numeric = NumericMode.Digits, maxLength = 10, placeholder = "۱۰ رقم", error = errors["nationalId"] ?: idLiveError,
        success = if (idOk) "کد ملی معتبر است" else null,
    )
    val mobileLiveError = if (form.mobile.length == 11) ir.mohitban.app.core.text.Validators.mobile(form.mobile) else null
    MField(
        form.mobile, { onChange(form.copy(mobile = it)) }, label = "شماره همراه", leadingIcon = Icons.Outlined.PhoneAndroid,
        numeric = NumericMode.Digits, maxLength = 11, placeholder = "۰۹۱۲۳۴۵۶۷۸۹", error = errors["mobile"] ?: mobileLiveError,
    )
    // rank (درجه) with its insignia
    var pickGrade by remember { mutableStateOf(false) }
    MField(
        form.grade, {}, label = "درجه", leadingIcon = Icons.Outlined.MilitaryTech, placeholder = "انتخاب درجه",
        error = errors["grade"], onClick = { pickGrade = true },
        trailing = if (form.grade.isNotBlank()) ({ ir.mohitban.app.ui.components.RankInsignia(form.grade, 26.dp, Modifier.padding(end = 6.dp)) }) else null,
    )
    if (pickGrade) {
        ir.mohitban.app.ui.components.GradePickerDialog(form.grade, onDismiss = { pickGrade = false }) { onChange(form.copy(grade = it)); pickGrade = false }
    }
    ir.mohitban.app.ui.components.SelectField(form.rank, { onChange(form.copy(rank = it)) }, Suggestions.ranks, label = "سمت", leadingIcon = Icons.Outlined.Park, placeholder = "انتخاب سمت", error = errors["rank"], title = "سمت خود را انتخاب کنید")
    // birthday (for the greeting) and employment date (for years of service)
    var pickBirth by remember { mutableStateOf(false) }
    var pickHire by remember { mutableStateOf(false) }
    MField(
        form.birthJdn?.let { ir.mohitban.app.core.jalali.JalaliDate.fromJdn(it).formatLong(false).fa() }.orEmpty(), {},
        label = "تاریخ تولد", leadingIcon = Icons.Outlined.Cake, placeholder = "انتخاب تاریخ", error = errors["birthJdn"], onClick = { pickBirth = true },
    )
    MField(
        form.hireJdn?.let { ir.mohitban.app.core.jalali.JalaliDate.fromJdn(it).formatLong(false).fa() }.orEmpty(), {},
        label = "تاریخ استخدام", leadingIcon = Icons.Outlined.WorkHistory, placeholder = "انتخاب تاریخ", error = errors["hireJdn"], onClick = { pickHire = true },
    )
    val today = ir.mohitban.app.core.jalali.JalaliDate.today()
    if (pickBirth) {
        ir.mohitban.app.ui.components.JalaliDatePickerDialog(
            initial = form.birthJdn?.let { ir.mohitban.app.core.jalali.JalaliDate.fromJdn(it) } ?: ir.mohitban.app.core.jalali.JalaliDate(today.year - 30, 1, 1),
            title = "تاریخ تولد (روی ماه و سال بزنید تا سال را انتخاب کنید)",
            onDismiss = { pickBirth = false }, onConfirm = { onChange(form.copy(birthJdn = it.jdn)); pickBirth = false },
        )
    }
    if (pickHire) {
        ir.mohitban.app.ui.components.JalaliDatePickerDialog(
            initial = form.hireJdn?.let { ir.mohitban.app.core.jalali.JalaliDate.fromJdn(it) } ?: ir.mohitban.app.core.jalali.JalaliDate(today.year - 5, 1, 1),
            title = "تاریخ استخدام (روی ماه و سال بزنید تا سال را انتخاب کنید)",
            onDismiss = { pickHire = false }, onConfirm = { onChange(form.copy(hireJdn = it.jdn)); pickHire = false },
        )
    }
}

@Composable
fun LocationSection(form: ProfileForm, errors: Map<String, String>, onChange: (ProfileForm) -> Unit) {
    ir.mohitban.app.ui.components.SelectField(
        form.province, { p -> onChange(form.copy(province = p, city = if (form.city in Suggestions.citiesOf(p)) form.city else "")) }, Suggestions.provinces,
        label = "اداره کل استان", leadingIcon = Icons.Outlined.LocationOn, error = errors["province"], title = "استان را انتخاب کنید",
    )
    ir.mohitban.app.ui.components.SelectField(
        form.city, { onChange(form.copy(city = it)) }, Suggestions.citiesOf(form.province), label = "شهرستان",
        leadingIcon = Icons.Outlined.LocationOn, placeholder = if (form.province.isBlank()) "ابتدا استان را انتخاب کنید" else "انتخاب شهرستان",
        error = errors["city"], title = "شهرستان محل خدمت را انتخاب کنید",
    )
    MField(form.protectedArea, { onChange(form.copy(protectedArea = it)) }, label = "منطقه حفاظت‌شده", leadingIcon = Icons.Outlined.Park, placeholder = "کاغذکنان", error = errors["protectedArea"])
    MField(form.station, { onChange(form.copy(station = it)) }, label = "پاسگاه محیط‌بانی", leadingIcon = Icons.Outlined.Home, placeholder = "آقکند", error = errors["station"], imeAction = androidx.compose.ui.text.input.ImeAction.Done)
}

@Composable
private fun TypeCard(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Corners.R)
    Row(
        modifier
            .height(58.dp)
            .then(if (selected) Modifier.clayInset(Corners.R, Color(0xFFDCEEE2)).border(1.5.dp, MColors.Green2, RoundedCornerShape(Corners.R)) else Modifier.clay(Corners.R, depth = 4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircleIcon(icon, if (selected) MColors.Green2 else MColors.Bg, if (selected) Color.White else MColors.Muted, size = 36.dp, iconSize = 19.dp)
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) MColors.Green else MColors.Muted, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, null, tint = MColors.Green2, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun VehicleSection(form: VehicleForm, errors: Map<String, String>, onChange: (VehicleForm) -> Unit, showErrors: Boolean) {
    FieldLabel("نوع وسیله", Icons.Outlined.DirectionsCar)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TypeCard("خودرو", Icons.Outlined.DirectionsCar, form.type == VehicleType.CAR, Modifier.weight(1f)) {
            onChange(form.withType(VehicleType.CAR))
        }
        TypeCard("موتورسیکلت", Icons.Outlined.TwoWheeler, form.type == VehicleType.MOTORCYCLE, Modifier.weight(1f)) {
            onChange(form.withType(VehicleType.MOTORCYCLE))
        }
    }
    Spacer(Modifier.height(14.dp))
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (form.type == VehicleType.CAR) {
                    CarPlateInput(form.carPlate, { onChange(form.copy(carPlate = it)) })
                } else {
                    MotoPlateInput(form.motoPlate, { onChange(form.copy(motoPlate = it)) })
                }
            }
            Text(
                "شماره پلاک (دولتی)",
                style = MaterialTheme.typography.labelMedium, color = MColors.Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (form.type == VehicleType.MOTORCYCLE) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagBadge("ردیف بالا: ۲ تا ۵ رقم", MColors.GreenLight, MColors.Green)
                    TagBadge("ردیف پایین: ۴ تا ۶ رقم", MColors.GreenLight, MColors.Green)
                }
            }
            val plateError = form.plateError()
            when {
                plateError == null -> FieldMessage(null, "پلاک معتبر است")
                showErrors || form.plateComplete -> FieldMessage(plateError, null)
            }
        }
    }

    val kinds = if (form.type == VehicleType.CAR) Suggestions.carKinds else Suggestions.motoKinds
    val models = if (form.type == VehicleType.CAR) Suggestions.carModels else Suggestions.motoModels
    val isCar = form.type == VehicleType.CAR
    val noun = if (isCar) "خودرو" else "موتورسیکلت"
    ComboField(
        form.kind, { onChange(form.copy(kind = it)) }, kinds,
        label = "نوع $noun", labelIcon = if (isCar) Icons.Outlined.DirectionsCar else Icons.Outlined.TwoWheeler,
        placeholder = "نوع $noun را بنویسید", error = errors["kind"],
    )
    ComboField(form.model, { onChange(form.copy(model = it)) }, models, label = "مدل $noun", placeholder = "مدل $noun را بنویسید", error = errors["model"])
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ComboField(
            form.year, { onChange(form.copy(year = it)) }, Suggestions.years(JalaliDate.today().year),
            label = "سال ساخت", numeric = NumericMode.Digits, maxLength = 4, placeholder = "مثلاً ۱۳۹۸",
            error = errors["year"], modifier = Modifier.weight(1f),
        )
        MField(
            form.odometer, { onChange(form.copy(odometer = it)) }, label = "کیلومتر فعلی", leadingIcon = Icons.Outlined.Speed,
            numeric = NumericMode.Grouped, maxLength = 7, placeholder = "۰", error = errors["odometer"], modifier = Modifier.weight(1f),
        )
    }
}
