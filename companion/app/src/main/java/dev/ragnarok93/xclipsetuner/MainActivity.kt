package dev.ragnarok93.xclipsetuner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.oneui.compose.theme.OneUITheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TelemetrySample(
    val timeMs: Long,
    val clockMhz: Float?,
    val busy: Float?,
    val gpuTemp: Float?,
    val maxMhz: Float?,
    val g3dTemp: Float?,
    val cpuCoolingStates: String,
    val thermalZoneTemperatures: String
)

class MainActivity : ComponentActivity() {
    private var snapshot by mutableStateOf<JSONObject?>(null)
    private var error by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(true)
    private var monitoring by mutableStateOf(true)
    private var logging by mutableStateOf(false)
    private var intervalIndex by mutableStateOf(1)
    private val samples = mutableStateListOf<TelemetrySample>()
    private val intervals = listOf(1000L, 2000L, 5000L, 10000L)
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val logFile by lazy { File(filesDir, "xclipse-session.csv") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OneUITheme(dynamicColors = false) {
                val colors = OneUITheme.colors
                LaunchedEffect(Unit) { refreshTelemetry() }
                LaunchedEffect(monitoring, intervalIndex) {
                    if (monitoring) {
                        while (true) {
                            delay(intervals[intervalIndex])
                            refreshTelemetry()
                        }
                    }
                }

                val device = snapshot?.optJSONObject("device")
                val gpu = snapshot?.optJSONObject("gpu")
                val thermal = snapshot?.optJSONObject("thermal")
                val devfreq = snapshot?.optJSONObject("devfreq")
                val controls = snapshot?.optJSONObject("controls")
                val updated = snapshot?.optLong("sampled_at_ms", -1L) ?: -1L

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.seslRoundAndBgcolor)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Xclipse Tuner", color = colors.seslPrimaryTextColor, fontSize = 28.sp)
                        Text("Live GPU performance, thermal and session telemetry", color = colors.seslSecondaryTextColor, fontSize = 14.sp)
                    }

                    Panel("MONITOR", Modifier.fillMaxWidth()) {
                        ValueLine("Monitoring", if (monitoring) "Live · every ${intervals[intervalIndex] / 1000}s" else "Paused")
                        ValueLine("Last update", if (updated > 0) timeLabel(updated) else "Waiting for first sample")
                        ValueLine("History", "${samples.size} / 180 samples")
                        ActionLine(if (monitoring) "Pause monitoring" else "Resume monitoring") { monitoring = !monitoring }
                        ActionLine("Refresh now") { refreshFromUi() }
                        ActionLine("Polling interval: ${intervals[intervalIndex] / 1000}s · change") {
                            intervalIndex = (intervalIndex + 1) % intervals.size
                        }
                    }

                    Panel("DEVICE", Modifier.fillMaxWidth()) {
                        ValueLine("Device", device?.optString("model").orEmpty().ifBlank { "Not detected" })
                        ValueLine("SoC", device?.optString("soc").orEmpty().ifBlank { "—" })
                        ValueLine("GPU", gpu?.optString("model").orEmpty().ifBlank { "Not detected" })
                        ValueLine("Kernel", device?.optString("kernel").orEmpty().ifBlank { "—" })
                        ValueLine("Detection", snapshot?.optString("detection").orEmpty().ifBlank { "Unknown" })
                    }

                    Panel("LIVE GPU", Modifier.fillMaxWidth()) {
                        ValueLine("Clock", frequency(gpu?.optLong("clock_khz", -1L) ?: -1L))
                        ValueLine("Busy (raw)", number(gpu?.optInt("busy_raw", -1) ?: -1))
                        ValueLine("GPU temperature", temperature(gpu?.optDouble("temperature_c", Double.NaN) ?: Double.NaN))
                        ValueLine("Governor", gpu?.optString("governor").orEmpty().ifBlank { "—" })
                        ValueLine("GPU min / max cap", "${frequency(gpu?.optLong("min_clock_khz", -1L) ?: -1L)} / ${frequency(gpu?.optLong("max_clock_khz", -1L) ?: -1L)}")
                    }

