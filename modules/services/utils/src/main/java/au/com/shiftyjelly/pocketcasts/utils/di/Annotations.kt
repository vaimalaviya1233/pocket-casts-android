package au.com.shiftyjelly.pocketcasts.utils.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IsEmulator

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForApplicationScope
