package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.ui.components.CatCard
import com.pushuprpg.app.ui.components.CatPortrait
import com.pushuprpg.app.ui.components.Pill
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.drawItemPicture
import com.pushuprpg.app.ui.components.giftWhyText
import com.pushuprpg.app.ui.components.itemNameRes
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.Gift
import com.pushuprpg.core.progression.ShopItem
import com.pushuprpg.core.progression.WearSlot

/**
 * 고양이 꾸미기: the cat's name and coat, and everything it has found.
 *
 * A found thing shows what it was found for, after the fact. The rest are 아직 비밀, with nothing
 * said about what they wait for: they are the cat's surprises, by the owner's decision, and a gift
 * listed with its price is a task. Tapping a found thing puts it on in place of whatever was in its
 * slot; tapping it again takes it off. The cat above wears the choice at once.
 */
@Composable
fun WardrobeScreen(
    catName: String,
    catCoat: CatCoat,
    /** The cat's name, as it should be stored, and its coat. Called on every change. */
    onCatChange: (String, CatCoat) -> Unit,
    wearing: Set<CatItem>,
    /** Every gift found so far. */
    found: Set<Gift>,
    /** The gifts that were new as the screen opened: marked for this visit. */
    fresh: Set<Gift>,
    onWear: (CatItem) -> Unit,
    onTakeOff: (WearSlot) -> Unit,
    modifier: Modifier = Modifier,
    /** 츄르 in hand and what it has bought; see [com.pushuprpg.core.progression.Shop]. */
    churu: Int = 0,
    bought: Set<CatItem> = emptySet(),
    /** Asked once the user has said yes; the caller pays and puts it on. */
    onBuy: (ShopItem) -> Unit = {},
) {
    var asking by remember { mutableStateOf<ShopItem?>(null) }
    asking?.let { item ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.shop_confirm_title, stringResource(itemNameRes(item.item)))) },
            text = { Text(stringResource(R.string.shop_confirm_body, item.price, churu)) },
            confirmButton = {
                TextButton(onClick = { asking = null; onBuy(item) }) { Text(stringResource(R.string.shop_buy)) }
            },
            dismissButton = {
                TextButton(onClick = { asking = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.wardrobe_title),
            style = Type.headline,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(16.dp))
        CatPortrait(
            coat = catCoat,
            wear = wearing,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        )
        Spacer(Modifier.height(16.dp))
        // No picture of its own: the cat is right above it.
        CatCard(name = catName, coat = catCoat, onChange = onCatChange, showCat = false)

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.wardrobe_found, found.size),
            style = Type.titleM,
            color = Palette.TextPrimary,
        )
        if (found.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.wardrobe_hint),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Gift.entries.chunked(TILES_PER_ROW).forEach { row ->
            // As tall as the tallest tile in it, so a worn or new one's pill does not leave the rest short.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { gift ->
                    val worn = gift.item in wearing
                    GiftTile(
                        gift = gift,
                        found = gift in found,
                        fresh = gift in fresh,
                        worn = worn,
                        coat = catCoat,
                        onClick = { if (worn) onTakeOff(gift.item.slot) else onWear(gift.item) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                // A short last row keeps its tiles the width of the rest.
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        val secrets = Gift.entries.size - found.size
        if (secrets > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.wardrobe_secret_note, secrets),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
        }

        // The shop, by the owner's decision (2026-10-01): 츄르 from set records buys what no gift
        // brings. Unlike the gifts, everything on the shelf shows what it is and what it costs.
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.shop_title),
                style = Type.titleM,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Pill(text = stringResource(R.string.shop_churu, churu), tint = Palette.Deep)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.shop_hint),
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        ShopItem.entries.chunked(TILES_PER_ROW).forEach { row ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { item ->
                    val owned = item.item in bought
                    val worn = item.item in wearing
                    ShopTile(
                        item = item,
                        owned = owned,
                        worn = worn,
                        affordable = churu >= item.price,
                        coat = catCoat,
                        onClick = {
                            when {
                                worn -> onTakeOff(item.item.slot)
                                owned -> onWear(item.item)
                                churu >= item.price -> asking = item
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ShopTile(
    item: ShopItem,
    owned: Boolean,
    worn: Boolean,
    affordable: Boolean,
    coat: CatCoat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val tappable = owned || affordable
    Column(
        modifier = modifier
            .cardSurface(shape = shape, color = if (worn) Palette.Bg3 else Palette.Bg2)
            .then(if (worn) Modifier.border(2.dp, Palette.Brand600, shape) else Modifier)
            .then(if (tappable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            ItemPicture(item.item, coat, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(itemNameRes(item.item)),
            style = Type.labelL,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Pill(
            text = when {
                worn -> stringResource(R.string.wardrobe_wearing)
                owned -> stringResource(R.string.shop_owned)
                else -> stringResource(R.string.shop_price, item.price)
            },
            tint = when {
                worn -> Palette.Brand400
                owned -> Palette.TextSecondary
                affordable -> Palette.Deep
                else -> Palette.TextTertiary
            },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private const val TILES_PER_ROW = 3

@Composable
private fun GiftTile(
    gift: Gift,
    found: Boolean,
    fresh: Boolean,
    worn: Boolean,
    coat: CatCoat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .cardSurface(shape = shape, color = if (worn) Palette.Bg3 else Palette.Bg2)
            .then(if (worn) Modifier.border(2.dp, Palette.Brand600, shape) else Modifier)
            .then(if (found) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (found) ItemPicture(gift.item, coat, Modifier.fillMaxSize()) else WrappedGift(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (found) stringResource(itemNameRes(gift.item)) else stringResource(R.string.wardrobe_secret),
            style = Type.labelL,
            color = if (found) Palette.TextPrimary else Palette.TextTertiary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (found) {
            Text(
                text = giftWhyText(gift),
                style = Type.labelM,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        when {
            worn -> Pill(
                text = stringResource(R.string.wardrobe_wearing),
                tint = Palette.Brand400,
                modifier = Modifier.padding(top = 6.dp),
            )
            fresh && found -> Pill(
                text = stringResource(R.string.wardrobe_new),
                tint = colors.accept,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A found thing, on the user's own cat: see [drawItemPicture]. */
@Composable
private fun ItemPicture(item: CatItem, coat: CatCoat, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(10.dp))) { drawItemPicture(item, coat) }
}

/** A gift still wrapped: what a secret looks like. */
@Composable
private fun WrappedGift(modifier: Modifier = Modifier) {
    val ink = Palette.TextTertiary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val box = Size(w * 0.56f, h * 0.38f)
        val left = (w - box.width) / 2f
        val top = h * 0.46f
        val lidH = h * 0.12f
        drawRoundRect(ink.copy(alpha = 0.35f), topLeft = Offset(left, top), size = box, cornerRadius = CornerRadius(w * 0.04f))
        drawRoundRect(
            ink.copy(alpha = 0.55f),
            topLeft = Offset(left - w * 0.04f, top - lidH),
            size = Size(box.width + w * 0.08f, lidH),
            cornerRadius = CornerRadius(w * 0.03f),
        )
        drawRect(ink, topLeft = Offset(w / 2f - w * 0.04f, top - lidH), size = Size(w * 0.08f, box.height + lidH))
        listOf(-1f, 1f).forEach { side ->
            drawCircle(ink, radius = w * 0.075f, center = Offset(w / 2f + side * w * 0.085f, top - lidH - h * 0.05f))
        }
    }
}
