package ir.mohitban.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.mohitban.app.R
import ir.mohitban.app.domain.model.Grades
import ir.mohitban.app.ui.theme.Corners
import ir.mohitban.app.ui.theme.MColors

private val RANK_IMAGES = intArrayOf(
    R.drawable.rank_01, R.drawable.rank_02, R.drawable.rank_03, R.drawable.rank_04, R.drawable.rank_05, R.drawable.rank_06,
    R.drawable.rank_07, R.drawable.rank_08, R.drawable.rank_09, R.drawable.rank_10, R.drawable.rank_11, R.drawable.rank_12,
)

/** The insignia picture of a rank, or null for an unknown rank. */
fun rankImage(grade: String): Int? = Grades.indexOf(grade).takeIf { it >= 0 }?.let { RANK_IMAGES[it] }

/** The shoulder insignia of [grade] at [height], with a thin gold edge and a soft shadow. */
@Composable
fun RankInsignia(grade: String, height: Dp, modifier: Modifier = Modifier) {
    val img = rankImage(grade) ?: return
    Image(
        painterResource(img), grade, contentScale = ContentScale.FillHeight,
        modifier = modifier.height(height).shadow(3.dp, RoundedCornerShape(3.dp)).clip(RoundedCornerShape(3.dp))
            .border(0.8.dp, Color(0xFFE2B54A).copy(alpha = 0.7f), RoundedCornerShape(3.dp)),
    )
}

/** Picks the ranger's rank from the twelve insignia (lowest first). */
@Composable
fun GradePickerDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(Corners.R)).background(Clay.Surface).padding(16.dp)) {
            Text("درجه‌ی خود را انتخاب کنید", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Grades.ALL.forEach { g ->
                    val on = Grades.indexOf(current) == Grades.indexOf(g)
                    Row(
                        Modifier.fillMaxWidth().height(60.dp)
                            .then(if (on) Modifier.clayInset(12.dp, Color(0xFFDCEEE2)).border(1.5.dp, MColors.Green2, RoundedCornerShape(12.dp)) else Modifier.clay(12.dp, depth = 3.dp))
                            .clickable { onPick(g) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) { RankInsignia(g, 36.dp) }
                        Text(
                            g, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                            color = if (on) MColors.Green else MColors.Ink, textAlign = TextAlign.Start, modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("بستن", color = MColors.Muted) }
        }
    }
}