                    Panel("DEVFREQ", Modifier.fillMaxWidth()) {
                        ValueLine("Node", devfreq?.optString("name").orEmpty().ifBlank { "Not found" })
                        ValueLine("Current", frequency(devfreq?.optLong("current_khz", -1L) ?: -1L))
                        ValueLine("Target", frequency(devfreq?.optLong("target_khz", -1L) ?: -1L))
                        ValueLine("Min / max", "${frequency(devfreq?.optLong("min_khz", -1L) ?: -1L)} / ${frequency(devfreq?.optLong("max_khz", -1L) ?: -1L)}")
                    }

                    Panel("THERMAL", Modifier.fillMaxWidth()) {
                        val g3d = thermal?.optLong("g3d_temperature_millic", -1L) ?: -1L
                        ValueLine("GPU sensor", temperature(gpu?.optDouble("temperature_c", Double.NaN) ?: Double.NaN))
                        ValueLine("G3D zone", if (g3d >= 0) "${"%.1f".format(Locale.US, g3d / 1000.0)} °C" else "—")
                        ValueLine("GPU cooling device", thermal?.optString("gpu_cooling_type").orEmpty().ifBlank { "Not found" })
                        ValueLine("Cooling state", "${number(thermal?.optInt("gpu_cooling_state", -1) ?: -1)} / ${number(thermal?.optInt("gpu_cooling_max_state", -1) ?: -1)}")
                        ValueLine("CPU cooling states", coolingStatesLabel(snapshot))
                        Text("Thermal zones: ${thermalZoneTemperaturesLabel(snapshot)}", color = colors.seslSecondaryTextColor, fontSize = 11.sp)
                        Text("CPU cooling states show active cpufreq cooling-device levels. A zero GPU cooling-device state does not rule out Samsung/vendor GPU frequency capping.", color = colors.seslSecondaryTextColor, fontSize = 12.sp)
                    }

                    Panel("PERFORMANCE HISTORY", Modifier.fillMaxWidth()) {
                        TelemetryChart("GPU clock", samples.mapNotNull { it.clockMhz }, "MHz")
                        TelemetryChart("GPU busy (raw)", samples.mapNotNull { it.busy }, "")
                        TelemetryChart("GPU temperature", samples.mapNotNull { it.gpuTemp }, "°C")
                        TelemetryChart("G3D thermal zone", samples.mapNotNull { it.g3dTemp }, "°C")
                        TelemetryChart("GPU max constraint", samples.mapNotNull { it.maxMhz }, "MHz")
                        ActionLine("Clear chart history") { samples.clear() }
                    }

                    Panel("SESSION LOGGING", Modifier.fillMaxWidth()) {
                        ValueLine("Recorder", if (logging) "Recording to app-private CSV" else "Off")
                        ValueLine("Log file", if (logFile.exists()) "${logFile.length() / 1024} KB" else "Not created")
                        ActionLine(if (logging) "Stop logging" else "Start logging") { toggleLogging() }
                        ActionLine("Share / export CSV") { shareLog() }
                        Text("Logging records each successful poll: GPU telemetry, G3D temperature, all thermal-zone temperatures, and CPU cooling-device states. The file stays in app-private storage until shared or the app is cleared.", color = colors.seslSecondaryTextColor, fontSize = 12.sp)
                    }

                    Panel("TUNING CONTROLS", Modifier.fillMaxWidth()) {
                        val writesEnabled = controls?.optBoolean("writes_enabled", false) ?: false
                        ValueLine("Write interface", if (writesEnabled) "Enabled by module" else "Locked · read-only")
                        ValueLine("Validated write nodes", controls?.optJSONArray("validated_write_nodes")?.length()?.toString() ?: "0")
                        ProfileRow("Balanced", "Stock Samsung policy · no override", true)
                        ProfileRow("Gaming", "Locked until GPU cap semantics and restore path are validated", false)
                        ProfileRow("Battery Saver", "Locked until supported controls are validated", false)
                        ProfileRow("Custom", "Locked · no arbitrary sysfs writes", false)
                        Text(controls?.optString("reason").orEmpty().ifBlank { "No validated tuning controls are exposed by the module." }, color = colors.seslSecondaryTextColor, fontSize = 12.sp)
                        Text("No controls here will disable thermal protection, force maximum clocks, change IFPO, or bypass Samsung vendor power management.", color = colors.seslSecondaryTextColor, fontSize = 12.sp)
                    }

