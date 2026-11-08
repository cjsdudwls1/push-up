package com.pushuprpg.app.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.pushuprpg.app.BuildConfig

/** An anchored banner. Its height is held before the ad arrives, so nothing above it jumps. */
@Composable
fun AdBanner(ads: Ads, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val width = maxWidth.value.toInt()
        val size = remember(width) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width) }
        val view = remember(size) {
            AdView(context).apply {
                setAdSize(size)
                adUnitId = BuildConfig.ADMOB_BANNER
                adListener = object : AdListener() {
                    override fun onAdImpression() = ads.log(AdPlacement.RECORDS, "show")
                    override fun onAdClicked() = ads.log(AdPlacement.RECORDS, "click")
                    override fun onAdFailedToLoad(error: LoadAdError) =
                        ads.log(AdPlacement.RECORDS, "load_error", error.code)
                }
                loadAd(AdRequest.Builder().build())
            }
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(view, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> view.pause()
                    Lifecycle.Event.ON_RESUME -> view.resume()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                view.destroy()
            }
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(size.height.dp))
    }
}
