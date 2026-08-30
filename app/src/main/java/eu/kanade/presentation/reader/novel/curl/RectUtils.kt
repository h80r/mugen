package eu.kanade.presentation.reader.novel.curl

// Vendored from io.github.oleksandrbalan:pagecurl 1.5.1 (Apache 2.0), github.com/oleksandrbalan/pagecurl.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

internal fun Rect.multiply(size: IntSize): Rect =
    Rect(
        topLeft = Offset(size.width * left, size.height * top),
        bottomRight = Offset(size.width * right, size.height * bottom),
    )
