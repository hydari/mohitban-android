package ir.mohitban.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.mohitban.app.ui.theme.Corners
import ir.mohitban.app.ui.theme.MColors

private fun norm(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "")

/**
 * A field that opens a full list to pick from (like the rank picker): large clay rows, the
 * current choice marked, and a search box for long lists.
 */
@Composable
fun SelectField(
    value: String,
    onPick: (String) -> Unit,
    options: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    placeholder: String = "انتخاب کنید",
    error: String? = null,
    title: String = label,
) {
    var open by remember { mutableStateOf(false) }
    MField(value, {}, modifier, label = label, leadingIcon = leadingIcon, placeholder = placeholder, error = error, onClick = { if (options.isNotEmpty()) open = true })
    if (open) ChoiceDialog(title, options, value, onDismiss = { open = false }) { onPick(it); open = false }
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) options else options.filter { norm(it).contains(norm(query)) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(Corners.R)).background(Clay.Surface).padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
            if (options.size > 10) {
                MField(query, { query = it }, Modifier.padding(top = 8.dp), placeholder = "جستجو…", leadingIcon = Icons.Outlined.Search, maxLength = 40)
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown) { o ->
                    val on = norm(o) == norm(current)
                    Row(
                        Modifier.fillMaxWidth().height(52.dp)
                            .then(if (on) Modifier.clayInset(12.dp, Color(0xFFDCEEE2)).border(1.5.dp, MColors.Green2, RoundedCornerShape(12.dp)) else Modifier.clay(12.dp, depth = 3.dp))
                            .clickable { onPick(o) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            o, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                            color = if (on) MColors.Green else MColors.Ink, modifier = Modifier.weight(1f),
                        )
                        if (on) Icon(Icons.Outlined.Check, null, tint = MColors.Green, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (shown.isEmpty()) Text("موردی پیدا نشد", style = MaterialTheme.typography.bodySmall, color = MColors.Muted, modifier = Modifier.padding(8.dp))
            CancelButton("بستن", Modifier.fillMaxWidth().padding(top = 12.dp), onClick = onDismiss)
        }
    }
}
