package au.com.shiftyjelly.pocketcasts.encryptedlogging.di

import android.content.Context
import android.util.Base64
import com.goterl.lazysodium.utils.Key
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.wordpress.android.fluxc.model.encryptedlogging.EncryptedLoggingKey
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AppSecrets

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideEncryptedLoggingKey(): EncryptedLoggingKey {
        return EncryptedLoggingKey(
            Key.fromBytes(
                Base64.decode(
                    "",
                    Base64.DEFAULT
                )
            )
        )
    }

    @Provides
    fun provideAppSecrets() =
        AppSecrets("", "")

    @Provides
    fun provideUserAgent(@ApplicationContext appContext: Context?): UserAgent {
        return UserAgent(appContext, "pc")
    }
}
