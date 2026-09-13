package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.billing.PlanPeriod
import com.pushuprpg.app.billing.SubscriptionPlan
import com.pushuprpg.app.ui.components.Pill
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.StatTile
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type

/**
 * The paywall.
 *
 * It opens with what the user has already built rather than with what they are missing, because by
 * the time anyone sees this they have done real reps and the honest offer is to keep going, not to
 * begin.
 *
 * It also states plainly that the first dungeon and the survival mode stay free forever. Hiding
 * that would be both a lie and a worse pitch: a fitness app that appears to lock exercise behind a
 * subscription earns one-star reviews and refunds, and the thing actually worth paying for here is
 * more game, not permission to work out.
 */
@Composable
fun PaywallScreen(
    plans: List<SubscriptionPlan>,
    lifetimeReps: Int,
    streakDays: Int,
    level: Int,
    onPurchase: (SubscriptionPlan) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    // Annual pre-selected: it is the better value and the better retention, and someone who wants
    // monthly will still find it one tap away.
    var selected by remember(plans) {
        mutableStateOf(plans.maxByOrNull { it.priceAmountMicros } ?: plans.firstOrNull())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.paywall_title),
            style = Type.headline,
            color = Palette.TextPrimary,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                value = lifetimeReps.toString(),
                label = stringResource(R.string.records_lifetime_reps),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${streakDays}일",
                label = stringResource(R.string.home_streak, streakDays).substringBefore(" "),
                accent = colors.combo,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "Lv.$level",
                label = "레벨",
                accent = Palette.Brand400,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(22.dp))
        plans.forEach { plan ->
            PlanCard(
                plan = plan,
                selected = plan == selected,
                cheapestPerMonth = plans.minOfOrNull { it.monthlyEquivalentMicros } ?: 0L,
                onClick = { selected = plan },
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(10.dp))
        // Play stops offering the trial to someone who has already used it, so the label has to
        // follow the offer. Promising seven free days above supporting text that correctly says
        // "monthly, charged immediately" is the kind of contradiction users act on and then refund.
        PrimaryButton(
            text = selected?.takeIf { it.hasFreeTrial }
                ?.let { stringResource(R.string.paywall_trial_days, it.freeTrialDays) }
                ?: stringResource(R.string.paywall_subscribe),
            supportingText = selected?.let { renewalSummary(it) },
            onClick = { selected?.let(onPurchase) },
            enabled = selected != null,
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.paywall_free_forever),
            style = Type.bodyM,
            color = colors.accept,
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface(shape = RoundedCornerShape(14.dp), color = colors.acceptDim)
                .padding(14.dp),
        )

        Spacer(Modifier.height(10.dp))
        // Korean subscription rules require the renewal terms to be legible before purchase, not
        // behind a link.
        Text(
            text = stringResource(R.string.paywall_manage),
            style = Type.labelM,
            color = Palette.TextTertiary,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = stringResource(R.string.paywall_restore),
                onClick = onRestore,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    selected: Boolean,
    cheapestPerMonth: Long,
    onClick: () -> Unit,
) {
    val colors = LocalGameColors.current
    val isBestValue = cheapestPerMonth > 0 && plan.monthlyEquivalentMicros <= cheapestPerMonth

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(
                shape = RoundedCornerShape(18.dp),
                color = if (selected) Palette.BrandWash else Palette.Bg2,
                borderColor = if (selected) Palette.Brand500 else Palette.StrokeSoft,
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = planTitle(plan), style = Type.titleM, color = Palette.TextPrimary)
                if (isBestValue) {
                    Spacer(Modifier.width(8.dp))
                    Pill(text = "최대 절약", tint = colors.accept)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = perMonthLabel(plan),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
        }
        Text(text = plan.formattedPrice, style = Type.titleL, color = Palette.TextPrimary)
    }
}

private fun planTitle(plan: SubscriptionPlan): String = when (plan.period) {
    PlanPeriod.ANNUAL -> "연간"
    PlanPeriod.MONTHLY -> "월간"
}

/**
 * Puts an annual plan on the same axis as a monthly one.
 *
 * The currency symbol is taken from the price Play already formatted rather than being rebuilt, so
 * this stays correct in every locale Play sells in.
 */
private fun perMonthLabel(plan: SubscriptionPlan): String {
    val symbol = plan.formattedPrice.takeWhile { !it.isDigit() }.trim()
    val perMonth = plan.monthlyEquivalentMicros / 1_000_000
    return "월 $symbol${"%,d".format(perMonth)} 꼴"
}

/** Renewal terms, which Korean subscription rules require to be visible before purchase. */
private fun renewalSummary(plan: SubscriptionPlan): String {
    val period = if (plan.period == PlanPeriod.ANNUAL) "1년" else "1개월"
    return if (plan.hasFreeTrial) {
        "${plan.freeTrialDays}일 무료 후 $period마다 ${plan.formattedPrice} 자동 결제"
    } else {
        "$period마다 ${plan.formattedPrice} 자동 결제"
    }
}
