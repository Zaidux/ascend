package com.mochi.ascend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochi.ascend.data.AppData
import com.mochi.ascend.data.Goal
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Store
import com.mochi.ascend.ui.AscendTheme
import com.mochi.ascend.ui.Glyph
import com.mochi.ascend.ui.GlyphIcon
import com.mochi.ascend.ui.GoalDetailScreen
import com.mochi.ascend.ui.GoalEditorScreen
import com.mochi.ascend.ui.LadderScreen
import com.mochi.ascend.ui.PlanWizardScreen
import com.mochi.ascend.ui.ReviewScreen
import com.mochi.ascend.ui.SettingsScreen
import com.mochi.ascend.ui.StatsScreen
import com.mochi.ascend.ui.TodayScreen
import com.mochi.ascend.widget.TodayWidget

private enum class Tab(val label: String, val glyph: Glyph) {
    TODAY("Today", Glyph.TODAY),
    LADDER("Ladder", Glyph.LADDER),
    STATS("Stats", Glyph.CHART),
    REVIEW("Review", Glyph.BOOK),
    SETTINGS("Settings", Glyph.GEAR),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Store.onWidgetRefresh { TodayWidget.refresh(applicationContext) }
        enableEdgeToEdge()

        setContent {
            val data by Store.data.collectAsStateWithLifecycle()
            AscendTheme(themeId = data.settings.themeId, dark = data.settings.dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(data)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(data: AppData) {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var openGoalId by remember { mutableStateOf<String?>(null) }
    // goal to edit (null = creating) and the horizon to preselect
    var editorTarget by remember { mutableStateOf<Pair<Goal?, Horizon>?>(null) }
    var showWizard by remember { mutableStateOf(false) }

    val editing = editorTarget

    if (showWizard) {
        PlanWizardScreen(
            data = data,
            onClose = { showWizard = false },
            onCreated = {
                showWizard = false
                tab = Tab.LADDER
            },
        )
        return
    }

    if (editing != null) {
        GoalEditorScreen(
            data = data,
            existing = editing.first,
            initialHorizon = editing.second,
            onClose = { editorTarget = null },
        )
        return
    }

    val goalId = openGoalId
    if (goalId != null) {
        GoalDetailScreen(
            data = data,
            goalId = goalId,
            onBack = { openGoalId = null },
            onEdit = { goal -> editorTarget = goal to goal.horizon },
            onOpenGoal = { goal -> openGoalId = goal.id },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            GlyphIcon(entry.glyph, size = 20.dp)
                        },
                        label = { Text(entry.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editorTarget = null to Horizon.DAY },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                GlyphIcon(
                    Glyph.PLUS,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    data = data,
                    onOpenGoal = { goal -> openGoalId = goal.id },
                    onAddGoal = { horizon -> editorTarget = null to horizon },
                    onBuildPlan = { showWizard = true },
                )

                Tab.LADDER -> LadderScreen(
                    data = data,
                    onOpenGoal = { goal -> openGoalId = goal.id },
                    onAddGoal = { horizon -> editorTarget = null to horizon },
                )

                Tab.STATS -> StatsScreen(data)

                Tab.REVIEW -> ReviewScreen(data)

                Tab.SETTINGS -> SettingsScreen(data)
            }
        }
    }
}
