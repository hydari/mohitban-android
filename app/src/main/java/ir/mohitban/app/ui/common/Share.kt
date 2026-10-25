package ir.mohitban.app.ui.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object Share {

    /** Opens the Android share sheet (Bale, Eitaa, Rubika, Telegram, WhatsApp, Bluetooth …). */
    fun file(context: Context, file: File, mime: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "ارسال فرم").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(context, "برنامه‌ای برای ارسال پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }

    /** Sends a ready PDF to the system print service (or "Save as PDF"). */
    fun print(context: Context, file: File, jobName: String) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                runCatching {
                    file.inputStream().use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure { callback.onWriteFailed(it.message) }
            }
        }
        runCatching { pm.print(jobName, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()) }
    }
}
