package ir.mohitban.app.ui.report

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ir.mohitban.app.ui.components.BottomBar
import ir.mohitban.app.ui.components.MainTab
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.components.clay
import ir.mohitban.app.ui.components.clayInset
import ir.mohitban.app.ui.records.RecordsScreen
import ir.mohitban.app.ui.theme.MColors
import kotlin.math.roundToInt

/** Which part of «گزارش‌ها» is open; set before opening the tab (e.g. after saving a fuel record). */
object ReportsHubTab {
    var tab by androidx.compose.runtime.mutableIntStateOf(0)
}

/**
 * «گزارش‌ها» in one place: the fuel records of the season and the quarterly report, as two
 * clearly named tabs with a sliding indicator; the content slides in from the tab's side.
 */
@Composable
fun ReportsHub(onTab: (MainTab) -> Unit, onEdit: (Long) -> Unit) {
    val tab = ReportsHubTab.tab
    Box(Modifier.fillMaxSize().background(MColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("گزارش‌ها", onBack = null)
            HubTabs(tab) { ReportsHubTab.tab = it }
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // right-to-left reading: the second tab comes in from the left
                    val dir = if (targetState > initialState) -1 else 1
                    (slideInHorizontally(tween(300)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 5 * dir } + fadeOut(tween(160)))
                },
                label = "hub",
                modifier = Modifier.fillMaxSize(),
            ) { t ->
                if (t == 0) RecordsScreen(onEdit = onEdit, onTab = onTab, onBack = null, chrome = false)
                else ReportScreen(onTab = onTab, onBack = null, chrome = false)
            }
        }
        BottomBar(MainTab.REPORTS, onTab, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun HubTabs(selected: Int, onSelect: (Int) -> Unit) {
    val slide = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) { slide.animateTo(selected.toFloat(), spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(50.dp).clay(14.dp, depth = 5.dp).padding(4.dp),
    ) {
        val w = maxWidth / 2
        Box(
            Modifier.offset { IntOffset((w.toPx() * slide.value).roundToInt(), 0) }.width(w).fillMaxHeight()
                .clayInset(11.dp, Color(0xFFDCEEE2)),
        )
        Row(Modifier.fillMaxSize()) {
            listOf(
                Triple("سوابق سوخت", Icons.AutoMirrored.Outlined.FormatListBulleted, Color(0xFF2F7FC1)),
                Triple("گزارش فصلی", Icons.Outlined.Assessment, Color(0xFFE08A1E)),
            ).forEachIndexed { i, (title, icon, color) ->
                val on = i == selected
                Row(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(icon, null, tint = if (on) color else MColors.Muted, modifier = Modifier.size(20.dp))
                    Text(
                        "  $title", style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                        color = if (on) MColors.Ink else MColors.Muted,
                    )
                }
            }
        }
    }
}
