package com.pushuprpg.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Placement
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.pose.PoseLandmarks as Lm

/**
 * The live placement line: what [com.pushuprpg.core.detect.PlacementCoach] says, in words.
 *
 * This is what replaced the paragraph of placement advice on the exercise picker. Read before the
 * set, that paragraph was forgotten by the time the phone was on the floor; said here, it is said
 * while the user is adjusting the phone, about the phone as it actually is — and it stops the
 * moment the placement is right.
 */
@Composable
fun PlacementBanner(
    placement: Placement,
    exercise: ExerciseType,
    modifier: Modifier = Modifier,
    /**
     * The pose model has not returned a frame yet. The coach's 화면 안으로 들어와 주세요 would then
     * mean only that no frame has come, so the line says the camera is getting ready instead.
     */
    preparing: Boolean = false,
) {
    val advice = placement.advice
    val line = if (preparing) {
        stringResource(R.string.camera_preparing)
    } else {
        advice?.let { stringResource(placementRes(it, exercise)) }
    }
    // Name the parts that left the picture: "아래쪽이 잘려요" is the fix, "화면 밖: 무릎, 발목" is
    // the reason for it, and the reason is what lets the user tell which way to move the phone.
    val parts = placement.offFrame.map { bodyPartRes(it) }.distinct().map { stringResource(it) }
    val detail = if (preparing || parts.isEmpty()) null else stringResource(R.string.placement_off_frame, parts.joinToString(", "))

    // Kept through the fade-out, so the banner fades the sentence rather than an empty pill.
    var lastLine by remember { mutableStateOf("") }
    var lastDetail by remember { mutableStateOf<String?>(null) }
    var lastReady by remember { mutableStateOf(false) }
    if (line != null) {
        lastLine = line
        lastDetail = detail
        lastReady = !preparing && advice == PlacementAdvice.READY
    }

    AnimatedVisibility(
        visible = line != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(300)),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.ScrimPanelHigh)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = lastLine,
                style = Type.bodyL,
                color = if (lastReady) LocalGameColors.current.accept else Palette.TextPrimary,
                textAlign = TextAlign.Center,
            )
            lastDetail?.let {
                Text(
                    text = it,
                    style = Type.bodyM,
                    color = Palette.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@StringRes
private fun placementRes(advice: PlacementAdvice, exercise: ExerciseType): Int = when (advice) {
    PlacementAdvice.STEP_INTO_VIEW -> R.string.placement_step_into_view
    PlacementAdvice.COME_CLOSER -> R.string.placement_come_closer
    PlacementAdvice.MOVE_PHONE_BACK -> R.string.placement_move_back
    PlacementAdvice.SHOW_BELOW -> R.string.placement_show_below
    PlacementAdvice.SHOW_ABOVE -> R.string.placement_show_above
    PlacementAdvice.CENTER -> R.string.placement_center
    // On the floor the fix is where the head points; standing or hanging, it is which way the
    // chest faces.
    PlacementAdvice.FACE_CAMERA -> when (exercise) {
        ExerciseType.PUSHUP, ExerciseType.PLANK -> R.string.placement_face_floor
        else -> R.string.placement_face_standing
    }
    PlacementAdvice.CLEARER -> R.string.placement_clearer
    PlacementAdvice.HOLD_PHONE_STILL -> R.string.quality_unstable_camera
    PlacementAdvice.SETTLING -> R.string.quality_subject_switch
    PlacementAdvice.SLOW_DOWN -> R.string.quality_implausible_rate
    PlacementAdvice.GET_IN_POSITION -> when (exercise) {
        ExerciseType.PUSHUP -> R.string.placement_start_pushup
        ExerciseType.PLANK -> R.string.placement_start_plank
        ExerciseType.SQUAT -> R.string.placement_start_squat
        ExerciseType.LUNGE -> R.string.placement_start_lunge
        ExerciseType.PULL_UP -> R.string.placement_start_pull_up
        ExerciseType.DIP -> R.string.placement_start_dip
    }
    PlacementAdvice.READY -> R.string.placement_ready
}

/** One name per joint, left and right folded together: the user has two knees and one problem. */
@StringRes
private fun bodyPartRes(landmark: Int): Int = when (landmark) {
    Lm.LEFT_SHOULDER, Lm.RIGHT_SHOULDER -> R.string.body_part_shoulder
    Lm.LEFT_ELBOW, Lm.RIGHT_ELBOW -> R.string.body_part_elbow
    Lm.LEFT_WRIST, Lm.RIGHT_WRIST -> R.string.body_part_wrist
    Lm.LEFT_HIP, Lm.RIGHT_HIP -> R.string.body_part_hip
    Lm.LEFT_KNEE, Lm.RIGHT_KNEE -> R.string.body_part_knee
    else -> R.string.body_part_ankle
}
