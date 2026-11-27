package com.jtownzen.secondchanceil.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jtownzen.secondchanceil.ui.theme.LotteryEmerald
import com.jtownzen.secondchanceil.ui.theme.LuckyGold

/** True when the screen is wider than it is tall (phone turned sideways). */
@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

enum class StepState { DONE, CURRENT, UPCOMING }

/** One numbered step of a guided flow. */
data class FlowStep(val title: String, val detail: String = "")

/**
 * Works out each step's state from the index of the step the user should do now.
 * Steps before it are done, the step itself is current, the rest are still to come.
 */
fun stepStates(stepCount: Int, currentIndex: Int): List<StepState> =
    List(stepCount) { i ->
        when {
            i < currentIndex -> StepState.DONE
            i == currentIndex -> StepState.CURRENT
            else -> StepState.UPCOMING
        }
    }

/** The numbered circle for a step: a check when done, gold when it is the next thing to do. */
@Composable
fun StepBadge(number: Int, state: StepState, modifier: Modifier = Modifier, size: Int = 26) {
    val background = when (state) {
        StepState.DONE -> LotteryEmerald
        StepState.CURRENT -> LuckyGold
        StepState.UPCOMING -> Color.Transparent
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .background(background, CircleShape)
            .border(1.5.dp, if (state == StepState.UPCOMING) Color.Gray else background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (state == StepState.DONE) {
            Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.Black, modifier = Modifier.size((size * 0.65f).dp))
        } else {
            Text(
                text = number.toString(),
                color = if (state == StepState.CURRENT) Color.Black else Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.5f).sp
            )
        }
    }
}

/** Steps stacked top to bottom, for side panels in landscape. The current step is outlined in gold. */
@Composable
fun VerticalStepList(
    steps: List<FlowStep>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val states = stepStates(steps.size, currentIndex)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { i, step ->
            val state = states[i]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .then(
                        if (state == StepState.CURRENT) {
                            Modifier.border(1.5.dp, LuckyGold, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                StepBadge(number = i + 1, state = state)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = step.title,
                        color = if (state == StepState.UPCOMING) textColor.copy(alpha = 0.55f) else textColor,
                        fontWeight = if (state == StepState.CURRENT) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (step.detail.isNotBlank() && state == StepState.CURRENT) {
                        Text(
                            text = step.detail,
                            color = textColor.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/** Steps side by side in one line, for portrait screens. */
@Composable
fun HorizontalStepStrip(
    steps: List<FlowStep>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val states = stepStates(steps.size, currentIndex)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { i, step ->
            val state = states[i]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false).padding(horizontal = 2.dp)
            ) {
                StepBadge(number = i + 1, state = state, size = 22)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = step.title,
                    color = if (state == StepState.UPCOMING) textColor.copy(alpha = 0.55f) else textColor,
                    fontWeight = if (state == StepState.CURRENT) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
