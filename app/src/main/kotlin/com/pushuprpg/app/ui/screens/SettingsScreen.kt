package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.AppSettings
import com.pushuprpg.app.domain.HapticStrength
import com.pushuprpg.app.ui.components.SectionHeader
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.SkeletonMode
import com.pushuprpg.core.game.Difficulty

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onRecalibrate: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.action_settings),
            style = Type.headline,
            color = Palette.TextPrimary,
        )

        // 절전 집중 모드 leads, because it is three features at once — the accessibility mode, the
        // battery fix, and the honest test of whether the audio design carries the game. Burying it
        // among the toggles would hide the most useful setting in the app.
        Spacer(Modifier.height(6.dp))
        FeatureCard(
            title = stringResource(R.string.settings_audio_only),
            subtitle = stringResource(R.string.settings_audio_only_sub),
            checked = settings.audioOnly,
            onCheckedChange = { v -> onChange { it.copy(audioOnly = v) } },
        )

        Spacer(Modifier.height(10.dp))
        SectionHeader(text = "표시")
        SegmentedSetting(
            title = stringResource(R.string.settings_overlay),
            options = SkeletonMode.entries,
            labelFor = {
                stringResource(
                    when (it) {
                        SkeletonMode.OFF -> R.string.settings_overlay_off
                        SkeletonMode.MINIMAL -> R.string.settings_overlay_minimal
                        SkeletonMode.FULL -> R.string.settings_overlay_full
                    }
                )
            },
            selected = settings.skeletonMode,
            onSelect = { v -> onChange { it.copy(skeletonMode = v) } },
        )
        SegmentedSetting(
            title = stringResource(R.string.settings_gauge_side),
            options = listOf(true, false),
            labelFor = {
                stringResource(if (it) R.string.settings_gauge_right else R.string.settings_gauge_left)
            },
            selected = settings.gaugeOnRight,
            onSelect = { v -> onChange { it.copy(gaugeOnRight = v) } },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_gauge_number),
            checked = settings.showGaugeNumber,
            onCheckedChange = { v -> onChange { it.copy(showGaugeNumber = v) } },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_large_text),
            checked = settings.largeText,
            onCheckedChange = { v -> onChange { it.copy(largeText = v) } },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_reduce_motion),
            checked = settings.reduceMotion,
            onCheckedChange = { v -> onChange { it.copy(reduceMotion = v) } },
        )
        SegmentedSetting(
            title = stringResource(R.string.settings_cvd),
            options = listOf(false, true),
            labelFor = {
                stringResource(if (it) R.string.settings_cvd_blue_yellow else R.string.settings_cvd_none)
            },
            selected = settings.colourBlindSafe,
            onSelect = { v -> onChange { it.copy(colourBlindSafe = v) } },
        )

        Spacer(Modifier.height(10.dp))
        SectionHeader(text = "소리와 진동")
        SwitchSetting(
            title = stringResource(R.string.settings_sfx),
            checked = settings.sfxEnabled,
            onCheckedChange = { v -> onChange { it.copy(sfxEnabled = v) } },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_music),
            checked = settings.musicEnabled,
            onCheckedChange = { v -> onChange { it.copy(musicEnabled = v) } },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_voice),
            checked = settings.voiceEnabled,
            onCheckedChange = { v -> onChange { it.copy(voiceEnabled = v) } },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_captions),
            checked = settings.captionsEnabled,
            onCheckedChange = { v -> onChange { it.copy(captionsEnabled = v) } },
        )
        SegmentedSetting(
            title = stringResource(R.string.settings_haptics),
            options = HapticStrength.entries,
            labelFor = {
                stringResource(
                    when (it) {
                        HapticStrength.OFF -> R.string.settings_haptics_off
                        HapticStrength.LIGHT -> R.string.settings_haptics_light
                        HapticStrength.MEDIUM -> R.string.settings_haptics_medium
                        HapticStrength.STRONG -> R.string.settings_haptics_strong
                    }
                )
            },
            selected = settings.hapticStrength,
            onSelect = { v -> onChange { it.copy(hapticStrength = v) } },
        )

        Spacer(Modifier.height(10.dp))
        SectionHeader(text = "운동")
        SegmentedSetting(
            title = stringResource(R.string.settings_exercise),
            options = ExerciseType.entries,
            labelFor = {
                stringResource(
                    when (it) {
                        ExerciseType.PUSHUP -> R.string.exercise_pushup
                        ExerciseType.SQUAT -> R.string.exercise_squat
                        ExerciseType.PLANK -> R.string.exercise_plank
                    }
                )
            },
            selected = settings.exercise,
            onSelect = { v -> onChange { it.copy(exercise = v) } },
        )
        SegmentedSetting(
            title = "난이도",
            options = Difficulty.entries,
            labelFor = { it.korean },
            selected = settings.difficulty,
            onSelect = { v -> onChange { it.copy(difficulty = v) } },
        )
        ActionSetting(
            title = stringResource(R.string.settings_recalibrate),
            onClick = onRecalibrate,
        )

        Spacer(Modifier.height(16.dp))
        // The privacy line lives on the settings screen as well as in the permission flow: it is
        // the single biggest objection a camera fitness app faces, and it is true, so it should be
        // easy to find rather than buried in a policy nobody opens.
        Text(
            text = stringResource(R.string.permission_privacy),
            style = Type.bodyM,
            color = Palette.TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface(shape = RoundedCornerShape(16.dp))
                .padding(14.dp),
        )
        ActionSetting(
            title = stringResource(R.string.settings_privacy),
            onClick = onOpenPrivacy,
        )
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalGameColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(
                shape = RoundedCornerShape(18.dp),
                color = if (checked) Palette.BrandWash else Palette.Bg2,
            )
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = Type.titleM, color = Palette.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = Type.bodyM, color = Palette.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accept),
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalGameColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = Type.bodyL, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accept),
        )
    }
}

@Composable
private fun <T> SegmentedSetting(
    title: String,
    options: List<T>,
    labelFor: @Composable (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(text = title, style = Type.bodyL, color = Palette.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val active = option == selected
                Text(
                    text = labelFor(option),
                    style = Type.labelL,
                    color = if (active) Palette.TextOnAccent else Palette.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .cardSurface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) Palette.Brand500 else Palette.Bg3,
                        )
                        .clickable { onSelect(option) }
                        .padding(vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionSetting(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        style = Type.bodyL,
        color = Palette.Brand400,
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}
