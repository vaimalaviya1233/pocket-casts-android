package au.com.shiftyjelly.pocketcasts.profile

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.widget.ConstraintLayout
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.images.SubscriptionBadge
import au.com.shiftyjelly.pocketcasts.localization.extensions.getStringPluralDaysMonthsOrYears
import au.com.shiftyjelly.pocketcasts.localization.extensions.getStringPluralSecondsMinutesHoursDaysOrYears
import au.com.shiftyjelly.pocketcasts.models.to.SignInState
import au.com.shiftyjelly.pocketcasts.models.to.SubscriptionStatus
import au.com.shiftyjelly.pocketcasts.models.type.SubscriptionFrequency
import au.com.shiftyjelly.pocketcasts.models.type.SubscriptionPlatform
import au.com.shiftyjelly.pocketcasts.models.type.SubscriptionTier
import au.com.shiftyjelly.pocketcasts.ui.extensions.getThemeColor
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.utils.Util
import au.com.shiftyjelly.pocketcasts.utils.extensions.toLocalizedFormatLongStyle
import com.gravatar.api.models.UserProfiles
import com.gravatar.services.ErrorType
import com.gravatar.services.GravatarListener
import com.gravatar.services.ProfileService
import com.gravatar.types.Email
import com.gravatar.ui.GravatarTheme
import com.gravatar.ui.LocalGravatarTheme
import com.gravatar.ui.components.LargeProfileSummary
import java.util.Date
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

