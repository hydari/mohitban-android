package ir.mohitban.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.toLatinDigits
import ir.mohitban.app.domain.model.Plate
import ir.mohitban.app.domain.model.PlateRules
import ir.mohitban.app.ui.theme.AppFonts
import ir.mohitban.app.ui.theme.MColors

/* ============================ Display ============================ */

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

@Composable
private fun FlagStrip(width: Dp, small: Boolean) {
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(MColors.PlateBlue)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val w = width * 0.55f
        val h = width * 0.14f
        Box(Modifier.size(w, h).background(MColors.FlagGreen))
        Box(Modifier.size(w, h).background(Color.White))
        Box(Modifier.size(w, h).background(MColors.FlagRed))
        Spacer(Modifier.height(if (small) 1.dp else 2.dp))
        Text(
            "I.R.\nIRAN", color = Color.White, fontSize = (width * 0.26f).sp(), lineHeight = (width * 0.29f).sp(),
            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, softWrap = false,
        )
    }
}

private fun plateStyle(size: TextUnit) = TextStyle(
    color = Color.White,
    fontWeight = FontWeight.Black,
    fontSize = size,
    fontFamily = AppFonts.family(),
    textAlign = TextAlign.Center,
)

/** Red government car plate. [height] controls the whole size. */
@Composable
fun RedCarPlate(plate: Plate.Car, height: Dp = 46.dp, modifier: Modifier = Modifier) {
    val small = height < 40.dp
    Box(modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier
                .height(height)
                .width(IntrinsicSize.Max)
                .clip(RoundedCornerShape(height * 0.14f))
                .background(MColors.PlateRed)
                .border(height * 0.05f, Color(0xFF1B1B1B), RoundedCornerShape(height * 0.14f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagStrip(height * if (small) 0.38f else 0.4f, small)
            Row(
                Modifier.padding(horizontal = height * 0.2f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(height * 0.16f),
            ) {
                Text(plate.first.fa(), style = plateStyle((height * 0.5f).sp()))
                Text(plate.letter, style = plateStyle((height * 0.4f).sp()))
                Text(plate.middle.fa(), style = plateStyle((height * 0.5f).sp()))
            }
            Box(Modifier.width(height * 0.045f).fillMaxHeight().background(Color.White))
            Column(
                Modifier.padding(horizontal = height * 0.14f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("ایران", style = plateStyle((height * 0.17f).sp()).copy(fontWeight = FontWeight.Bold), lineHeight = (height * 0.2f).sp())
                Text(plate.iranCode.fa(), style = plateStyle((height * 0.4f).sp()), lineHeight = (height * 0.44f).sp())
            }
        }
    }
    }
}

/** Red government motorcycle plate: two rows. [height] is the whole plate height. */
@Composable
fun RedMotoPlate(plate: Plate.Motorcycle, height: Dp = 70.dp, modifier: Modifier = Modifier) {
    Box(modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier
                .height(height)
                .width(IntrinsicSize.Max)
                .clip(RoundedCornerShape(height * 0.12f))
                .background(MColors.PlateRed)
                .border(height * 0.035f, Color(0xFF1B1B1B), RoundedCornerShape(height * 0.12f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagStrip(height * 0.24f, small = true)
            Column(
                Modifier.padding(horizontal = height * 0.2f).widthIn(min = height * 1.1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(plate.top.fa(), style = plateStyle((height * 0.3f).sp()).copy(letterSpacing = (height * 0.03f).sp()))
                Box(Modifier.fillMaxWidth().height(height * 0.03f).background(Color.White))
                Text(plate.bottom.fa(), style = plateStyle((height * 0.33f).sp()).copy(letterSpacing = (height * 0.04f).sp()))
            }
        }
    }
    }
}

@Composable
fun PlateView(plate: Plate, height: Dp = 46.dp, modifier: Modifier = Modifier) {
    when (plate) {
        is Plate.Car -> RedCarPlate(plate, height, modifier)
        is Plate.Motorcycle -> RedMotoPlate(plate, height * 1.5f, modifier)
    }
}

/* ============================ Input ============================ */

@Composable
private fun PlateDigitBox(
    value: String,
    onValue: (String) -> Unit,
    maxLen: Int,
    fontSize: TextUnit,
    minWidth: Dp,
    requester: FocusRequester,
    next: FocusRequester?,
) {
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val d = raw.toLatinDigits().filter { it.isDigit() }.take(maxLen)
            onValue(d)
            if (d.length == maxLen) next?.requestFocus()
        },
        singleLine = true,
        textStyle = plateStyle(fontSize),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = if (next != null) ImeAction.Next else ImeAction.Done),
        visualTransformation = PersianNumberTransformation(false),
        modifier = Modifier
            .focusRequester(requester)
            .widthIn(min = minWidth)
            .width(IntrinsicSize.Min)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    Color.White.copy(alpha = 0.85f), Offset(0f, y), Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text("-".repeat(maxLen), style = plateStyle(fontSize).copy(color = Color.White.copy(alpha = 0.45f)))
                inner()
            }
        },
    )
}

