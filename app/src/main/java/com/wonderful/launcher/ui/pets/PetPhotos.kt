package com.wonderful.launcher.ui.pets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * 家具の「写真」に使う画像。選んだ画像を縮小してアプリの中に保存しておく
 * （元の写真を消したり、アクセス権が切れたりしても表示できるように）。
 */
object PetPhotos {
    private const val MAX_PX = 720

    private fun dir(context: Context) = File(context.filesDir, "pet_photos").apply { mkdirs() }

    /** 取り込んで、保存したファイル名を返す。失敗したら null */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        // スマホで撮った写真は向きの情報が別に入っているので、その通りに回す
        val rotation = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        val scale = MAX_PX.toFloat() / max(bmp.width, bmp.height)
        if (rotation != 0 || scale < 1f) {
            val m = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0) postRotate(rotation.toFloat())
            }
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (out !== bmp) bmp.recycle()
            bmp = out
        }
        val name = "photo_${UUID.randomUUID()}.jpg"
        File(dir(context), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        name
    }.getOrNull()

    fun load(context: Context, name: String): Bitmap? = runCatching {
        if (name.isEmpty() || name.contains('/') || name.contains("..")) return null
        val f = File(dir(context), name)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }.getOrNull()

    fun delete(context: Context, name: String) {
        if (name.isEmpty() || name.contains('/') || name.contains("..")) return
        runCatching { File(dir(context), name).delete() }
    }
}
