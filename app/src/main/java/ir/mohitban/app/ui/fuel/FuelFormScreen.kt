package ir.mohitban.app.ui.fuel

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mohitban.app.container
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.rial
import ir.mohitban.app.domain.model.FuelType
import ir.mohitban.app.domain.model.VehicleType
import ir.mohitban.app.ui.common.Share
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.TagBadge
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.AnimatedCheck
import ir.mohitban.app.ui.components.GreenButton
import ir.mohitban.app.ui.components.FieldLabel
import ir.mohitban.app.ui.components.FieldMessage
import ir.mohitban.app.ui.components.JalaliDatePickerDialog
import ir.mohitban.app.ui.components.MField
import ir.mohitban.app.ui.components.NumericMode
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.PlateView
import ir.mohitban.app.ui.components.SignatureField
import ir.mohitban.app.ui.components.OutlinePill
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.components.rememberImagePicker
import ir.mohitban.app.ui.components.rememberSignatureFieldState
import ir.mohitban.app.ui.components.rememberStoredImage
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import ir.mohitban.app.ui.components.clay
import ir.mohitban.app.ui.components.clayInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelFormScreen(
    entryId: Long,
    newVehicleId: Long?,
    onVehicleConsumed: () -> Unit,
    onAddVehicle: () -> Unit,
    croppedReceipt: String?,
    onReceiptConsumed: () -> Unit,
    onCropReceipt: (File) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val vm = appViewModel(key = "fuel_$entryId") { c, _ ->
        FuelFormViewModel(entryId, c.profileRepository, c.settingsRepository, c.fuelRepository, c.files)
    }
    // a fuel record needs a vehicle: the first time, register one before anything else
    if (vm.loaded && vm.vehicles.isEmpty() && newVehicleId == null) {
        AlertDialog(
            onDismissRequest = onBack,
            containerColor = ir.mohitban.app.ui.components.Clay.Surface,
            icon = { CircleIcon(Icons.Outlined.DirectionsCar, MColors.Green, Color.White, size = 56.dp, iconSize = 28.dp) },
            title = { Text("ابتدا وسیله‌ی نقلیه را ثبت کنید", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "برای ثبت سند هزینه سوخت، اول خودرو یا موتورسیکلت سازمانی خود را با پلاک ثبت کنید.",
                    style = MaterialTheme.typography.bodyMedium, color = MColors.Muted,
                )
            },
            confirmButton = { GreenButton("ثبت خودرو یا موتورسیکلت", onClick = onAddVehicle, modifier = Modifier.fillMaxWidth().height(50.dp)) },
            dismissButton = { TextButton(onBack) { Text("بازگشت") } },
        )
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val driverSig = rememberSignatureFieldState()
    val passengerSig = rememberSignatureFieldState()
    var vehicleSheet by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }

    LaunchedEffect(croppedReceipt) {
        if (croppedReceipt != null) {
            vm.onReceipt(croppedReceipt)
            onReceiptConsumed()
        }
    }
    // show saved signatures while editing
    val oldDriver by rememberStoredImage(vm.existingDriverSignature, 600)
    val oldPassenger by rememberStoredImage(vm.existingPassengerSignature, 600)
    LaunchedEffect(oldDriver) { if (oldDriver != null && !driverSig.changed) driverSig.existing = oldDriver }
    LaunchedEffect(oldPassenger) { if (oldPassenger != null && !passengerSig.changed) passengerSig.existing = oldPassenger }
    LaunchedEffect(newVehicleId) {
        if (newVehicleId != null) {
            vm.refreshVehicles(newVehicleId.takeIf { it > 0 })
            onVehicleConsumed()
        }
    }

    val receiptPicker = rememberImagePicker("receipt", onImported = { vm.onReceipt(it) }, rawCameraFile = onCropReceipt)

    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar(if (vm.isEdit) "ویرایش هزینه سوخت" else "ثبت هزینه سوخت", onBack = onBack)
        if (!vm.loaded) return@Column
        Column(
            Modifier
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            // ---------- 1. vehicle (chosen first) ----------
            val vehicle = vm.vehicle
            val isMoto = vehicle?.type == VehicleType.MOTORCYCLE
            FieldLabel(if (isMoto) "موتورسیکلت" else "خودرو", if (isMoto) Icons.Outlined.TwoWheeler else Icons.Outlined.DirectionsCar) {
                if (vm.vehicles.size > 1) TagBadge("برای تغییر لمس کنید", MColors.GreenLight, MColors.Green)
            }
            AppCard(Modifier.fillMaxWidth(), onClick = { vehicleSheet = true }) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(vehicle?.title ?: "انتخاب وسیله", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Outlined.ExpandMore, null, tint = MColors.Muted, modifier = Modifier.size(20.dp))
                        }
                        val sub = listOfNotNull(vehicle?.kind?.takeIf { it.isNotBlank() }, vehicle?.year?.takeIf { it.isNotBlank() }?.let { "مدل ${it.fa()}" })
                        if (sub.isNotEmpty()) Text(sub.joinToString(" | "), style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                    // the plate sits on the left side of the card
                    vehicle?.let { Box(Modifier.padding(start = 8.dp, end = 4.dp)) { PlateView(it.plate, height = 36.dp) } }
                }
            }


            FieldMessage(vm.errors["vehicle"], null)

            // ---------- 2. date + odometer ----------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MField(
                    value = vm.date.format(), onValueChange = {}, numeric = NumericMode.Digits,
                    label = "تاریخ سوخت‌گیری", leadingIcon = Icons.Outlined.CalendarMonth,
                    onClick = { datePicker = true }, modifier = Modifier.weight(1.15f),
                )
                Column(Modifier.weight(1f)) {
                    MField(
                        value = vm.odometer, onValueChange = { vm.odometer = it }, numeric = NumericMode.Grouped, maxLength = 7,
                        label = "کیلومتر فعلی", leadingIcon = Icons.Outlined.Speed, placeholder = "۰",
                        error = vm.errors["odometer"],
                    )
                    vm.lastOdometer?.let {
                        Text(
                            "آخرین ثبت‌شده: ${it.rial()}",
                            style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
                            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                        )
                    }
                }
            }

            // ---------- fuel type ----------
            FieldLabel("نوع سوخت")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (vm.cngAllowed) {
                    FuelTypeCard(
                        FuelType.CNG, Icons.Outlined.LocalFireDepartment, "${vm.rates.cng.rial()} ریال/کیلو",
                        vm.fuelType == FuelType.CNG, MColors.Green2, Color.White, Modifier.weight(1f),
                    ) { vm.onFuelType(FuelType.CNG) }
                }
                FuelTypeCard(
                    FuelType.GASOLINE, Icons.Outlined.WaterDrop, "${vm.rates.gasoline.size.fa()} نرخ مصوب",
                    vm.fuelType == FuelType.GASOLINE, MColors.AmberBg, MColors.Amber, Modifier.weight(1f),
                ) { vm.onFuelType(FuelType.GASOLINE) }
            }
            if (!vm.cngAllowed) {
                Text(
                    "برای موتورسیکلت فقط بنزین قابل ثبت است (گاز CNG ویژه خودرو است).",
                    style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                )
            }
            if (vm.fuelType == FuelType.GASOLINE) {
                FieldLabel("نرخ هر لیتر (ریال)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.rates.gasoline.forEachIndexed { i, r ->
                        RateChip(r.rial(), vm.gasolineRateIndex == i, Modifier.weight(1f)) { vm.gasolineRateIndex = i }
                    }
                }
            }
            MField(
                value = vm.amount, onValueChange = { vm.amount = it }, numeric = NumericMode.Decimal, maxLength = 7,
                label = "مقدار سوخت (${vm.fuelType.unit})",
                leadingIcon = if (vm.fuelType == FuelType.CNG) Icons.Outlined.LocalFireDepartment else Icons.Outlined.WaterDrop,
                trailing = { Text(vm.fuelType.unit, style = MaterialTheme.typography.labelMedium, color = MColors.Muted) },
                placeholder = "۰", error = vm.errors["amount"], highlight = vm.amount.isNotEmpty(), big = true,
            )

            // ---------- total ----------
            AppCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Outlined.Payments, MColors.GreenLight, MColors.Green, size = 46.dp, iconSize = 24.dp)
                    Text("مبلغ کل", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(vm.total.rial(), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), color = MColors.Green)
                            Spacer(Modifier.width(4.dp))
                            Text("ریال", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Green, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                }
            }

            // ---------- receipt ----------
            FieldLabel("عکس رسید", Icons.Outlined.Receipt) {
                if (vm.receiptRequired) TagBadge("الزامی برای بنزین", MColors.ErrorBg, MColors.Error)
                else TagBadge("برای گاز اختیاری", MColors.Bg, MColors.Muted)
            }
            val receipt by rememberStoredImage(vm.receiptPath, 500)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 120.dp, height = 88.dp)
                        .clip(RoundedCornerShape(Corners.R))
                        .background(Color(0xFFE4E3DE))
                        .clickable { receiptPicker.openSheet() },
                    contentAlignment = Alignment.Center,
                ) {
                    val r = receipt
                    if (r != null) {
                        Image(r, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        CircleIcon(Icons.Outlined.Check, MColors.Green2, Color.White, size = 24.dp, iconSize = 14.dp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), border = Color.White)
                    } else {
                        Icon(Icons.Outlined.Receipt, null, tint = Color(0xFF9AA29C), modifier = Modifier.size(34.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    if (vm.receiptPath != null) {
                        Text("رسید کارتخوان ثبت شد", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
                        Text(
                            "حذف رسید", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Error,
                            modifier = Modifier.padding(top = 4.dp).clickable { vm.removeReceipt() },
                        )
                    } else {
                        Text("از رسید کارتخوان عکس بگیرید", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
                        Text("لبه‌ها خودکار تشخیص داده می‌شود", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Green)
                    }
                }
                Box(Modifier.clip(CircleShape).clickable { receiptPicker.openSheet() }) {
                    CircleIcon(Icons.Outlined.PhotoCamera, MColors.Green, Color.White, size = 54.dp, iconSize = 26.dp)
                }
            }
            FieldMessage(vm.errors["receipt"], null)

            // ---------- signatures ----------
            FieldLabel("امضای دیجیتال")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SignatureField(driverSig, "امضای راننده", Modifier.weight(1f), required = true, error = vm.errors["driver"])
                SignatureField(passengerSig, "امضای سرنشین (اختیاری)", Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            PillButton(
                if (vm.isEdit) "ذخیره تغییرات" else "ثبت و ذخیره فرم",
                onClick = { vm.requestSave(driverEmpty = driverSig.isEmpty) },
                icon = Icons.AutoMirrored.Outlined.Send,
            )
            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (vehicleSheet) {
        ModalBottomSheet(onDismissRequest = { vehicleSheet = false }, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Text("انتخاب خودرو یا موتورسیکلت", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                vm.vehicles.forEach { v ->
                    val selected = v.id == vm.vehicle?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(Corners.R))
                            .background(if (selected) MColors.GreenTint else MColors.Bg)
                            .clickable { vm.selectVehicle(v.id); vehicleSheet = false }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircleIcon(
                            if (v.type == VehicleType.CAR) Icons.Outlined.DirectionsCar else Icons.Outlined.TwoWheeler,
                            if (selected) MColors.Green2 else Color.White, if (selected) Color.White else MColors.Green, size = 38.dp, iconSize = 19.dp,
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(v.title, style = MaterialTheme.typography.titleSmall)
                            Text(v.type.title, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                        }
                        PlateView(v.plate, height = if (v.type == VehicleType.CAR) 28.dp else 32.dp)
                    }
                }
                OutlinePill(
                    "افزودن خودرو یا موتورسیکلت جدید", { vehicleSheet = false; onAddVehicle() },
                    Modifier.fillMaxWidth().padding(top = 10.dp), Icons.Outlined.Add,
                )
            }
        }
    }

    if (datePicker) {
        JalaliDatePickerDialog(vm.date, onDismiss = { datePicker = false }, onConfirm = { vm.date = it; datePicker = false })
    }

    if (vm.showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!vm.saving) vm.showConfirm = false },
            containerColor = Color.White,
            title = { Text("بررسی و تأیید نهایی", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    val lines = vm.summaryLines()
                    lines.forEachIndexed { i, (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, color = MColors.Muted, modifier = Modifier.weight(1f))
                            Text(
                                v,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (i == lines.lastIndex) MColors.Green else MColors.Ink,
                            )
                        }
                        if (i < lines.lastIndex) HorizontalDivider(color = MColors.Line)
                    }
                    Text(
                        "پس از ثبت، فرم رسمی (PDF و تصویر) ساخته می‌شود.",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {
                GreenButton(
                    if (vm.saving) "در حال ثبت…" else "ثبت نهایی",
                    enabled = !vm.saving,
                    onClick = { vm.save(driverSig.bitmap, passengerSig.bitmap, passengerCleared = passengerSig.isEmpty) },
                )
            },
            dismissButton = { TextButton(onClick = { vm.showConfirm = false }) { Text("ویرایش", color = MColors.Muted) } },
        )
    }

    vm.savedEntry?.let { saved ->
        val container = context.container
        AlertDialog(
            onDismissRequest = onDone,
            containerColor = Color.White,
            icon = { AnimatedCheck(size = 72.dp) },
            title = { Text("فرم هزینه سوخت ثبت شد", style = MaterialTheme.typography.titleMedium) },
            text = { Text("فرم رسمی را می‌توانید به صورت PDF یا تصویر برای اداره ارسال کنید.", style = MaterialTheme.typography.bodyMedium, color = MColors.Muted) },
            confirmButton = {
                Column(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinePill("ارسال PDF", {
                            scope.launch {
                                val f = withContext(Dispatchers.IO) { container.fuelForm.createPdf(saved) }
                                Share.file(context, f, "application/pdf")
                            }
                        }, Modifier.weight(1f), Icons.Outlined.PictureAsPdf)
                        OutlinePill("ارسال تصویر", {
                            scope.launch {
                                val f = withContext(Dispatchers.IO) { container.fuelForm.createPng(saved) }
                                Share.file(context, f, "image/png")
                            }
                        }, Modifier.weight(1f), Icons.Outlined.ImageIcon)
                    }
                    GreenButton("بازگشت", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp))
                }
            },
        )
    }
}

@Composable
private fun ShareRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, tint = MColors.Green, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = MColors.Green, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FuelTypeCard(
    type: FuelType,
    icon: ImageVector,
    subtitle: String,
    selected: Boolean,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Corners.R)
    Box(
        modifier
            .then(if (selected) Modifier.clayInset(Corners.R, Color(0xFFDCEEE2)).border(1.5.dp, MColors.Green2, RoundedCornerShape(Corners.R)) else Modifier.clay(Corners.R, depth = 6.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(icon, if (selected) MColors.Green2 else iconBg, if (selected) Color.White else iconTint, size = 42.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Text(type.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) MColors.Green2 else Color.White)
                .border(1.5.dp, if (selected) MColors.Green2 else Color(0xFFC5CCC7), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
    }
}

@Composable
private fun RateChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Corners.R)
    Column(
        modifier
            .height(54.dp)
            .clip(shape)
            .background(if (selected) MColors.Green else Color.White)
            .border(1.5.dp, if (selected) MColors.Green else MColors.Line, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (selected) Color.White else MColors.Ink)
        Text("ریال", style = MaterialTheme.typography.labelSmall, color = if (selected) Color(0xFFCFE3C7) else MColors.Muted)
    }
}
