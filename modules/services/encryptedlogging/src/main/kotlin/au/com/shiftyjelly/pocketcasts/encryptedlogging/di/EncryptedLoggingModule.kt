package au.com.shiftyjelly.pocketcasts.encryptedlogging.di

import android.util.Base64
import au.com.shiftyjelly.pocketcasts.encryptedlogging.EncryptedLoggingKey
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
                    "",
                    Base64.DEFAULT
                )
            )
        )
    }

    @Provides
    fun provideAppSecrets() = AppSecrets("", "")

    data class AppSecrets(val appId: String, val appSecret: String)
}
