package au.com.shiftyjelly.pocketcasts.encryptedlogging.di

import android.util.Base64
import au.com.shiftyjelly.pocketcasts.encryptedlogging.EncryptedLoggingKey
import au.com.shiftyjelly.pocketcasts.encyptedlogging.BuildConfig
import com.goterl.lazysodium.utils.Key
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object EncryptedLoggingModule {
    @Provides
    fun provideEncryptedLoggingKey(): EncryptedLoggingKey {
        return EncryptedLoggingKey(
            Key.fromBytes(
                Base64.decode(
                    BuildConfig.WP_ENCRYTPED_LOGGING_KEY,
                    Base64.DEFAULT
                )
            )
        )
    }

    @Provides
    fun provideAppSecrets() = AppSecrets(BuildConfig.WP_OAUTH_APP_ID, BuildConfig.WP_OAUTH_APP_SECRET)

    data class AppSecrets(val appId: String, val appSecret: String)
}
