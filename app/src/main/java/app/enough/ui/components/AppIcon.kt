package app.enough.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.enough.ui.theme.EnoughType
import app.enough.ui.theme.LocalEnoughColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IconCache {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun get(ctx: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return try {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap().also { cache.put(pkg, it) }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}

@Composable
fun AppIcon(pkg: String, blocked: Boolean = false, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val c = LocalEnoughColors.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { IconCache.get(ctx, pkg) }
    }
    val inner = if (blocked) size - 10.dp else size
    val body: @Composable () -> Unit = {
        val b = bmp
        if (b != null) {
            Image(
                b, contentDescription = null, modifier = Modifier.size(inner),
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                alpha = if (blocked) 0.35f else 1f,
            )
        } else {
            Box(Modifier.size(inner).border(2.dp, c.fg), contentAlignment = Alignment.Center) {
                Txt(pkg.substringAfterLast('.').take(1).uppercase(), EnoughType.name, c.fg)
            }
        }
    }
    if (blocked) Box(Modifier.border(2.dp, c.red).padding(3.dp)) { body() } else body()
}
