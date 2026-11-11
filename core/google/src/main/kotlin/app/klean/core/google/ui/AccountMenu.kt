package app.klean.core.google.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.klean.core.design.component.AccountAvatar
import app.klean.core.design.component.AvatarSizeBar
import app.klean.core.design.component.AvatarSizeLarge
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.SectionHeader
import app.klean.core.design.component.groupRow
import app.klean.core.design.theme.Space
import app.klean.core.google.AccountActions
import app.klean.core.google.AccountsState
import app.klean.core.google.AdoptResult
import app.klean.core.google.AuthStep
import app.klean.core.google.GoogleAccount
import app.klean.core.google.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Links at the foot of the menu. Hidden while null (Klean has no hosted policy pages yet). */
object KleanLinks {
    val PRIVACY_URL: String? = null
    val TERMS_URL: String? = null
}

const val MANAGE_GOOGLE_ACCOUNT_URL = "https://myaccount.google.com/"

data class AccountMenuUi(
    val accounts: AccountsState = AccountsState.None,
    val busy: Boolean = false,
    /** Consent screen to launch once. */
    val consent: IntentSender? = null,
    /** Set once an account was picked: the menu closes. */
    val done: Boolean = false,
    /** A calm one-line note (string res), or null. */
    val note: Int? = null,
)

/** The menu's state and actions; built by the screen from the shell's [AccountActions] (no Hilt). */
class AccountMenuViewModel(private val actions: AccountActions) : ViewModel() {
    private val local = MutableStateFlow(AccountMenuUi())
    /** Picking another account closes the menu once it is current; connecting or reconnecting keeps it open. */
    private var closeAfterGrant = false

    val ui: StateFlow<AccountMenuUi> = combine(local, actions.state) { l, a -> l.copy(accounts = a) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    /** "Connect Google Tasks", "Add another account" ([pick]: Google's account picker) and "Show your Google photo". */
    fun connect(pick: Boolean = false) = run(close = pick) { actions.connect(pick) }

    fun switchTo(email: String) {
        if (email == ui.value.accounts.current?.email) {
            local.update { it.copy(done = true) }
            return
        }
        run(close = true) { actions.switchTo(email) }
    }

    fun onConsentResult(data: Intent?) = viewModelScope.launch {
        local.update { it.copy(consent = null) }
        handle(actions.finishConsent(data))
    }

    private fun run(close: Boolean, step: suspend () -> AuthStep) = viewModelScope.launch {
        closeAfterGrant = close
        local.update { it.copy(busy = true, note = null) }
        handle(step())
    }

    private suspend fun handle(step: AuthStep) {
        when (step) {
            AuthStep.Granted -> {
                val r = actions.completeGrant()
                local.update { it.copy(busy = false, done = closeAfterGrant && r is AdoptResult.Identified) }
            }
            is AuthStep.NeedsConsent -> local.update { it.copy(consent = step.intentSender) }
            AuthStep.Declined -> local.update { it.copy(busy = false) }
            is AuthStep.Unavailable -> local.update { it.copy(busy = false, note = R.string.google_unavailable_now) }
        }
    }
}

/** Everything the menu's rows do, so the content can be tested without a view model. */
data class AccountMenuActions(
    val onClose: () -> Unit = {},
    val onManageGoogle: (String) -> Unit = {},
    val onSwitch: (String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onManageDevice: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onShowPhoto: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onHelp: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
)

/**
 * The account menu, Google's full-screen pattern in Klean's look: ✕, the email, a large avatar, "Hi, {first name}",
 * "Manage your Google Account", an expandable Switch account list, then More from Klean. Opened from Today's avatar.
 */
@Composable
fun AccountMenuScreen(
    actions: AccountActions,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    vm: AccountMenuViewModel = viewModel { AccountMenuViewModel(actions) },
) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r -> vm.onConsentResult(r.data) }
    LaunchedEffect(s.consent) { s.consent?.let { launcher.launch(IntentSenderRequest.Builder(it).build()) } }
    LaunchedEffect(s.done) { if (s.done) onClose() }
    val photos = s.accounts.accounts.associate { it.email to rememberAccountPhoto(s.accounts.photos[it.email]) }
    AccountMenuContent(
        ui = s,
        photos = photos,
        actions = AccountMenuActions(
            onClose = onClose,
            onManageGoogle = { email -> ctx.openUri("$MANAGE_GOOGLE_ACCOUNT_URL?authuser=${Uri.encode(email)}") },
            onSwitch = { vm.switchTo(it) },
            onAdd = { vm.connect(pick = true) },
            onManageDevice = { ctx.startSafely(Intent(Settings.ACTION_SYNC_SETTINGS)) },
            onConnect = { vm.connect() },
            onShowPhoto = { vm.connect() },
            onSettings = onSettings,
            onHelp = onHelp,
            onOpenUrl = { ctx.openUri(it) },
        ),
    )
}

private fun Context.openUri(uri: String) = startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser or settings screen for it on this phone: nothing to open.
    }
}

