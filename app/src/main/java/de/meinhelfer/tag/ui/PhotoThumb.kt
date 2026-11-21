package de.meinhelfer.tag.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.media.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Quadratische Vorschau eines lokal gespeicherten Fotos. */
@Composable
fun PhotoThumb(photoName: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photoName) {
        value = withContext(Dispatchers.IO) {
            PhotoStore.decodeSampled(PhotoStore.file(context, photoName), 300)?.asImageBitmap()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "Foto der Packung",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(MaterialTheme.shapes.small),
        )
    }
}
