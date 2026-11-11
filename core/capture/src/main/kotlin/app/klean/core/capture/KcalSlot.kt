package app.klean.core.capture

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.Space
import app.klean.shared.diet.CalorieInput
import kotlinx.coroutines.launch

/**
 * The optional calorie slot: `300`, `200g` or `150x100x200` (kcal per amount × what you ate). Shows the
 * result as you type, and an ⓘ with the syntax. Used by the capture sheet (Food) and the entry screen.
 */
@Composable
fun KcalSlot(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.replace('\n', ' ')) },
            label = { Text(stringResource(R.string.capture_kcal)) },
            singleLine = true,
            enabled = enabled,
            // Text, not Number: "x" and "g" are part of the syntax.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.width(KCAL_FIELD_WIDTH).testTag(KCAL_TAG),
        )
        KcalHelp()
        kcalPreview(value)?.let { p ->
            Text(
                p,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** What the slot will save, in words; null while it's empty. */
@Composable
fun kcalPreview(value: String): String? = when (val p = CalorieInput.parse(value)) {
    CalorieInput.Empty -> null
    CalorieInput.Invalid -> stringResource(R.string.capture_kcal_invalid)
    is CalorieInput.Kcal -> stringResource(R.string.capture_kcal_preview, p.kcal)
    is CalorieInput.PerAmount -> stringResource(R.string.capture_kcal_preview, p.total)
    is CalorieInput.Grams -> stringResource(R.string.capture_kcal_grams, p.grams)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KcalHelp() {
    val tip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = { RichTooltip { Text(stringResource(R.string.capture_kcal_help)) } },
        state = tip,
    ) {
        IconButton(onClick = { scope.launch { tip.show() } }, modifier = Modifier.size(Space.touchTarget)) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.capture_kcal_help_label))
        }
    }
}

const val KCAL_TAG = "kcal-slot"
private val KCAL_FIELD_WIDTH = 156.dp
