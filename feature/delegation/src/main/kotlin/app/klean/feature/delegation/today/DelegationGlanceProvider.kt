package app.klean.feature.delegation.today

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.feature.delegation.R
import app.klean.feature.delegation.data.DelegationStatus
import app.klean.feature.delegation.notify.DelegationLinks
import app.klean.shared.module.ModuleId
import app.klean.shared.time.toLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Delegation on Today's whiteboard: what Hermes is waiting on (an approval, or an answer) needs attention and opens
 * the approval or the conversation; delegations finished today are in "so far today". No quick action: delegating
 * starts from a task or from capture ("ask Hermes to…").
 */
@Singleton
class DelegationGlanceProvider @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: dagger.Lazy<KleanDatabase>,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : TodayGlanceProvider {
    override val moduleId: ModuleId = ModuleId.DELEGATION

    override fun glance(): Flow<GlanceContribution> = combine(
        db.get().delegationDao().observeAll(),
        db.get().approvalRequestDao().observePending(),
        db.get().taskDao().observeAll(),
    ) { delegations, approvals, tasks ->
        val live = delegations.filter { !it.sync.deleted }
        val byId = live.associateBy { it.id }
        val titles = tasks.associate { it.id to it.title }
        fun title(delegationId: String) = byId[delegationId]?.let { titles[it.taskId] }.orEmpty()
        val tz = zone.current()
        val today = clock.now().toEpochMilliseconds().toLocal(tz).date
        GlanceContribution(
            attention = approvals.filter { !it.sync.deleted }.map { a ->
                GlanceRow(
                    "approval:${a.id}", moduleId, title(a.delegationId), a.sync.updatedAt,
                    detail = ctx.getString(R.string.delegation_glance_approval, a.action.replace('_', ' ')),
                    icon = Icons.Rounded.AutoAwesome, route = DelegationLinks.approval(a.id),
                )
            } + live.filter { it.status == DelegationStatus.NEEDS_INPUT }.map { d ->
                GlanceRow(
                    "delegation:${d.id}", moduleId, titles[d.taskId].orEmpty(), d.sync.updatedAt,
                    detail = ctx.getString(R.string.delegation_glance_question),
                    icon = Icons.Rounded.AutoAwesome, route = DelegationLinks.delegation(d.id),
                )
            },
            done = live.filter { it.status == DelegationStatus.DONE && it.sync.updatedAt.toLocal(tz).date == today }.map { d ->
                GlanceRow(
                    "delegation:${d.id}", moduleId, titles[d.taskId].orEmpty(), d.sync.updatedAt,
                    detail = ctx.getString(R.string.delegation_glance_done),
                    icon = Icons.Rounded.AutoAwesome, route = DelegationLinks.delegation(d.id),
                )
            },
        )
    }
}

/** Puts this module on Today's whiteboard. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class DelegationGlanceBindings {
    @dagger.Binds @dagger.multibindings.IntoSet abstract fun delegationGlanceProvider(p: DelegationGlanceProvider): TodayGlanceProvider
}