/** Editable red car plate: [12] [حرف ▾] [345] | ایران [67] */
@Composable
fun CarPlateInput(plate: Plate.Car, onChange: (Plate.Car) -> Unit, modifier: Modifier = Modifier) {
    val r1 = remember { FocusRequester() }
    val r2 = remember { FocusRequester() }
    val r3 = remember { FocusRequester() }
    val h = 64.dp
    Box(modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier
                .height(h)
                .width(IntrinsicSize.Max)
                .clip(RoundedCornerShape(9.dp))
                .background(MColors.PlateRed)
                .border(3.dp, Color(0xFF1B1B1B), RoundedCornerShape(9.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagStrip(22.dp, small = false)
            Row(
                Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlateDigitBox(plate.first, { onChange(plate.copy(first = it, letter = "الف")) }, 2, (h * 0.44f).sp(), 34.dp, r1, r2)
                // government red plates always carry the letter «الف»
                Text("الف", style = plateStyle((h * 0.34f).sp()))
                PlateDigitBox(plate.middle, { onChange(plate.copy(middle = it)) }, 3, (h * 0.44f).sp(), 50.dp, r2, r3)
            }
            Box(Modifier.width(3.dp).fillMaxHeight().background(Color.White))
            Column(Modifier.padding(horizontal = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ایران", style = plateStyle(11.dp.sp()).copy(fontWeight = FontWeight.Bold))
                PlateDigitBox(plate.iranCode, { onChange(plate.copy(iranCode = it)) }, 2, (h * 0.34f).sp(), 30.dp, r3, null)
            }
        }
    }
    }
}

/** Editable red motorcycle plate; rows accept 2–5 and 4–6 digits. */
@Composable
fun MotoPlateInput(plate: Plate.Motorcycle, onChange: (Plate.Motorcycle) -> Unit, modifier: Modifier = Modifier) {
    val r1 = remember { FocusRequester() }
    val r2 = remember { FocusRequester() }
    Box(modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier
                .height(104.dp)
                .width(IntrinsicSize.Max)
                .clip(RoundedCornerShape(11.dp))
                .background(MColors.PlateRed)
                .border(3.dp, Color(0xFF1B1B1B), RoundedCornerShape(11.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagStrip(22.dp, small = true)
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 6.dp).widthIn(min = 130.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PlateDigitBox(plate.top, { onChange(plate.copy(top = it)) }, PlateRules.MOTO_TOP_MAX, 26.dp.sp(), 70.dp, r1, null)
                Box(Modifier.fillMaxWidth().height(2.5.dp).background(Color.White))
                PlateDigitBox(plate.bottom, { onChange(plate.copy(bottom = it)) }, PlateRules.MOTO_BOTTOM_MAX, 28.dp.sp(), 100.dp, r2, null)
            }
        }
    }
    }
}
