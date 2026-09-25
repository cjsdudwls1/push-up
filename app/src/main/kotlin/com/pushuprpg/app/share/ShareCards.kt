package com.pushuprpg.app.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.pushuprpg.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Turns a result into something a person can post.
 *
 * A text-only share is a line that disappears in a chat scroll; an image is the only format that
 * survives being forwarded, and 고냥이 모드 exists to be forwarded. If anything here fails — no disk,
 * no provider, a device that has never heard of PNG — the share still happens, as text. Losing the
 * picture is annoying; losing the share is the whole point.
 */
object ShareCards {

    private const val TAG = "ShareCards"
    private const val DIRECTORY = "share"
    private const val PREFIX = "card-"
    private const val SUFFIX = ".png"

    /** Old cards, once a receiving app has had ten minutes to read them. */
    private const val STALE_MS = 10 * 60 * 1000L

    /**
     * Renders and writes the card, off the main thread.
     *
     * Returns null rather than throwing: every caller has a text fallback, and a share button that
     * does nothing is worse than a share without a picture.
     */
    suspend fun writeCard(context: Context, data: ShareCardData): Uri? = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            val directory = File(context.cacheDir, DIRECTORY)
            directory.mkdirs()
            pruneStale(directory)

            bitmap = ShareCardRenderer.render(context.resources, data)
            val file = File(directory, PREFIX + System.currentTimeMillis() + SUFFIX)
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    Log.w(TAG, "PNG encode refused; sharing as text")
                    return@withContext null
                }
                out.flush()
            }
            FileProvider.getUriForFile(context, authority(context), file)
        } catch (e: Exception) {
            // Cache writes fail for boring reasons — a full disk, a device in a weird state. None of
            // them should cost the user their share.
            Log.w(TAG, "Could not write the share card", e)
            null
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * Builds the chooser.
     *
     * The caption rides along with the image because most Korean targets (카카오톡, 인스타 스토리) take
     * one or the other depending on where it is dropped, and a card with no words next to it in a
     * group chat reads as a stray screenshot.
     */
    fun chooser(context: Context, data: ShareCardData, uri: Uri?): Intent {
        val caption = caption(context, data)
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, caption)
            if (uri == null) {
                type = "text/plain"
            } else {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                // Both the flag and the ClipData: some targets read the grant from one, some from
                // the other, and a target that reads neither shows a broken thumbnail.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(
                    context.contentResolver,
                    context.getString(R.string.app_name),
                    uri,
                )
            }
        }
        return Intent.createChooser(send, context.getString(R.string.survival_share)).apply {
            if (uri != null) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun caption(context: Context, data: ShareCardData): String = when (data) {
        is ShareCardData.Survival ->
            context.getString(R.string.survival_share_text, data.score)

        is ShareCardData.Dungeon -> context.getString(
            when {
                data.inSeconds && data.cleared -> R.string.share_text_dungeon_cleared_hold
                data.inSeconds -> R.string.share_text_dungeon_defeat_hold
                data.cleared -> R.string.share_text_dungeon_cleared
                else -> R.string.share_text_dungeon_defeat
            },
            data.dungeonName,
            if (data.inSeconds) data.heldSeconds else data.reps,
        )
    }

    private fun authority(context: Context): String = context.packageName + ".fileprovider"

    private fun pruneStale(directory: File) {
        val cutoff = System.currentTimeMillis() - STALE_MS
        directory.listFiles()?.forEach { file ->
            if (file.name.startsWith(PREFIX) && file.lastModified() < cutoff) file.delete()
        }
    }
}
