package com.dub.calorietracker.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import com.dub.calorietracker.data.ThemeMode
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalDate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.dub.calorietracker.R
import com.dub.calorietracker.data.EntrySource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Restaurant),
    WEEK("Progress", Icons.Filled.CalendarMonth),
    PLAN("Plan", Icons.Filled.Calculate),
    CHECKIN("Check-in", Icons.Filled.MonitorWeight),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = when (state.theme.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status/navigation bar icons follow the app's light/dark choice, not just the phone's.
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(state.theme.accent, dark) { App(vm) }
        }
    }
}

/** Chakra Petch (SIL Open Font License) — squared, techy letterforms used for all text. */
private val AppFont = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

internal val AppTypography = Typography().let { t ->
    fun TextStyle.f() = copy(fontFamily = AppFont)
    t.copy(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

@Composable
fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (state.plan == null) Tab.PLAN else Tab.TODAY) }
    // The camera screen opens from the camera button in the middle of the bottom bar.
    var showCapture by rememberSaveable { mutableStateOf(false) }
    // Settings opens from the gear on the Check-in tab.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Bumped to reset the capture screen (clear photo + note) after a meal is logged.
    var captureSession by rememberSaveable { mutableIntStateOf(0) }

    fun openCapture() {
        vm.selectedDate.value = LocalDate.now()
        captureSession++
        showCapture = true
    }
    fun closeCapture() { showCapture = false; tab = Tab.TODAY }

    if (state.plan == null) {
        // First run: setup is required. No bottom bar, no camera — nothing else is reachable until a plan is saved.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            PlanScreen(
                vm,
                Modifier.systemBarsPadding().imePadding(),
                firstRun = true,
                onDone = { tab = Tab.TODAY },
            )
        }
    } else if (showSettings) {
        BackHandler { showSettings = false }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                Row(Modifier.padding(start = 8.dp, top = 8.dp)) {
                    IconButton(onClick = { showSettings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
                SettingsScreen(vm, Modifier.weight(1f))
            }
        }
    } else if (showCapture) {
        key(captureSession) { CaptureScreen(vm, onClose = ::closeCapture) }
    } else Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                @Composable
                fun RowScope.TabItem(t: Tab) = NavigationBarItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = { Icon(t.icon, contentDescription = t.label) },
                    label = { Text(t.label) },
                    colors = itemColors,
                )
                TabItem(Tab.TODAY)
                TabItem(Tab.WEEK)
                // Center camera button: camera, gallery, typing and barcode.
                NavigationBarItem(
                    selected = false,
                    onClick = ::openCapture,
                    icon = {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CameraAlt, "Add food with camera", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = itemColors,
                )
                TabItem(Tab.PLAN)
                TabItem(Tab.CHECKIN)
            }
        }
    ) { padding ->
        val mod = Modifier.padding(padding).consumeWindowInsets(padding).imePadding()
        when (tab) {
            Tab.TODAY -> TodayScreen(vm, mod, onSetUpPlan = { tab = Tab.PLAN })
            Tab.WEEK -> WeekScreen(vm, mod, onSetUpPlan = { tab = Tab.PLAN },
                onOpenDay = { vm.selectedDate.value = it; tab = Tab.TODAY })
            Tab.PLAN -> PlanScreen(vm, mod, onDone = { tab = Tab.TODAY })
            Tab.CHECKIN -> CheckInScreen(vm, mod, onOpenSettings = { showSettings = true })
        }
    }

    error?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
            title = { Text("Couldn't estimate") },
            text = { Text(msg) },
        )
    }

    RestoreDialog(vm)

    pending?.let { p ->
        val busy by vm.busy.collectAsStateWithLifecycle()
        ReviewDialog(p, busy = busy,
            onRefine = if (p.source == EntrySource.PHOTO || p.source == EntrySource.TEXT) { fix -> vm.refine(p, fix) } else null,
            onCancel = vm::cancelPending, onConfirm = { edited, servings, remember ->
            vm.confirmPending(edited, servings, remember)
            // After logging from the camera screen, show today's bars.
            if (showCapture) closeCapture()
        })
    }
}