                    Panel("SAFETY & STATUS", Modifier.fillMaxWidth()) {
                        Text("Telemetry is read-only. The companion does not accept arbitrary paths or values and does not write GPU sysfs nodes. Samsung thermal and vendor power management remain in control.", color = colors.seslSecondaryTextColor, fontSize = 13.sp)
                        error?.let { Text(it, color = colors.seslFunctionalRed, fontSize = 12.sp) }
                        Text(if (loading) "Reading telemetry…" else "Tap Refresh now to sample immediately.", color = colors.seslSecondaryTextColor, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    private fun refreshFromUi() {
        if (!loading) activityScope.launch { refreshTelemetry() }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private suspend fun refreshTelemetry() {
        if (loading && snapshot != null) return
        loading = true
        error = null
        try {
            val output = withContext(Dispatchers.IO) {
                val process = ProcessBuilder(
                    "su", "-c",
                    "/system/bin/sh /data/adb/modules/xclipse_tuner/bin/xclipse-tuner status"
                ).redirectErrorStream(true).start()
                val text = process.inputStream.bufferedReader().use { it.readText() }
                val exit = process.waitFor()
                if (exit != 0) throw IllegalStateException("Module command exited with $exit: $text")
                text
            }
            val parsed = JSONObject(output)
            val now = System.currentTimeMillis()
            parsed.put("sampled_at_ms", now)
            snapshot = parsed
            val gpu = parsed.optJSONObject("gpu")
            val sample = TelemetrySample(
                now,
                gpu?.optLong("clock_khz", -1L)?.takeIf { it >= 0 }?.div(1000f),
                gpu?.optDouble("busy_raw", Double.NaN)?.takeIf { it.isFinite() }?.toFloat(),
                gpu?.optDouble("temperature_c", Double.NaN)?.takeIf { it.isFinite() }?.toFloat(),
                gpu?.optLong("max_clock_khz", -1L)?.takeIf { it >= 0 }?.div(1000f),
                parsed.optJSONObject("thermal")?.optDouble("g3d_temperature_c", Double.NaN)?.takeIf { it.isFinite() }?.toFloat(),
                cpuCoolingStates(parsed),
                thermalZoneTemperatures(parsed)
            )
            samples.add(sample)
            while (samples.size > 180) samples.removeAt(0)
            if (logging) appendLog(sample)
        } catch (failure: Exception) {
            error = failure.message ?: "Unable to read module telemetry"
        } finally {
            loading = false
        }
    }

    private suspend fun appendLog(sample: TelemetrySample) = withContext(Dispatchers.IO) {
        if (!logFile.exists() || logFile.length() == 0L) {
            logFile.writeText("timestamp_iso,gpu_clock_mhz,gpu_busy_raw,gpu_temperature_c,gpu_max_cap_mhz\n")
        }
        logFile.appendText(
            "${isoTime(sample.timeMs)},${csv(sample.clockMhz)},${csv(sample.busy)},${csv(sample.gpuTemp)},${csv(sample.maxMhz)}\n"
        )
    }

    private fun toggleLogging() {
        if (logging) {
            logging = false
        } else {
            if (!logFile.exists()) logFile.writeText("timestamp_iso,gpu_clock_mhz,gpu_busy_raw,gpu_temperature_c,gpu_max_cap_mhz\n")
            logging = true
        }
    }

    private fun shareLog() {
        if (!logFile.exists() || logFile.length() == 0L) {
            error = "No session log exists yet. Start logging and collect a few samples first."
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Xclipse Tuner telemetry session")
            putExtra(Intent.EXTRA_TEXT, logFile.readText())
        }
        startActivity(Intent.createChooser(send, "Export telemetry CSV"))
    }
}


@androidx.compose.runtime.Composable
private fun Panel(title: String, modifier: Modifier = Modifier, content: @androidx.compose.runtime.Composable () -> Unit) {
    val colors = OneUITheme.colors
    Column(
        modifier = modifier.background(colors.seslBackgroundColor, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = colors.seslPrimaryColor, fontSize = 12.sp)
        content()
    }
}

@androidx.compose.runtime.Composable
private fun ValueLine(label: String, value: String) {
    val colors = OneUITheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.seslSecondaryTextColor, fontSize = 13.sp)
        Text(value, color = colors.seslPrimaryTextColor, fontSize = 13.sp)
    }
}

@androidx.compose.runtime.Composable
private fun ActionLine(label: String, action: () -> Unit) {
    val colors = OneUITheme.colors
    Text(label, color = colors.seslPrimaryColor, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 5.dp))
}