open class UserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    open val layoutResource = R.layout.view_user

    var signedInState: SignInState? = null
        set(value) {
            field = value
            update(value)
        }

    var accountStartDate: Date = Date()
    val maxSubscriptionExpiryMs = 30L * 24L * 60L * 60L * 1000L
    val lblUserEmail: TextView
    val lblSignInStatus: TextView?

    val btnAccount: Button?
    val gravatarProfile: ComposeView?
    private val subscriptionBadge: ComposeView?
    private val isDarkTheme: Boolean
        get() = Theme.isDark(context)

    init {
        LayoutInflater.from(context).inflate(layoutResource, this, true)
        lblUserEmail = findViewById(R.id.lblUserEmail)
        lblSignInStatus = findViewById(R.id.lblSignInStatus)
        btnAccount = findViewById(R.id.btnAccount)
        subscriptionBadge = findViewById(R.id.subscriptionBadge)
        gravatarProfile = findViewById(R.id.gravatarProfile)
    }

    open fun update(signInState: SignInState?) {
        updateEmail(signInState)
        updateSubscriptionBadge(signInState)
        updateAccountButton(signInState)
        updateGravatarProfile(signInState)
    }


    private fun setDaysRemainingTextIfNeeded(signInState: SignInState.SignedIn) {
        val status = ((signInState as? SignInState.SignedIn)?.subscriptionStatus as? SubscriptionStatus.Paid) ?: return
        if (status.autoRenew) {
            return
        }

        val timeLeftMs = status.expiry.time - Date().time
        if (timeLeftMs <= 0) {
            return
        }

        if (timeLeftMs <= maxSubscriptionExpiryMs) {
            val expiresIn = resources.getStringPluralSecondsMinutesHoursDaysOrYears(timeLeftMs)
            val messagesRes = if (signInState.isSignedInAsPatron) LR.string.profile_patron_expires_in else LR.string.profile_plus_expires_in
            lblUserEmail.text = context.getString(messagesRes, expiresIn).uppercase()
            lblUserEmail.setTextColor(lblUserEmail.context.getThemeColor(UR.attr.support_05))
        }
    }

    private fun updateEmail(signInState: SignInState?) {
        when (signInState) {
            is SignInState.SignedIn -> {
                lblUserEmail.text = signInState.email
                lblUserEmail.visibility = View.VISIBLE
                lblUserEmail.setTextColor(context.getThemeColor(UR.attr.primary_text_01))

                if (this !is ExpandedUserView) setDaysRemainingTextIfNeeded(signInState)
            }

            is SignInState.SignedOut -> {
                lblUserEmail.text = context.getString(LR.string.profile_set_up_account)
                lblUserEmail.visibility = View.GONE
            }

            null -> lblUserEmail.text = null
        }
    }

    private fun updateGravatarProfile(signInState: SignInState?) {
        gravatarProfile?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val profileService = ProfileService()
                var profiles by remember { mutableStateOf(UserProfiles(emptyList()), neverEqualPolicy()) }
                var error by remember { mutableStateOf("") }

                when (signInState) {
                    is SignInState.SignedIn -> {
                        profileService.fetchWithListener(
                            Email(signInState.email),
                            object : GravatarListener<UserProfiles, ErrorType> {
                                override fun onSuccess(response: UserProfiles) {
                                    profiles = response
                                }

                                override fun onError(errorType: ErrorType) {
                                    error = errorType.name
                                }
                            },
                        )
                    }
                    else -> {}
                }

                if (signInState is SignInState.SignedIn) {
                    if (profiles.entry.isNotEmpty()) {
                        Image(
                            painter = painterResource(id = R.drawable.gravatar_profile_background), contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .fillMaxSize()
                        )
                        CompositionLocalProvider(LocalGravatarTheme provides object : GravatarTheme {
                            override val colorScheme: ColorScheme
                                @Composable
                                get() = MaterialTheme.colorScheme.copy(
                                    outline = Color.LightGray,
                                    onBackground = Color.White)

                            override val typography: Typography
                                @Composable
                                get() = MaterialTheme.typography.copy(
                                    // Couldn't find the right setting for this under colorScheme
                                    headlineSmall = MaterialTheme.typography.headlineSmall.copy(color = Color.White),
                                )
                        }) {
                            LargeProfileSummary(
                                profile = profiles.entry.first(),
                                modifier = Modifier
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateSubscriptionBadge(signInState: SignInState?) {
        val fontSize = if (Util.isAutomotive(context)) 20.sp else 14.sp
        val iconSize = if (Util.isAutomotive(context)) 20.dp else 14.dp
        val padding = if (Util.isAutomotive(context)) 6.dp else 4.dp
        subscriptionBadge?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme(if (isDarkTheme) Theme.ThemeType.DARK else Theme.ThemeType.LIGHT) {
                    if (signInState is SignInState.SignedIn) {
                        val isExpandedUserView = this@UserView is ExpandedUserView
                        val modifier = Modifier.padding(top = 16.dp)
                        if (signInState.isSignedInAsPatron) {
                            SubscriptionBadge(
                                iconRes = IR.drawable.ic_patron,
                                shortNameRes = LR.string.pocket_casts_patron_short,
                                iconColor = if (!isExpandedUserView) Color.White else Color.Unspecified,
                                backgroundColor = if (!isExpandedUserView) colorResource(UR.color.patron_purple) else null,
                                textColor = if (!isExpandedUserView) colorResource(UR.color.patron_purple_light) else null,
                                modifier = if (isExpandedUserView) modifier else Modifier,
                                iconSize = iconSize,
                                fontSize = fontSize,
                                padding = padding,
                            )
                        } else if (signInState.isSignedInAsPlus && isExpandedUserView) {
                            SubscriptionBadge(
                                iconRes = IR.drawable.ic_plus,
                                shortNameRes = LR.string.pocket_casts_plus_short,
                                iconColor = colorResource(UR.color.plus_gold),
                                iconSize = iconSize,
                                fontSize = fontSize,
                                padding = padding,
                                modifier = modifier,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateAccountButton(signInState: SignInState?) {
        btnAccount?.text = when (signInState) {
            is SignInState.SignedIn -> context.getString(LR.string.profile_account)
            is SignInState.SignedOut -> context.getString(LR.string.profile_set_up_account)
            else -> null
        }
    }
}

class ExpandedUserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : UserView(context, attrs, defStyleAttr) {
    override val layoutResource: Int
        get() = R.layout.view_expanded_user
    val lblPaymentStatus: TextView
        get() = findViewById(R.id.lblPaymentStatus)

    override fun update(signInState: SignInState?) {
        super.update(signInState)

        val status = (signInState as? SignInState.SignedIn)?.subscriptionStatus ?: return
        when (status) {
            is SubscriptionStatus.Free -> {
                lblPaymentStatus.text = context.getString(LR.string.profile_free_account)
                lblSignInStatus?.text = ""
            }
            is SubscriptionStatus.Paid -> {
                val activeSubscription = status.subscriptions.getOrNull(status.index)
                if (activeSubscription == null ||
                    activeSubscription.tier in listOf(
                        SubscriptionTier.PATRON,
                        SubscriptionTier.PLUS,
                    )
                ) {
                    setupLabelsForPaidUser(status, signInState)
                } else {
                    setupLabelsForSupporter(activeSubscription)
                }
            }
        }
    }

    private fun setupLabelsForPaidUser(status: SubscriptionStatus.Paid, signInState: SignInState) {
        if (status.autoRenew) {
            val strMonthly = context.getString(LR.string.profile_monthly)
            val strYearly = context.getString(LR.string.profile_yearly)
            lblPaymentStatus.text = context.getString(LR.string.profile_next_payment, status.expiry.toLocalizedFormatLongStyle())
            lblSignInStatus?.text = when (status.frequency) {
                SubscriptionFrequency.MONTHLY -> strMonthly
                SubscriptionFrequency.YEARLY -> strYearly
                else -> null
            }
            lblSignInStatus?.setTextColor(context.getThemeColor(UR.attr.primary_text_02))
        } else {
            if (status.platform == SubscriptionPlatform.GIFT) {
                if (signInState.isLifetimePlus) {
                    lblPaymentStatus.text = context.resources.getString(LR.string.plus_thanks_for_your_support_bang)
                } else {
                    val giftDaysString = context.resources.getStringPluralDaysMonthsOrYears(status.giftDays)
                    lblPaymentStatus.text = context.resources.getString(LR.string.profile_time_free, giftDaysString)
                }
            } else {
                lblPaymentStatus.text = context.getString(LR.string.profile_payment_cancelled)
            }

            if (signInState.isLifetimePlus) {
                lblSignInStatus?.text = context.resources.getString(LR.string.plus_lifetime_member)
                lblSignInStatus?.setTextColor(lblSignInStatus.context.getThemeColor(UR.attr.support_02))
            } else {
                lblSignInStatus?.text = context.getString(LR.string.profile_plus_expires, status.expiry.toLocalizedFormatLongStyle())
                lblSignInStatus?.setTextColor(lblSignInStatus.context.getThemeColor(UR.attr.primary_text_02))
            }
        }
    }

    private fun setupLabelsForSupporter(subscription: SubscriptionStatus.Subscription) {
        if (subscription.autoRenewing) {
            lblPaymentStatus.text = context.getString(LR.string.supporter)
            lblPaymentStatus.setTextColor(lblPaymentStatus.context.getThemeColor(UR.attr.support_02))

            lblSignInStatus?.text = context.getString(LR.string.supporter_check_contributions)
            lblSignInStatus?.setTextColor(context.getThemeColor(UR.attr.primary_text_02))
        } else {
            lblPaymentStatus.text = context.getString(LR.string.supporter_payment_cancelled)
            lblPaymentStatus.setTextColor(lblPaymentStatus.context.getThemeColor(UR.attr.support_05))

            val expiryDate = subscription.expiryDate?.let { it.toLocalizedFormatLongStyle() } ?: context.getString(LR.string.profile_expiry_date_unknown)
            lblSignInStatus?.text = context.getString(LR.string.supporter_subscription_ends, expiryDate)
            lblSignInStatus?.setTextColor(context.getThemeColor(UR.attr.primary_text_02))
        }
    }
}
