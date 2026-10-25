package ir.mohitban.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import ir.mohitban.app.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a picture stored by [ir.mohitban.app.data.files.FileStore] off the main thread. */
@Composable
fun rememberStoredImage(path: String?, maxSize: Int = 1200): State<ImageBitmap?> {
    val files = LocalContext.current.container.files
    return produceState<ImageBitmap?>(initialValue = null, path, maxSize) {
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            files.loadBitmap(path, maxSize)?.asImageBitmap()
        }
    }
}
