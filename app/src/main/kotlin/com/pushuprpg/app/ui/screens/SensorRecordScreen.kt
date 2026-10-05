package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.sensor.SensorRecordViewModel
import com.pushuprpg.app.ui.components.KeepScreenOn
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.durationText
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType

/** The movements a sensor recording is for: the ones the sensor detector is being written for. */
private val SENSOR_MOVEMENTS = listOf(ExerciseType.PUSHUP, ExerciseType.SQUAT)

/**
 * Debug builds only: 설정 → 테스트 → 센서 기록하기. Records the phone's accelerometer and gyroscope
 * through a set and sends them as a file, for building and testing a sensor rep detector on a PC.
 */
@Composable
fun SensorRecordScreen(
    vm: SensorRecordViewModel,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    // Android stops an app's motion sensors once it is in the background, and a screen going off
    // puts it there: a recording runs with the screen on.
    if (state.recording) KeepScreenOn()

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.sensor_record_title),
            style = Type.headline,
            color = Palette.TextPrimary,
        )
        Text(
            text = stringResource(R.string.sensor_record_intro),
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )

        if (!vm.hasAccelerometer) {
            Text(
                text = stringResource(R.string.sensor_record_no_accelerometer),
                style = Type.bodyL,
                color = Palette.TextPrimary,
            )
            return@Column
        }
        if (!vm.hasGyroscope) {
            Text(
                text = stringResource(R.string.sensor_record_no_gyroscope),
                style = Type.bodyM,
                color = Palette.TextTertiary,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SENSOR_MOVEMENTS.forEach { movement ->
                val label = stringResource(exerciseLabelRes(movement))
                val pick = { vm.selectMovement(movement) }
                if (movement == state.movement) {
                    PrimaryButton(text = label, onClick = pick, modifier = Modifier.weight(1f))
                } else {
                    SecondaryButton(
                        text = label,
                        onClick = pick,
                        enabled = !state.recording,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface(shape = RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = durationText(state.seconds), style = Type.headline, color = Palette.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.sensor_record_counts, state.accelSamples, state.gyroSamples),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
        }

        if (state.recording) {
            PrimaryButton(text = stringResource(R.string.sensor_record_stop), onClick = vm::stop)
        } else {
            PrimaryButton(text = stringResource(R.string.sensor_record_start), onClick = vm::start)
        }

        if (state.hasTrace && !state.recording) {
            Text(
                text = stringResource(R.string.sensor_record_reps),
                style = Type.bodyL,
                color = Palette.TextPrimary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.sensor_record_minus),
                    onClick = { vm.setActualReps(state.actualReps - 1) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.actualReps.toString(),
                    style = Type.headline,
                    color = Palette.TextPrimary,
                    modifier = Modifier.weight(1f).wrapContentWidth(),
                )
                SecondaryButton(
                    text = stringResource(R.string.sensor_record_plus),
                    onClick = { vm.setActualReps(state.actualReps + 1) },
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(text = stringResource(R.string.sensor_record_send), onClick = onSend)
        }
    }
}
