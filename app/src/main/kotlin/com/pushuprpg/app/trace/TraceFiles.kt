package com.pushuprpg.app.trace

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.pushuprpg.app.R
import com.pushuprpg.core.trace.PoseTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a recorded run to a file and offers it to whatever the user picks.
 *
 * The file goes in the same cache directory as the share cards, because that is the one directory
 * the FileProvider exposes. It is quantized JSON: a few megabytes a minute, which a phone can send
 * over mobile data and the upload page will take.
 */
object TraceFiles {

    private const val TAG = "TraceFiles"
    private const val DIRECTORY = "share"
    private const val PREFIX = "pushup-trace-"
    private const val SUFFIX = ".json"

    /** Old traces, once a receiving app has had ten minutes to read them. */
    private const val STALE_MS = 10 * 60 * 1000L

    /** Off the main thread, and null rather than a throw: the caller says so in a toast. */
    suspend fun write(context: Context, trace: PoseTrace): Uri? = withContext(Dispatchers.IO) {
        try {
            val directory = File(context.cacheDir, DIRECTORY)
            directory.mkdirs()
            val cutoff = System.currentTimeMillis() - STALE_MS
            directory.listFiles()?.forEach { file ->
                if (file.name.startsWith(PREFIX) && file.lastModified() < cutoff) file.delete()
            }

            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(directory, PREFIX + stamp + SUFFIX)
            // Streamed: four minutes of frames is more than ten megabytes of text.
            file.outputStream().buffered().use { PoseTrace.encodeTo(trace.quantized(), it) }
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the trace", e)
            null
        }
    }

    fun chooser(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.trace_share_subject))
            // Both the flag and the ClipData, for the same reason as the share card: targets read
            // the grant from one or the other.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, context.getString(R.string.app_name), uri)
        }
        return Intent.createChooser(send, context.getString(R.string.trace_share_title))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
