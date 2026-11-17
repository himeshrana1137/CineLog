package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.LanguageUtils
import com.example.data.model.WatchedMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

enum class SocialCardFormat(
    val widthPx: Int,
    val heightPx: Int,
    val label: String,
    val aspectText: String,
) {
    STORY(1080, 1920, "Story / Status", "1080 × 1920"),
    SQUARE(1080, 1080, "Square Post", "1080 × 1080"),
}

object MediaSaver {

    suspend fun downloadBitmap(imageUrl: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                doInput = true
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Failed to fetch image (HTTP ${connection.responseCode})")
            }
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
                    ?: throw IllegalStateException("Unable to decode image bitmap.")
            }
        }
    }

    suspend fun renderSocialCardBitmap(
        movie: WatchedMovie,
        posterBitmap: Bitmap?,
        format: SocialCardFormat,
        accentColorInt: Int,
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = format.widthPx
        val h = format.heightPx
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 1. Atmospheric dark cinema background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(
                    blendColor(0xFF0B0B0F.toInt(), accentColorInt, 0.24f),
                    0xFF14141B.toInt(),
                    0xFF0B0B0F.toInt(),
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Subtle accent spotlight glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentColorInt and 0x00FFFFFF) or (0x2A shl 24)
        }
        canvas.drawCircle(w * 0.85f, h * 0.14f, w * 0.42f, glowPaint)

        // 2. Card inner frame
        val pad = if (format == SocialCardFormat.STORY) 86f else 64f
        val frameRect = RectF(pad, pad, w - pad, h - pad)
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF181822.toInt()
        }
        canvas.drawRoundRect(frameRect, 48f, 48f, framePaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = (accentColorInt and 0x00FFFFFF) or (0x88 shl 24)
        }
        canvas.drawRoundRect(frameRect, 48f, 48f, borderPaint)

        // 3. Top header: CINELOG badge + Year / Media Type (Spoiler-free!)
        val innerLeft = pad + 54f
        val innerRight = w - pad - 54f
        val innerWidth = (innerRight - innerLeft).toInt()
        var cursorY = pad + 56f

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorInt
            textSize = if (format == SocialCardFormat.STORY) 42f else 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        canvas.drawText("CINELOG", innerLeft, cursorY + 34f, brandPaint)

        val metaText = buildString {
            append(if (movie.isTv) "TV SERIES" else "FILM")
            if (movie.releaseYear != "—") append(" • ${movie.releaseYear}")
            val lang = LanguageUtils.displayName(movie.originalLanguage)
            if (lang.isNotBlank()) append(" • $lang")
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA0A3B1.toInt()
            textSize = if (format == SocialCardFormat.STORY) 28f else 24f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(metaText, innerRight, cursorY + 32f, metaPaint)
        cursorY += if (format == SocialCardFormat.STORY) 96f else 74f

        // 4. Poster image (2:3 ratio)
        val posterHeight = if (format == SocialCardFormat.STORY) 860f else 440f
        val posterWidth = posterHeight * (2f / 3f)
        val posterLeft = (w - posterWidth) / 2f
        val posterRect = RectF(posterLeft, cursorY, posterLeft + posterWidth, cursorY + posterHeight)
        val posterBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF232330.toInt()
        }
        canvas.drawRoundRect(posterRect, 32f, 32f, posterBgPaint)

        if (posterBitmap != null) {
            val saveCount = canvas.save()
            val clipPath = android.graphics.Path().apply {
                addRoundRect(posterRect, 32f, 32f, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            val srcRect = centerCropRect(posterBitmap.width, posterBitmap.height, posterWidth / posterHeight)
            canvas.drawBitmap(posterBitmap, srcRect, posterRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restoreToCount(saveCount)
        }
        cursorY += posterHeight + if (format == SocialCardFormat.STORY) 64f else 40f

        // 5. Title (Spoiler-free: NO overview/plot!)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (format == SocialCardFormat.STORY) 58f else 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder
            .obtain(movie.title, 0, movie.title.length, titlePaint, innerWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(innerLeft, cursorY)
        titleLayout.draw(canvas)
        canvas.restore()
        cursorY += titleLayout.height + if (format == SocialCardFormat.STORY) 44f else 28f

        // 6. User's Star Rating
        val starStr = if (movie.userRating > 0) {
            "★".repeat(movie.userRating) + "☆".repeat(5 - movie.userRating) + "   ${movie.userRating}/5"
        } else {
            "WATCHED ON CINELOG"
        }
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5C518.toInt()
            textSize = if (format == SocialCardFormat.STORY) 46f else 36f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(starStr, w / 2f, cursorY + 38f, starPaint)
        cursorY += if (format == SocialCardFormat.STORY) 90f else 66f

        // 7. Emoji Reaction Pill (if present)
        val reaction = movie.quickReaction
        if (reaction != null) {
            val reactionLabel = "${reaction.emoji}  ${reaction.label}"
            val reactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = if (format == SocialCardFormat.STORY) 36f else 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textW = reactPaint.measureText(reactionLabel)
            val pillW = textW + 84f
            val pillH = if (format == SocialCardFormat.STORY) 78f else 64f
            val pillLeft = (w - pillW) / 2f
            val pillRect = RectF(pillLeft, cursorY, pillLeft + pillW, cursorY + pillH)
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (accentColorInt and 0x00FFFFFF) or (0x44 shl 24)
            }
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBg)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, borderPaint)
            reactPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(reactionLabel, w / 2f, cursorY + pillH * 0.65f, reactPaint)
        }

        // 8. Spoiler-Free footer badge
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA0A3B1.toInt()
            textSize = 24f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText(
            "SPOILER-FREE LOG • TRACKED WITH CINELOG",
            w / 2f,
            h - pad - 44f,
            footerPaint,
        )

        out
    }

    suspend fun cropToNineSixteenBitmap(
        source: Bitmap,
        offsetXFraction: Float,
        offsetYFraction: Float,
        zoom: Float,
    ): Bitmap = withContext(Dispatchers.Default) {
        val targetWidth = 1080
        val targetHeight = 1920
        val targetAspect = 9f / 16f

        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        val srcAspect = srcW / srcH

        // Base crop window matching 9:16 inside source
        val baseCropW: Float
        val baseCropH: Float
        if (srcAspect > targetAspect) {
            baseCropH = srcH
            baseCropW = baseCropH * targetAspect
        } else {
            baseCropW = srcW
            baseCropH = baseCropW / targetAspect
        }

        val clampedZoom = zoom.coerceIn(1f, 3f)
        val cropW = (baseCropW / clampedZoom).coerceAtLeast(16f)
        val cropH = (baseCropH / clampedZoom).coerceAtLeast(16f)

        val maxSlackX = (srcW - cropW).coerceAtLeast(0f)
        val maxSlackY = (srcH - cropH).coerceAtLeast(0f)

        val left = ((0.5f + offsetXFraction).coerceIn(0f, 1f) * maxSlackX)
        val top = ((0.5f + offsetYFraction).coerceIn(0f, 1f) * maxSlackY)

        val srcRect = Rect(
            left.roundToInt().coerceIn(0, source.width - 1),
            top.roundToInt().coerceIn(0, source.height - 1),
            (left + cropW).roundToInt().coerceIn(1, source.width),
            (top + cropH).roundToInt().coerceIn(1, source.height),
        )

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(
            source,
            srcRect,
            Rect(0, 0, targetWidth, targetHeight),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
        )
        output
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileNamePrefix: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safePrefix = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40)
            val fileName = "${safePrefix}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CineLog")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create MediaStore entry.")
                try {
                    resolver.openOutputStream(uri)?.use { stream ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            throw IllegalStateException("Failed to compress PNG.")
                        }
                    } ?: throw IllegalStateException("Could not open output stream.")

                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                "Saved $fileName to Pictures/CineLog"
            } else {
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.cacheDir
                val cineDir = File(baseDir, "CineLog").apply { mkdirs() }
                val outFile = File(cineDir, fileName)
                FileOutputStream(outFile).use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw IllegalStateException("Failed to compress PNG.")
                    }
                }
                MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf("image/png"), null)
                "Saved $fileName"
            }
        }
    }

    suspend fun shareBitmapViaSheet(
        context: Context,
        bitmap: Bitmap,
        fileNamePrefix: String,
        shareText: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cardsDir = File(context.cacheDir, "cards").apply { mkdirs() }
            val safePrefix = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40)
            val file = File(cardsDir, "${safePrefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IllegalStateException("Failed to write PNG for sharing.")
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share CineLog Card").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    private fun centerCropRect(srcW: Int, srcH: Int, targetAspect: Float): Rect {
        val srcAspect = srcW.toFloat() / srcH.toFloat()
        return if (srcAspect > targetAspect) {
            val cropW = (srcH * targetAspect).roundToInt()
            val left = (srcW - cropW) / 2
            Rect(left, 0, left + cropW, srcH)
        } else {
            val cropH = (srcW / targetAspect).roundToInt()
            val top = (srcH - cropH) / 2
            Rect(0, top, srcW, top + cropH)
        }
    }

    private fun blendColor(base: Int, tint: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(base) * inv + Color.red(tint) * ratio).roundToInt().coerceIn(0, 255)
        val g = (Color.green(base) * inv + Color.green(tint) * ratio).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(base) * inv + Color.blue(tint) * ratio).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
