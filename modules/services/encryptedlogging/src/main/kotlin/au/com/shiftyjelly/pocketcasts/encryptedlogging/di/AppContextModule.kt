package au.com.shiftyjelly.pocketcasts.encryptedlogging.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppContextModule {
    @Singleton
    @Provides
    fun providesContext(@ApplicationContext context: Context): Context {
        return context
    }
}