@androidx.compose.runtime.Composable
private fun ProfileRow(name: String, description: String, enabled: Boolean) {
    val colors = OneUITheme.colors
    Column(
        Modifier.fillMaxWidth().background(
            if (enabled) colors.seslPrimaryColor.copy(alpha = 0.10f) else colors.seslRoundAndBgcolor,
            RoundedCornerShape(16.dp)
        ).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(name, color = colors.seslPrimaryTextColor, fontSize = 15.sp)
        Text(description, color = colors.seslSecondaryTextColor, fontSize = 12.sp)
    }
}

@androidx.compose.runtime.Composable
private fun TelemetryChart(title: String, values: List<Float>, unit: String) {
    val colors = OneUITheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = colors.seslPrimaryTextColor, fontSize = 13.sp)
        if (values.size < 2) {
            Text("Collecting samples…", color = colors.seslSecondaryTextColor, fontSize = 11.sp)
        } else {
            val min = values.minOrNull() ?: 0f
            val max = values.maxOrNull() ?: 1f
            val range = (max - min).takeIf { it > 0f } ?: 1f
            Canvas(Modifier.fillMaxWidth().height(76.dp)) {
                val left = 2f
                val top = 5f
                val usableWidth = size.width - 4f
                val usableHeight = size.height - 10f
                for (index in 1 until values.size) {
                    val x1 = left + usableWidth * (index - 1) / (values.size - 1)
                    val x2 = left + usableWidth * index / (values.size - 1)
                    val y1 = top + usableHeight * (1f - (values[index - 1] - min) / range)
                    val y2 = top + usableHeight * (1f - (values[index] - min) / range)
                    drawLine(colors.seslPrimaryColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }
            Text("Min ${"%.1f".format(Locale.US, min)} · Max ${"%.1f".format(Locale.US, max)} ${unit}", color = colors.seslSecondaryTextColor, fontSize = 10.sp)
        }
    }
}

private fun cpuCoolingStates(snapshot: JSONObject): String {
    val devices = snapshot.optJSONArray("cooling_devices") ?: return ""
    return (0 until devices.length()).mapNotNull { index ->
        val device = devices.optJSONObject(index) ?: return@mapNotNull null
        val type = device.optString("type")
        if (!type.contains("cpu", ignoreCase = true) && !type.contains("cpufreq", ignoreCase = true)) return@mapNotNull null
        val state = device.optInt("current_state", -1)
        val max = device.optInt("max_state", -1)
        if (state < 0) null else "$type=$state/${if (max >= 0) max.toString() else "?"}"
    }.joinToString(";")
}

private fun thermalZoneTemperatures(snapshot: JSONObject): String {
    val zones = snapshot.optJSONArray("thermal_zones") ?: return ""
    return (0 until zones.length()).mapNotNull { index ->
        val zone = zones.optJSONObject(index) ?: return@mapNotNull null
        val type = zone.optString("type").ifBlank { return@mapNotNull null }
        val milli = zone.optDouble("temperature_millic", Double.NaN)
        if (!milli.isFinite()) null else "$type=${"%.1f".format(Locale.US, milli / 1000.0)}"
    }.joinToString(";")
}

private fun coolingStatesLabel(snapshot: JSONObject?): String {
    val value = snapshot?.let(::cpuCoolingStates).orEmpty()
    return value.ifBlank { "Not reported" }
}

private fun thermalZoneTemperaturesLabel(snapshot: JSONObject?): String {
    val value = snapshot?.let(::thermalZoneTemperatures).orEmpty()
    return value.ifBlank { "Not reported" }
}

private fun csvText(value: String): String = "\"${value.replace("\"", "\"\"")}\""
private fun frequency(khz: Long): String = if (khz < 0) "—" else "${khz / 1000.0} MHz"
private fun temperature(value: Double): String = if (!value.isFinite()) "—" else "${"%.1f".format(Locale.US, value)} °C"
private fun number(value: Int): String = if (value < 0) "—" else value.toString()
private fun timeLabel(value: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(value))
private fun isoTime(value: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(value))
private fun csv(value: Float?): String = value?.toString() ?: ""
