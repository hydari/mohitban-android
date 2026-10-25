package ir.mohitban.app.ui.profile

import androidx.compose.foundation.Image
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

@Composable
fun PersonalSection(form: ProfileForm, errors: Map<String, String>, onChange: (ProfileForm) -> Unit) {
    val picker = rememberImagePicker("profile", onImported = { onChange(form.copy(photoPath = it)) })
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
                else Icon(Icons.Outlined.Person, null, tint = MColors.Green2, modifier = Modifier.size(42.dp))
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
        Text("عکس پروفایل (اختیاری)", style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp))
    }
    MField(form.fullName, { onChange(form.copy(fullName = it)) }, label = "نام و نام خانوادگی", leadingIcon = Icons.Outlined.Person, placeholder = "علی رضایی", error = errors["fullName"])
    MField(form.fatherName, { onChange(form.copy(fatherName = it)) }, label = "نام پدر", leadingIcon = Icons.Outlined.Person, placeholder = "حسن", error = errors["fatherName"])
    val idOk = form.nationalId.length == 10 && errors["nationalId"] == null && ir.mohitban.app.core.text.Validators.nationalId(form.nationalId) == null
    MField(
        form.nationalId, { onChange(form.copy(nationalId = it)) }, label = "کد ملی", leadingIcon = Icons.Outlined.Badge,
        numeric = NumericMode.Digits, maxLength = 10, placeholder = "۱۰ رقم", error = errors["nationalId"],
        success = if (idOk) "کد ملی معتبر است" else null,
    )
    MField(
        form.mobile, { onChange(form.copy(mobile = it)) }, label = "شماره همراه", leadingIcon = Icons.Outlined.PhoneAndroid,
        numeric = NumericMode.Digits, maxLength = 11, placeholder = "۰۹۱۲۳۴۵۶۷۸۹", error = errors["mobile"],
    )
    ComboField(form.rank, { onChange(form.copy(rank = it)) }, Suggestions.ranks, label = "درجه / سمت", leadingIcon = Icons.Outlined.Park, placeholder = "محیط‌بان", error = errors["rank"])
}

@Composable
fun LocationSection(form: ProfileForm, errors: Map<String, String>, onChange: (ProfileForm) -> Unit) {
    ComboField(
        form.province, { onChange(form.copy(province = it)) }, Suggestions.provinces,
        label = "اداره کل استان", leadingIcon = Icons.Outlined.LocationOn, error = errors["province"], readOnly = true,
    )
    MField(form.city, { onChange(form.copy(city = it)) }, label = "شهرستان", leadingIcon = Icons.Outlined.LocationOn, placeholder = "کلیبر", error = errors["city"])
    MField(form.protectedArea, { onChange(form.copy(protectedArea = it)) }, label = "منطقه حفاظت‌شده", leadingIcon = Icons.Outlined.Park, placeholder = "ارسباران", error = errors["protectedArea"])
    MField(form.station, { onChange(form.copy(station = it)) }, label = "پاسگاه محیط‌بانی", leadingIcon = Icons.Outlined.Home, placeholder = "مکیدی", error = errors["station"], imeAction = androidx.compose.ui.text.input.ImeAction.Done)
}

@Composable
private fun TypeCard(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(58.dp)
            .clip(shape)
            .background(if (selected) MColors.GreenTint else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) MColors.Green2 else MColors.Line, shape)
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
    val picker = rememberImagePicker("vehicle", onImported = { onChange(form.copy(photoPath = it)) })
    val photo by rememberStoredImage(form.photoPath, 700)

    FieldLabel("نوع وسیله", Icons.Outlined.DirectionsCar)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TypeCard("خودرو", Icons.Outlined.DirectionsCar, form.type == VehicleType.CAR, Modifier.weight(1f)) {
            onChange(form.copy(type = VehicleType.CAR))
        }
        TypeCard("موتورسیکلت", Icons.Outlined.TwoWheeler, form.type == VehicleType.MOTORCYCLE, Modifier.weight(1f)) {
            onChange(form.copy(type = VehicleType.MOTORCYCLE))
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
    ComboField(form.kind, { onChange(form.copy(kind = it)) }, kinds, label = "نوع خودرو", labelIcon = Icons.Outlined.DirectionsCar, placeholder = kinds.first(), error = errors["kind"])
    ComboField(form.model, { onChange(form.copy(model = it)) }, models, label = "مدل", placeholder = models.first(), error = errors["model"])
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ComboField(
            form.year, { onChange(form.copy(year = it)) }, Suggestions.years(JalaliDate.today().year),
            label = "سال ساخت", numeric = NumericMode.Digits, maxLength = 4, placeholder = "۱۳۹۸",
            error = errors["year"], modifier = Modifier.weight(1f),
        )
        MField(
            form.odometer, { onChange(form.copy(odometer = it)) }, label = "کیلومتر فعلی", leadingIcon = Icons.Outlined.Speed,
            numeric = NumericMode.Grouped, maxLength = 7, placeholder = "۰", error = errors["odometer"], modifier = Modifier.weight(1f),
        )
    }
    FieldLabel("عکس وسیله (اختیاری)")
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MColors.GreenTint)
            .border(1.5.dp, Color(0xFFBFD3C5), RoundedCornerShape(16.dp))
            .clickable { picker.openSheet() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val p = photo
        if (p != null) {
            Image(p, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize())
        } else {
            Row(Modifier.weight(1f).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIcon(Icons.Outlined.PhotoCamera, MColors.Green, Color.White, size = 44.dp)
                Column {
                    Text("افزودن عکس وسیله", style = MaterialTheme.typography.titleSmall, color = MColors.Green)
                    Text("در کارت خودرو و فرم سوخت نمایش داده می‌شود", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                }
            }
        }
    }
    if (form.photoPath != null) {
        Text(
            "حذف عکس",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MColors.Error,
            modifier = Modifier.padding(6.dp).clickable { onChange(form.copy(photoPath = null)) },
        )
    }
}
