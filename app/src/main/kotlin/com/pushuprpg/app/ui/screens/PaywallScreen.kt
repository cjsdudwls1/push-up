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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.billing.BillingEvent
import com.pushuprpg.app.billing.PlanPeriod
import com.pushuprpg.app.billing.RestoreOutcome
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
 *
 * With no plans it says whether Play is still being asked or cannot be — [plansUnavailable] — and
 * offers [onRetry]. It used to draw no card and a grey button, a dead end offline, signed out of
 * Play, or on a build Play will not sell from.
 */
@Composable
fun PaywallScreen(
    plans: List<SubscriptionPlan>,
    plansUnavailable: Boolean,
    lifetimeReps: Int,
    streakDays: Int,
    level: Int,
    onPurchase: (SubscriptionPlan) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
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
                value = stringResource(R.string.records_days_value, streakDays),
                label = stringResource(R.string.home_streak, streakDays).substringBefore(" "),
                accent = colors.combo,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "Lv.$level",
                label = stringResource(R.string.level_section),
                accent = Palette.Brand400,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(22.dp))
        if (plans.isEmpty()) {
            Text(
                text = stringResource(
                    if (plansUnavailable) R.string.paywall_unavailable else R.string.paywall_loading
                ),
                style = Type.bodyM,
                color = Palette.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .cardSurface(shape = RoundedCornerShape(18.dp))
                    .padding(18.dp),
            )
            Spacer(Modifier.height(10.dp))
            if (plansUnavailable) {
                SecondaryButton(text = stringResource(R.string.paywall_retry), onClick = onRetry)
                Spacer(Modifier.height(10.dp))
            }
        }
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
        // follow the offer. Promising seven free days above renewal terms that correctly say
        // "monthly, charged immediately" is the kind of contradiction users act on and then refund.
        PrimaryButton(
            text = selected?.takeIf { it.hasFreeTrial }
                ?.let { stringResource(R.string.paywall_trial_days, it.freeTrialDays) }
                ?: stringResource(R.string.paywall_subscribe),
            onClick = { selected?.let(onPurchase) },
            enabled = selected != null,
        )
        // Korean subscription rules require the renewal terms to be legible before purchase. As the
        // button's one-line supporting text they were cut to "…" at a large font size, the price
        // and 자동 결제 going first; on their own they wrap.
        selected?.let { plan ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = renewalSummary(plan),
                style = Type.bodyM,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

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
                    Pill(text = stringResource(R.string.paywall_best_value), tint = colors.accept)
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

@Composable
private fun planTitle(plan: SubscriptionPlan): String = stringResource(
    when (plan.period) {
        PlanPeriod.ANNUAL -> R.string.paywall_plan_annual
        PlanPeriod.MONTHLY -> R.string.paywall_plan_monthly
    }
)

/**
 * Puts an annual plan on the same axis as a monthly one.
 *
 * The currency symbol is taken from the price Play already formatted rather than being rebuilt, so
 * this stays correct in every locale Play sells in.
 */
@Composable
private fun perMonthLabel(plan: SubscriptionPlan): String {
    val symbol = plan.formattedPrice.takeWhile { !it.isDigit() }.trim()
    val perMonth = plan.monthlyEquivalentMicros / 1_000_000
    return stringResource(R.string.paywall_per_month, symbol + "%,d".format(perMonth))
}

/** Renewal terms, which Korean subscription rules require to be visible before purchase. */
@Composable
private fun renewalSummary(plan: SubscriptionPlan): String = when {
    plan.hasFreeTrial && plan.period == PlanPeriod.ANNUAL ->
        stringResource(R.string.paywall_renewal_trial_annual, plan.freeTrialDays, plan.formattedPrice)
    plan.hasFreeTrial ->
        stringResource(R.string.paywall_renewal_trial_monthly, plan.freeTrialDays, plan.formattedPrice)
    plan.period == PlanPeriod.ANNUAL -> stringResource(R.string.paywall_renewal_annual, plan.formattedPrice)
    else -> stringResource(R.string.paywall_renewal_monthly, plan.formattedPrice)
}

/** What the paywall says about a billing event, or null for one that speaks for itself. */
internal fun paywallMessage(event: BillingEvent): Int? = when (event) {
    // A purchase, or one already owned, is told by the entitlement opening and the paywall closing.
    BillingEvent.PurchaseCompleted, BillingEvent.AlreadyOwned, BillingEvent.PurchaseCancelled -> null
    BillingEvent.PurchasePending -> R.string.paywall_pending
    is BillingEvent.PurchaseFailed -> R.string.paywall_purchase_error
}

/** What the paywall says after 구매 복원; a subscription found is told as a purchase is. */
internal fun paywallMessage(outcome: RestoreOutcome): Int? = when (outcome) {
    RestoreOutcome.FOUND -> null
    RestoreOutcome.NOTHING -> R.string.paywall_restore_none
    RestoreOutcome.UNREACHABLE -> R.string.paywall_restore_unreachable
}
