package com.oam.pdftools.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.oam.pdftools.R
import android.net.Uri
import androidx.work.*
import com.oam.pdftools.App
import com.oam.pdftools.AppState
import com.oam.pdftools.kb
import com.oam.pdftools.Secrets
import com.oam.pdftools.data.Recent
import com.oam.pdftools.fileMeta
import com.oam.pdftools.pdf.PdfOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PdfWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val op = inputData.getString(OP)!!
        // Background start can be refused (Android 12+); the job still runs, just without the guarantee.
        runCatching { setForeground(foregroundInfo(op)) }
        val ins = inputData.getStringArray(IN)!!.map(Uri::parse)
        val out = Uri.parse(inputData.getString(OUT))
        val ops = PdfOps(applicationContext.contentResolver)
        var extra = Data.EMPTY
        fun single(block: () -> Unit) = block().let { listOf(out) }
        runCatching {
            val outs = when (op) {
                IMG, SCAN -> single { ops.imagesToPdf(ins, out) }
                MERGE -> single { ops.merge(ins, out) }
                COMPRESS -> single {
                    val kb = inputData.getInt(TARGET_KB, 0)
                    val allowRaster = inputData.getBoolean(ALLOW_RASTER, false)
                    if (kb <= 0) ops.compress(ins[0], out)
                    else {
                        // 1 KB = 1000 bytes here: stays under the limit whether a portal counts 1000 or 1024
                        val r = ops.compressToTarget(ins[0], out, kb * 1000L, File(applicationContext.cacheDir, "compress"), allowRaster) {
                            setProgressAsync(workDataOf(STEP to it))
                        }
                        extra = workDataOf(TARGET_KB to kb, ALLOW_RASTER to allowRaster, HIT to r.hit, RASTER to (r.mode == PdfOps.Mode.RASTER))
                    }
                }
                EXTRACT -> single { ops.extract(ins[0], pages(), out) }
                ROTATE -> single { ops.rotate(ins[0], pages().zip(inputData.getIntArray(DEGS)!!.toList()).toMap(), out) }
                TO_IMAGE -> {
                    val png = inputData.getBoolean(PNG, false)
                    extra = workDataOf(MIME to if (png) "image/png" else "image/jpeg")
                    ops.toImages(ins[0], pages().ifEmpty { null }, out, applicationContext.fileMeta(ins[0]).first.substringBeforeLast('.'),
                        inputData.getInt(DPI, 150), png)
                }
                PROTECT -> single { ops.protect(ins[0], out, password()) }
                UNLOCK -> single { ops.unlock(ins[0], out, password()) }
                ID_CARD -> single { ops.idCard(ins[0], ins.getOrNull(1), out) }
                SPLIT -> ops.split(ins[0], ranges(), out, applicationContext.fileMeta(ins[0]).first.substringBeforeLast('.'))
                else -> error("Unknown op $op")
            }
            val dao = (applicationContext as App).db.recents()
            var outSize = 0L
            outs.forEach {
                val (name, size) = applicationContext.fileMeta(it); outSize += size
                if (op != TO_IMAGE) dao.put(Recent(it.toString(), name, op, size)) // Recents = PDFs only
            }
            if (!AppState.visible) notifyDone(op, outs.size, applicationContext.fileMeta(outs.first()).first, outSize)
            Data.Builder().putAll(extra).putAll(workDataOf(
                IN_SIZE to if (op == TO_IMAGE) 0L else ins.sumOf { applicationContext.fileMeta(it).second }, OUT_SIZE to outSize,
                OUTS to outs.take(50).map(Uri::toString).toTypedArray() // Data is capped at 10 KB
            )).build()
        }.fold({ Result.success(it) }, { Result.failure(workDataOf("err" to it.message)) })
    }

    private fun password() = Secrets.take(inputData.getString(PW_KEY)) ?: error("Session expired. Please try again.")
    private fun foregroundInfo(op: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "File processing", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(applicationContext, 0,
            applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle("$op in progress…")
            .setContentText("Your file is being processed on this phone").setProgress(0, 0, true)
            .setOngoing(true).setSilent(true).setContentIntent(open).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
               else ForegroundInfo(id.hashCode(), n)
    }

    /** "Your PDF is ready" when the job finishes while the user is in another app. Tapping opens the app (result is in Recent). */
    private fun notifyDone(op: String, count: Int, name: String, size: Long) {
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        foregroundInfo(op) // ensures the channel exists
        val open = PendingIntent.getActivity(applicationContext, 1,
            applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(if (count > 1) "$count files ready" else if (op == TO_IMAGE) "Images ready" else "Your PDF is ready")
            .setContentText("$name · ${size.kb()}").setAutoCancel(true).setContentIntent(open).build()
        androidx.core.app.NotificationManagerCompat.from(applicationContext).notify(id.hashCode() + 1, n)
    }

    private fun pages() = inputData.getIntArray(PAGES)!!.toList()
    private fun ranges() = inputData.getIntArray(RANGES)!!.toList().chunked(2).map { it[0]..it[1] }

    companion object {
        const val CHANNEL = "jobs"; const val OP = "op"; const val IN = "in"; const val OUT = "out"; const val STEP = "step"; const val TARGET_KB = "targetKb"; const val HIT = "hit"; const val RASTER = "raster"; const val ALLOW_RASTER = "allowRaster"
        const val OUTS = "outs"; const val IN_SIZE = "inSize"; const val OUT_SIZE = "outSize"
        const val IMG = "Images to PDF"; const val MERGE = "Merge PDF"; const val SPLIT = "Split PDF"; const val COMPRESS = "Compress PDF"
        const val TO_IMAGE = "PDF to images"; const val DPI = "dpi"; const val PNG = "png"; const val MIME = "mime"
        const val PROTECT = "Protect PDF"; const val UNLOCK = "Unlock PDF"; const val PW_KEY = "pwKey"
        const val ID_CARD = "ID card to PDF"; const val SCAN = "Scan document"
        const val EXTRACT = "Extract pages"; const val ROTATE = "Rotate pages"
        const val PAGES = "pages"; const val DEGS = "degs"; const val RANGES = "ranges"

        fun enqueue(ctx: Context, op: String, ins: List<Uri>, out: Uri, extra: Map<String, Any> = emptyMap()): UUID {
            val req = OneTimeWorkRequestBuilder<PdfWorker>()
                .setInputData(Data.Builder().putAll(extra).putAll(workDataOf(OP to op, IN to ins.map(Uri::toString).toTypedArray(), OUT to out.toString())).build())
                .build() // TODO: setForeground() + notification for 100+ MB jobs
            WorkManager.getInstance(ctx).enqueue(req)
            return req.id
        }
    }
}
