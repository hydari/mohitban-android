package ir.mohitban.app.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.mohitban.app.container
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Camera / gallery picker. For receipts set [rawCameraFile] to receive the untouched camera
 * file path (so it can be cropped); otherwise the picture is imported into private storage
 * and [onImported] receives the stored relative path.
 */
class ImagePickerHandle internal constructor(
    val openSheet: () -> Unit,
    val takePhoto: () -> Unit,
    val pickGallery: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberImagePicker(
    prefix: String,
    onImported: (String) -> Unit,
    rawCameraFile: ((File) -> Unit)? = null,
): ImagePickerHandle {
    val context = LocalContext.current
    val files = context.container.files
    val scope = rememberCoroutineScope()
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(false) }
    val onImportedLatest by rememberUpdatedState(onImported)
    val rawLatest by rememberUpdatedState(rawCameraFile)

    fun importUri(uri: Uri) {
        scope.launch {
            val stored = withContext(Dispatchers.IO) { files.importImage(uri, prefix) }
            if (stored != null) onImportedLatest(stored) else Toast.makeText(context, "خواندن تصویر ممکن نشد", Toast.LENGTH_SHORT).show()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCamera
        if (ok && path != null) {
            val f = File(path)
            val raw = rawLatest
            if (raw != null) raw(f) else importUri(Uri.fromFile(f))
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val raw = rawLatest
            if (raw != null) {
                scope.launch {
                    val f = withContext(Dispatchers.IO) {
                        val bmp = files.decodeUri(uri, 2400) ?: return@withContext null
                        val out = files.newCameraFile()
                        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                        out
                    }
                    if (f != null) raw(f)
                }
            } else {
                importUri(uri)
            }
        }
    }

    val takePhoto = {
        val f = files.newCameraFile()
        pendingCamera = f.absolutePath
        runCatching { camera.launch(files.uriFor(f)) }
            .onFailure { Toast.makeText(context, "دوربین در دسترس نیست", Toast.LENGTH_SHORT).show() }
        Unit
    }
    val pickGallery = { runCatching { gallery.launch("image/*") }; Unit }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp)) {
                SheetRow(Icons.Outlined.PhotoCamera, "گرفتن عکس با دوربین") { sheet = false; takePhoto() }
                SheetRow(Icons.Outlined.PhotoLibrary, "انتخاب از گالری") { sheet = false; pickGallery() }
            }
        }
    }
    return remember(prefix) { ImagePickerHandle({ sheet = true }, takePhoto, pickGallery) }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircleIcon(icon, MColors.GreenLight, MColors.Green, size = 42.dp, iconSize = 21.dp)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
