package dev.ragnarok93.xclipsetuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.oneui.compose.theme.OneUITheme

class MainActivity : ComponentActivity() {
    private var snapshot by mutableStateOf<JSONObject?>(null)
    private var error by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OneUITheme(dynamicColors = false) {
                val colors = OneUITheme.colors
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) { refreshTelemetry() }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.seslRoundAndBgcolor)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Xclipse Tuner", color = colors.seslPrimaryTextColor, fontSize = 28.sp)
                        Text(
                            "GPU performance and thermal telemetry",
                            color = colors.seslSecondaryTextColor,
                            fontSize = 14.sp
                        )
                    }

                    val device = snapshot?.optJSONObject("device")
                    val gpu = snapshot?.optJSONObject("gpu")
                    val thermal = snapshot?.optJSONObject("thermal")
                    val devfreq = snapshot?.optJSONObject("devfreq")

                    Panel(title = "DEVICE", modifier = Modifier.fillMaxWidth()) {
                        ValueLine("Device", device?.optString("model").orEmpty().ifBlank { "Not detected" })
                        ValueLine("SoC", device?.optString("soc").orEmpty().ifBlank { "—" })
                        ValueLine("GPU", gpu?.optString("model").orEmpty().ifBlank { "Not detected" })
                        ValueLine("Kernel", device?.optString("kernel").orEmpty().ifBlank { "—" })
                    }

                    Panel(title = "LIVE GPU", modifier = Modifier.fillMaxWidth()) {
                        ValueLine("Clock", frequency(gpu?.optLong("clock_khz", -1L) ?: -1L))
                        ValueLine("Busy (raw)", number(gpu?.optInt("busy_raw", -1) ?: -1))
                        ValueLine(
                            "GPU temperature",
                            temperature(gpu?.optDouble("temperature_c", Double.NaN) ?: Double.NaN)
                        )
                        ValueLine("Governor", gpu?.optString("governor").orEmpty().ifBlank { "—" })
                        ValueLine(
                            "GPU max constraint",
                            frequency(gpu?.optLong("max_clock_khz", -1L) ?: -1L)
                        )
                    }

                    Panel(title = "DEVFREQ", modifier = Modifier.fillMaxWidth()) {
                        ValueLine("Current", frequency(devfreq?.optLong("current_khz", -1L) ?: -1L))
                        ValueLine("Target", frequency(devfreq?.optLong("target_khz", -1L) ?: -1L))
                        ValueLine(
                            "Min / max",
                            "${frequency(devfreq?.optLong("min_khz", -1L) ?: -1L)} / " +
                                frequency(devfreq?.optLong("max_khz", -1L) ?: -1L)
                        )
                    }

                    Panel(title = "THERMAL", modifier = Modifier.fillMaxWidth()) {
                        val g3d = thermal?.optLong("g3d_temperature_millic", -1L) ?: -1L
                        ValueLine(
                            "G3D zone",
                            if (g3d >= 0) "${"%.1f".format(g3d / 1000.0)} °C" else "—"
                        )
                        ValueLine(
                            "GPU cooling state",
                            number(thermal?.optInt("gpu_cooling_state", -1) ?: -1)
                        )
                    }

                    Panel(title = "PROFILES", modifier = Modifier.fillMaxWidth()) {
                        ProfileRow("Balanced", "Samsung defaults · available", true)
                        ProfileRow("Gaming", "Awaiting validated Xclipse controls", false)
                        ProfileRow("Battery Saver", "Awaiting validated Xclipse controls", false)
                        ProfileRow("Custom", "Awaiting validated Xclipse controls", false)
                    }

                    Panel(title = "SAFETY", modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Read-only mode is active. The app does not write GPU sysfs nodes. " +
                                "Samsung thermal and vendor power management remain untouched.",
                            color = colors.seslSecondaryTextColor,
                            fontSize = 14.sp
                        )
                    }

                    error?.let {
                        Text(it, color = colors.seslFunctionalRed, fontSize = 13.sp)
                    }
                    Text(
                        if (loading) "Reading module telemetry…" else "Tap to refresh",
                        color = colors.seslSecondaryTextColor,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable { scope.launch { refreshTelemetry() } }
                            .padding(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    private suspend fun refreshTelemetry() {
        loading = true
        error = null
        try {
            val output = withContext(Dispatchers.IO) {
                val process = ProcessBuilder(
                    "su",
                    "-c",
                    "/system/bin/sh /data/adb/modules/xclipse_tuner/bin/xclipse-tuner status"
                ).redirectErrorStream(true).start()
                val text = process.inputStream.bufferedReader().use { it.readText() }
                val exit = process.waitFor()
                if (exit != 0) throw IllegalStateException("Module command exited with $exit: $text")
                text
            }
            snapshot = JSONObject(output)
        } catch (failure: Exception) {
            error = failure.message ?: "Unable to read module telemetry"
        } finally {
            loading = false
        }
    }
}

@androidx.compose.runtime.Composable
private fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    val colors = OneUITheme.colors
    Column(
        modifier = modifier
            .background(colors.seslBackgroundColor, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = colors.seslPrimaryColor, fontSize = 12.sp)
        content()
    }
}

@androidx.compose.runtime.Composable
private fun ValueLine(label: String, value: String) {
    val colors = OneUITheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.seslSecondaryTextColor, fontSize = 14.sp)
        Text(value, color = colors.seslPrimaryTextColor, fontSize = 14.sp)
    }
}

@androidx.compose.runtime.Composable
private fun ProfileRow(name: String, description: String, enabled: Boolean) {
    val colors = OneUITheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (enabled) colors.seslPrimaryColor.copy(alpha = 0.10f) else colors.seslRoundAndBgcolor,
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(name, color = colors.seslPrimaryTextColor, fontSize = 16.sp)
        Text(description, color = colors.seslSecondaryTextColor, fontSize = 12.sp)
    }
}

private fun frequency(khz: Long): String =
    if (khz < 0) "—" else "${khz / 1000.0} MHz"

private fun temperature(value: Double): String =
    if (value.isNaN()) "—" else "${"%.1f".format(value)} °C"

private fun number(value: Int): String = if (value < 0) "—" else value.toString()
