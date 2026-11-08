package app.klean.core.capture.photo

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import app.klean.core.capture.R

/** Camera and gallery launchers for attaching a photo. Photos are always optional. */
class PhotoPicker internal constructor(
    val takePhoto: () -> Unit,
    val chooseFromGallery: () -> Unit,
    /** True after the camera couldn't be opened (no camera app); show a calm note. */
    val noCamera: Boolean,
)

/**
 * Camera via TakePicture into [newCaptureUri] (a FileProvider uri under filesDir/photos), gallery via
 * PickVisualMedia(ImageOnly). The pending camera file survives the activity being recreated.
 */
@Composable
fun rememberPhotoPicker(
    newCaptureUri: () -> Pair<String, Uri>,
    onTaken: (name: String, uri: Uri, success: Boolean) -> Unit,
    onPicked: (Uri) -> Unit,
): PhotoPicker {
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var noCamera by remember { mutableStateOf(false) }
    val taken by rememberUpdatedState(onTaken)
    val picked by rememberUpdatedState(onPicked)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingName
        val uri = pendingUri?.let(Uri::parse)
        pendingName = null
        pendingUri = null
        if (name != null && uri != null) taken(name, uri, ok)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked(uri)
    }
    return PhotoPicker(
        takePhoto = {
            val (name, uri) = newCaptureUri()
            pendingName = name
            pendingUri = uri.toString()
            try {
                noCamera = false
                camera.launch(uri)
            } catch (e: ActivityNotFoundException) {
                Log.i("Klean", "no camera app", e)
                pendingName = null
                pendingUri = null
                noCamera = true
                taken(name, uri, false)
            }
        },
        chooseFromGallery = {
            noCamera = false
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        noCamera = noCamera,
    )
}

/** "Take photo" / "Choose from gallery". Place it in a Box next to the photo button it drops from. */
@Composable
fun PhotoMenu(expanded: Boolean, onDismiss: () -> Unit, picker: PhotoPicker) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.photo_take)) },
            leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
            onClick = {
                onDismiss()
                picker.takePhoto()
            },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.photo_choose)) },
            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
            onClick = {
                onDismiss()
                picker.chooseFromGallery()
            },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}
