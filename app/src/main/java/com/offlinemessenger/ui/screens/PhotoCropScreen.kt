package com.offlinemessenger.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemessenger.ui.theme.IOSBlack
import com.offlinemessenger.ui.theme.IOSBlue
import com.offlinemessenger.ui.theme.IOSGray6
import com.offlinemessenger.ui.theme.IOSSecondary
import com.offlinemessenger.ui.theme.IOSText
import java.io.ByteArrayOutputStream

/**
 * Profile-photo editor: pinch to zoom, drag to position, rotate.
 * Saves a 192px square JPEG. Full-screen overlay; onDone(null) = cancel.
 */
@Composable
fun PhotoCropScreen(
    uri: Uri,
    onDone: (ByteArray?) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf(loadSampledBitmap(context, uri)) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    if (bitmap == null) {
        onDone(null)
        return
    }
    val bmp = bitmap!!

    Scaffold(containerColor = IOSBlack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onDone(null) }) {
                    Icon(Icons.Filled.Close, "cancel", tint = IOSText)
                }
                Text("Edit photo", color = IOSText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = {
                    bitmap = rotateBitmap(bmp, 90f)
                    scale = 1f
                    offset = Offset.Zero
                }) {
                    Icon(Icons.Filled.RotateRight, "rotate", tint = IOSBlue)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Drag to position · pinch to zoom",
                color = IOSSecondary, fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))

            // Square crop viewport
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                val sideDp = maxWidth
                val sidePx = with(density) { sideDp.toPx() }
                // Cover scale: bitmap scaled to fill the square.
                val s0 = maxOf(sidePx / bmp.width, sidePx / bmp.height)

                Box(
                    modifier = Modifier
                        .size(sideDp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(IOSGray6)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(sideDp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .pointerInput(bmp) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val ns = (scale * zoom).coerceIn(1f, 4f)
                                    scale = ns
                                    // Recompute clamp with the new scale.
                                    val dw = bmp.width * s0 * ns
                                    val dh = bmp.height * s0 * ns
                                    val mx = ((dw - sidePx) / 2).coerceAtLeast(0f)
                                    val my = ((dh - sidePx) / 2).coerceAtLeast(0f)
                                    offset = Offset(
                                        (offset.x + pan.x).coerceIn(-mx, mx),
                                        (offset.y + pan.y).coerceIn(-my, my),
                                    )
                                }
                            },
                    )
                }
                // Save uses the same math.
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = CircleShape,
                    color = IOSBlue,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        onDone(cropSquare(bmp, sidePx, s0, scale, offset))
                    },
                ) {
                    Icon(
                        Icons.Filled.Check, "save",
                        tint = IOSText,
                        modifier = Modifier.padding(18.dp).size(28.dp),
                    )
                }
            }
        }
    }
}

/** Extracts the visible square region and returns a 192px JPEG. */
private fun cropSquare(
    bmp: Bitmap,
    sidePx: Float,
    s0: Float,
    scale: Float,
    offset: Offset,
): ByteArray? {
    return runCatching {
        val total = s0 * scale
        val sizePx = sidePx / total
        // Top-left of the visible square in bitmap coordinates.
        val x0 = ((-sidePx / 2 - offset.x) / total + bmp.width / 2)
            .toInt().coerceIn(0, bmp.width - 1)
        val y0 = ((-sidePx / 2 - offset.y) / total + bmp.height / 2)
            .toInt().coerceIn(0, bmp.height - 1)
        val size = sizePx.toInt().coerceIn(1, minOf(bmp.width - x0, bmp.height - y0))
        val cropped = Bitmap.createBitmap(bmp, x0, y0, size, size)
        val out192 = Bitmap.createScaledBitmap(cropped, 192, 192, true)
        val bos = ByteArrayOutputStream()
        out192.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        bos.toByteArray()
    }.getOrNull()
}

private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Decodes the image sampled down so the longest side is <= maxDim. */
fun loadSampledBitmap(context: Context, uri: Uri, maxDim: Int = 1024): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()
}
