package com.yg.moduread.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * PDF 全屏查看叠加层。
 *
 * 为什么是「普通 Box」而不是 Popup / Dialog：
 *  - Dialog 在本项目实测白屏，且界面树不可读
 *  - Popup 尺寸由内容决定，fillMaxSize 拿不到屏幕高度，上下留黑边
 *
 * 作为普通 Box 叠加在界面根部时，尺寸天然由父约束（整屏）决定，
 * 手势也不会与列表竞争 —— 这是最朴素也最可控的做法。
 */
@Composable
fun PdfFullscreenOverlay(
    bitmap: Bitmap,
    label: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.97f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MAX_OVERLAY_ZOOM)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
        Text(
            text = if (scale > 1.01f) {
                label + "   " + (scale * 100).toInt() + "%"
            } else {
                label + "   双指缩放 · 点任意处关闭"
            },
            fontSize = Type.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .clip(Shapes.chip)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f))
                .padding(horizontal = Sp.m, vertical = Sp.xs)
        )
    }
}

/** 叠加层缩放上限。位图按 2 倍渲染，4 倍视觉放大已接近可辨认极限。 */
private const val MAX_OVERLAY_ZOOM = 4f
