package au.com.shiftyjelly.pocketcasts.encryptedlogging.utils

import java.util.Locale

interface LocaleProvider {
    fun provideLocale(): Locale?
}
