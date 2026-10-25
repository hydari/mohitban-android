package ir.mohitban.app.ui.components

import androidx.compose.foundation.background
import ir.mohitban.app.ui.theme.Corners
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.toLatinDigits
import ir.mohitban.app.ui.theme.MColors

enum class NumericMode { None, Digits, Grouped, Decimal }

/**
 * Shows Latin digits typed/stored in the field as Persian digits, optionally with
 * thousands separators, while keeping the cursor mapping correct.
 */
class PersianNumberTransformation(private val group: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (!group) return TransformedText(AnnotatedString(raw.fa()), OffsetMapping.Identity)
        val intLen = raw.indexOf('.').let { if (it < 0) raw.length else it }
        val out = StringBuilder()
        val o2t = IntArray(raw.length + 1)
        for (i in raw.indices) {
            if (i in 1 until intLen && (intLen - i) % 3 == 0) out.append(',')
            o2t[i] = out.length
            val c = raw[i]
            out.append(if (c == '.') '٫' else c)
        }
        o2t[raw.length] = out.length
        val t2o = IntArray(out.length + 1)
        var oi = 0
        for (t in 0..out.length) {
            while (oi < raw.length && o2t[oi + 1] <= t) oi++
            t2o[t] = oi
        }
        return TransformedText(
            AnnotatedString(out.toString().fa()),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, raw.length)]
                override fun transformedToOriginal(offset: Int) = t2o[offset.coerceIn(0, out.length)]
            },
        )
    }
}

/** Normalises user input for numeric fields. */
fun cleanNumeric(input: String, mode: NumericMode, maxLength: Int): String {
    val latin = input.toLatinDigits()
    val cleaned = when (mode) {
        NumericMode.None -> return input.take(maxLength)
        NumericMode.Digits, NumericMode.Grouped -> latin.filter { it.isDigit() }
        NumericMode.Decimal -> {
            val f = latin.replace('/', '.').filter { it.isDigit() || it == '.' }
            val dot = f.indexOf('.')
            if (dot < 0) f else f.substring(0, dot + 1) + f.substring(dot + 1).replace(".", "").take(2)
        }
    }
    return cleaned.take(maxLength)
}

@Composable
fun FieldLabel(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = MColors.Muted, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MColors.Muted)
        if (trailing != null) trailing()
    }
}

/**
 * The white, rounded, softly shadowed input field of the reference design.
 * Pass [onClick] to turn it into a read-only selector (date / dropdown).
 */
@Composable
fun MField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelIcon: ImageVector? = null,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    numeric: NumericMode = NumericMode.None,
    maxLength: Int = 120,
    error: String? = null,
    success: String? = null,
    onClick: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    keyboardType: KeyboardType? = null,
    highlight: Boolean = false,
    big: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Corners.R)
    val borderColor = when {
        error != null -> MColors.Error
        focused || highlight -> MColors.Green2
        success != null -> Color(0xFF7FC79A)
        else -> MColors.Line
    }
    Column(modifier) {
        if (label != null) FieldLabel(label, labelIcon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (big) 56.dp else 50.dp)
                .shadow(3.dp, shape, clip = false, ambientColor = Color(0x14143C1E), spotColor = Color(0x1F143C1E))
                .background(Color.White, shape)
                .border(if (focused || highlight || error != null) 1.5.dp else 1.dp, borderColor, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leadingIcon != null) CircleIcon(leadingIcon, MColors.Green, Color.White, size = 30.dp, iconSize = 17.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MColors.Ink,
                    fontSize = if (big) 17.sp else 15.sp,
                )
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = MColors.Hint, fontWeight = FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onClick != null) {
                    val shown = when (numeric) {
                        NumericMode.None -> value
                        else -> PersianNumberTransformation(numeric == NumericMode.Grouped || numeric == NumericMode.Decimal)
                            .filter(AnnotatedString(value)).text.text
                    }
                    Text(shown, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = { onValueChange(cleanNumeric(it, numeric, maxLength)) },
                        singleLine = true,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(MColors.Green),
                        interactionSource = interaction,
                        visualTransformation = when (numeric) {
                            NumericMode.None -> VisualTransformation.None
                            NumericMode.Digits -> PersianNumberTransformation(false)
                            NumericMode.Grouped, NumericMode.Decimal -> PersianNumberTransformation(true)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType ?: when (numeric) {
                                NumericMode.None -> KeyboardType.Text
                                NumericMode.Decimal -> KeyboardType.Decimal
                                else -> KeyboardType.Number
                            },
                            imeAction = imeAction,
                        ),
                        keyboardActions = KeyboardActions.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (trailing != null) trailing()
        }
        FieldMessage(error = error, success = success)
    }
}

@Composable
fun FieldMessage(error: String?, success: String?) {
    when {
        error != null -> Row(
            Modifier.padding(top = 6.dp, start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MColors.Error, modifier = Modifier.size(15.dp))
            Text(error, style = MaterialTheme.typography.labelMedium, color = MColors.Error)
        }
        success != null -> Row(
            Modifier.padding(top = 6.dp, start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Outlined.CheckCircle, null, tint = MColors.Green2, modifier = Modifier.size(16.dp))
            Text(success, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Green2)
        }
    }
}

/**
 * Editable field with a suggestions menu (chevron on the left like the reference).
 * The user may pick a suggestion or type anything.
 */
@Composable
fun ComboField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelIcon: ImageVector? = null,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    error: String? = null,
    numeric: NumericMode = NumericMode.None,
    maxLength: Int = 80,
    readOnly: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        MField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            labelIcon = labelIcon,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            error = error,
            numeric = numeric,
            maxLength = maxLength,
            onClick = if (readOnly) ({ open = true }) else null,
            trailing = {
                Icon(
                    Icons.Outlined.ExpandMore, "انتخاب",
                    tint = Color(0xFF8A928D),
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { open = true },
                )
            },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(Color.White),
        ) {
            // while typing, narrow the list to matching options (all of them once a value is chosen)
            val typed = value.trim()
            val shown = if (!readOnly && typed.isNotEmpty() && options.none { it == typed }) {
                options.filter { it.contains(typed) }.ifEmpty { options }
            } else options
            shown.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(if (numeric != NumericMode.None) opt.fa() else opt, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { onValueChange(opt); open = false },
                )
            }
        }
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))