@Composable
fun AccountMenuContent(
    ui: AccountMenuUi,
    photos: Map<String, ImageBitmap?>,
    actions: AccountMenuActions,
    startExpanded: Boolean = false,
    privacyUrl: String? = KleanLinks.PRIVACY_URL,
    termsUrl: String? = KleanLinks.TERMS_URL,
) {
    val a = ui.accounts
    val current = a.current
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    KleanScreen(
        title = current?.email ?: stringResource(R.string.account_menu_title),
        showAdd = false,
        header = { MenuTop(current?.email ?: stringResource(R.string.account_menu_title), actions.onClose) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item(key = "who") {
                Who(a, current?.let { photos[it.email] }, ui.busy, actions)
            }
            if (a.connected) {
                item(key = "switch") {
                    SwitchAccount(a, photos, expanded, onToggle = { expanded = !expanded }, actions)
                }
            } else if (a.available) {
                item(key = "connect") {
                    Box(Modifier.padding(top = Space.lg).groupRow(0, 1)) {
                        ListRow(
                            title = stringResource(R.string.account_connect),
                            supporting = stringResource(R.string.account_connect_body),
                            leading = { Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = actions.onConnect.takeUnless { ui.busy },
                        )
                    }
                }
            }
            item(key = "more_h") { SectionHeader(stringResource(R.string.account_more_from)) }
            item(key = "more") {
                Column(Modifier.testTag(CLUTTER_EXEMPT)) {
                    Box(Modifier.groupRow(0, 2)) {
                        ListRow(
                            title = stringResource(R.string.account_settings),
                            leading = { Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = actions.onSettings,
                        )
                    }
                    Box(Modifier.groupRow(1, 2)) {
                        ListRow(
                            title = stringResource(R.string.account_help),
                            leading = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = actions.onHelp,
                        )
                    }
                }
            }
            if (privacyUrl != null || termsUrl != null) {
                item(key = "legal") { Legal(privacyUrl, termsUrl, actions.onOpenUrl) }
            }
            ui.note?.let { n ->
                item(key = "note") {
                    Text(
                        stringResource(n),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The top row: the email centred, ✕ on the right. */
@Composable
private fun MenuTop(title: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Space.xxs, vertical = Space.xxs), contentAlignment = Alignment.Center) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Space.touchTarget).semantics { heading() },
        )
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd).size(Space.touchTarget)) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.account_close))
        }
    }
}

/** The large avatar and greeting; the quiet "Show your Google photo" for an account connected before the profile. */
@Composable
private fun Who(a: AccountsState, photo: ImageBitmap?, busy: Boolean, actions: AccountMenuActions) {
    val current = a.current
    Column(
        Modifier.fillMaxWidth().padding(top = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        AccountAvatar(photo = photo, initial = current?.initial, size = AvatarSizeLarge)
        when {
            current != null -> {
                Text(stringResource(R.string.account_hi, current.firstName), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                OutlinedButton(onClick = { actions.onManageGoogle(current.email) }, modifier = Modifier.heightIn(min = Space.touchTarget)) {
                    Text(stringResource(R.string.account_manage_google))
                }
            }
            a.connected -> {
                Text(stringResource(R.string.account_connected_unknown), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                TextButton(onClick = actions.onShowPhoto, enabled = !busy, modifier = Modifier.heightIn(min = Space.touchTarget)) {
                    Text(stringResource(R.string.account_show_photo))
                }
                Text(
                    stringResource(R.string.account_show_photo_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> Text(
                stringResource(R.string.account_no_google),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.md),
            )
        }
    }
}

/** "Switch account": a row that expands into every account (a tick on the current one), Add, and Manage. */
@Composable
private fun SwitchAccount(a: AccountsState, photos: Map<String, ImageBitmap?>, expanded: Boolean, onToggle: () -> Unit, actions: AccountMenuActions) {
    val rows = if (expanded) a.accounts.size + 2 else 0
    val count = rows + 1
    Column(Modifier.padding(top = Space.lg)) {
        Box(Modifier.groupRow(0, count)) {
            ListRow(
                title = stringResource(R.string.account_switch),
                trailing = {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                onClick = onToggle,
            )
        }
        if (expanded) {
            Column(Modifier.testTag(CLUTTER_EXEMPT)) {
                a.accounts.forEachIndexed { i, acc ->
                    Box(Modifier.groupRow(i + 1, count)) { AccountRow(acc, photos[acc.email], acc.email == a.current?.email) { actions.onSwitch(acc.email) } }
                }
                Box(Modifier.groupRow(count - 2, count)) {
                    ListRow(
                        title = stringResource(R.string.account_add),
                        leading = { Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = actions.onAdd,
                    )
                }
                Box(Modifier.groupRow(count - 1, count)) {
                    ListRow(
                        title = stringResource(R.string.account_manage_device),
                        leading = { Icon(Icons.Rounded.ManageAccounts, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = actions.onManageDevice,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(acc: GoogleAccount, photo: ImageBitmap?, current: Boolean, onClick: () -> Unit) {
    val desc = stringResource(R.string.account_current)
    ListRow(
        title = acc.name ?: acc.email,
        supporting = acc.email.takeIf { acc.name != null },
        leading = { AccountAvatar(photo = photo, initial = acc.initial, size = AvatarSizeBar) },
        trailing = if (current) {
            { Icon(Icons.Rounded.Check, contentDescription = desc, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        onClick = onClick,
    )
}

@Composable
private fun Legal(privacyUrl: String?, termsUrl: String?, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.md), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        privacyUrl?.let { u -> TextButton(onClick = { onOpen(u) }) { Text(stringResource(R.string.account_privacy)) } }
        if (privacyUrl != null && termsUrl != null) Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
        termsUrl?.let { u -> TextButton(onClick = { onOpen(u) }) { Text(stringResource(R.string.account_terms)) } }
    }
}
