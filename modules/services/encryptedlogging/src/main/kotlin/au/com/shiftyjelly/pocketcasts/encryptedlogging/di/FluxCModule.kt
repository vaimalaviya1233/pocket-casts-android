package au.com.shiftyjelly.pocketcasts.encryptedlogging.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.wordpress.android.fluxc.module.DatabaseModule
import org.wordpress.android.fluxc.module.OkHttpClientModule
import org.wordpress.android.fluxc.module.ReleaseNetworkModule

@InstallIn(SingletonComponent::class)
@Module(
    includes = [
        ReleaseNetworkModule::class,
        OkHttpClientModule::class,
        DatabaseModule::class
    ]
)
interface FluxCModule
