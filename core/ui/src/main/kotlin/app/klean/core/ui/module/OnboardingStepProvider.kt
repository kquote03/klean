package app.klean.core.ui.module

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * A setup-wizard page that isn't a module's own (main phone, contacts import, Google, pairing, ...).
 * Bind with @IntoSet. Pages are ordered by [order] among the built-in ones: welcome 0, modules 100,
 * sleep schedule 200, permissions 400, widget 500, AI 700, then module pages from 900 (diet last).
 * Later milestones use: main phone 300, contacts 600, Google 800, backend 810, Hermes 820, watch 830.
 */
interface OnboardingStepProvider {
    /** Stable id, e.g. "core.mainPhone". */
    val key: String
    val order: Int
    @get:StringRes val titleRes: Int
    /** The page body. Writes its settings as they change, like module pages. */
    @Composable fun Content()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingStepMultibindings {
    @Multibinds abstract fun steps(): Set<OnboardingStepProvider>
}
