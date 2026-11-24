package eu.hxreborn.pixelinjector.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.theme.AppText
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(20.dp)
private val SheetPadding = 16.dp
private val SectionGap = 16.dp
private const val SHEET_HEIGHT = 0.8f

@Composable
fun CrtScreenOffSheet(
    mode: String,
    style: String,
    curve: String,
    duration: Int,
    flash: Int,
    afterglow: Int,
    fringe: Boolean,
    cyan: Boolean,
    capture: Boolean,
    shader: Boolean,
    onModeChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onCurveChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onFlashChange: (Int) -> Unit,
    onAfterglowChange: (Int) -> Unit,
    onFringeChange: (Boolean) -> Unit,
    onCyanChange: (Boolean) -> Unit,
    onCaptureChange: (Boolean) -> Unit,
    onShaderChange: (Boolean) -> Unit,
    onViewLog: () -> Unit,
) {
    Column(
        Modifier.fillMaxHeight(SHEET_HEIGHT).padding(SheetPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        SheetHeader(stringResource(R.string.tweak_crt_screen_off))
        ChipChoiceTile(
            title = stringResource(R.string.crt_mode),
            options =
                listOf(
                    Prefs.CRT_MODE_DEFAULT to stringResource(R.string.crt_mode_default),
                    Prefs.CRT_MODE_CLASSIC to stringResource(R.string.crt_mode_classic),
                    Prefs.CRT_MODE_STUDIO to stringResource(R.string.crt_mode_studio),
                    Prefs.CRT_MODE_PHOSPHOR to stringResource(R.string.crt_mode_phosphor),
                ),
            selected = mode,
            onSelect = onModeChange,
        )
        ChipChoiceTile(
            title = stringResource(R.string.crt_style),
            options =
                listOf(
                    Prefs.CRT_STYLE_HORIZONTAL to stringResource(R.string.crt_style_horizontal),
                    Prefs.CRT_STYLE_VERTICAL to stringResource(R.string.crt_style_vertical),
                ),
            selected = style,
            onSelect = onStyleChange,
        )
        ChipChoiceTile(
            title = stringResource(R.string.crt_curve),
            options =
                listOf(
                    Prefs.CRT_CURVE_STANDARD to stringResource(R.string.crt_curve_standard),
                    Prefs.CRT_CURVE_CREEP to stringResource(R.string.crt_curve_creep),
                    Prefs.CRT_CURVE_HEAVY to stringResource(R.string.crt_curve_heavy),
                    Prefs.CRT_CURVE_EXTREME to stringResource(R.string.crt_curve_extreme),
                ),
            selected = curve,
            onSelect = onCurveChange,
        )
        CrtSliderTile(
            title = stringResource(R.string.crt_duration),
            value = duration,
            range = Prefs.CRT_DURATION_MIN..Prefs.CRT_DURATION_MAX,
            step = DURATION_STEP_MS,
            valueText = { stringResource(R.string.crt_duration_value, it) },
            onChange = onDurationChange,
        )
        CrtSliderTile(
            title = stringResource(R.string.crt_flash),
            value = flash,
            range = Prefs.CRT_FLASH_MIN..Prefs.CRT_FLASH_MAX,
            step = FLASH_STEP_PERCENT,
            valueText = { stringResource(R.string.crt_flash_value, it) },
            onChange = onFlashChange,
        )
        if (mode == Prefs.CRT_MODE_PHOSPHOR) {
            CrtSliderTile(
                title = stringResource(R.string.crt_afterglow),
                value = afterglow,
                range = Prefs.CRT_AFTERGLOW_MIN..Prefs.CRT_AFTERGLOW_MAX,
                step = AFTERGLOW_STEP_MS,
                valueText = { stringResource(R.string.crt_afterglow_value, it) },
                onChange = onAfterglowChange,
            )
        }
        ChipSwitchTile(stringResource(R.string.crt_fringe), fringe, onFringeChange)
        ChipSwitchTile(stringResource(R.string.crt_cyan), cyan, onCyanChange)
        ChipSwitchTile(stringResource(R.string.crt_capture), capture, onCaptureChange)
        ChipSwitchTile(stringResource(R.string.crt_shader), shader, onShaderChange)
        TextButton(onClick = onViewLog) { Text(stringResource(R.string.crt_view_log)) }
    }
}

private const val DURATION_STEP_MS = 50
private const val FLASH_STEP_PERCENT = 10
private const val AFTERGLOW_STEP_MS = 50

@Composable
private fun CrtSliderTile(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    valueText: @Composable (Int) -> String,
    onChange: (Int) -> Unit,
) {
    var shown by remember(value) {
        mutableFloatStateOf(value.coerceIn(range.first, range.last).toFloat())
    }
    Tile(shape = TileShape) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppText.tileTitle)
            Text(valueText(shown.roundToInt()), style = AppText.tileTitle)
            Slider(
                value = shown,
                onValueChange = { shown = it },
                onValueChangeFinished = { onChange(shown.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            )
        }
    }
}
