package app.klean

import app.klean.core.data.backup.BuildConfigProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object BuildConfigModule {
    @Provides
    fun buildConfig(): BuildConfigProvider = object : BuildConfigProvider {
        override val versionName: String = BuildConfig.VERSION_NAME
    }
}
