package app.klean.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.Space

/** The avatar's size in a top bar (Google's apps use 32 dp inside a 48 dp target). */
val AvatarSizeBar: Dp = 32.dp

/** The large avatar at the top of the account menu. */
val AvatarSizeLarge: Dp = 80.dp

/**
 * A round account avatar, as Google's apps show it: the profile [photo]; without one, the [initial] on a calm tonal
 * circle; without either, a neutral person icon on the same circle (Klean needs no account). With [onClick] it is a
 * 48 dp button described by [contentDescription].
 */
@Composable
fun AccountAvatar(
    photo: ImageBitmap?,
    initial: String?,
    modifier: Modifier = Modifier,
    size: Dp = AvatarSizeBar,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val face: @Composable () -> Unit = { AvatarFace(photo, initial, size) }
    if (onClick == null) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) { face() }
    } else {
        Box(
            modifier
                .size(Space.touchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription?.let { this.contentDescription = it } },
            contentAlignment = Alignment.Center,
        ) { face() }
    }
}

@Composable
private fun AvatarFace(photo: ImageBitmap?, initial: String?, size: Dp) {
    if (photo != null) {
        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        return
    }
    val letter = initial?.trim()?.takeIf { it.isNotEmpty() }?.take(1)
    val (bg, fg) = avatarColors(letter)
    Box(Modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        if (letter != null) {
            Text(
                letter,
                color = fg,
                style = if (size >= AvatarSizeLarge) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
            )
        } else {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** The initial's circle: one of the three tonal containers, picked by the letter, so each account keeps its colour. */
@Composable
private fun avatarColors(letter: String?): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    if (letter == null) return c.secondaryContainer to c.onSecondaryContainer
    return when (letter.first().code % 3) {
        0 -> c.primaryContainer to c.onPrimaryContainer
        1 -> c.tertiaryContainer to c.onTertiaryContainer
        else -> c.secondaryContainer to c.onSecondaryContainer
    }
}
