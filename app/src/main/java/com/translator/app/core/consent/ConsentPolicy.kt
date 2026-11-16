package com.translator.app.core.consent

/** What may be collected or used, per Consent Mode type. */
data class ConsentSignals(
    val analyticsStorage: Boolean,
    val adStorage: Boolean,
    val adUserData: Boolean,
    val adPersonalization: Boolean,
) {
    companion object { val DENIED = ConsentSignals(false, false, false, false) }
}

/**
 * Reads IABTCF_PurposeConsents: a string of '0'/'1' where character n-1 is purpose n. Null, empty or too-short input means
 * "not consented" for the missing purposes.
 */
fun tcfPurpose(purposeConsents: String?, purpose: Int): Boolean =
    purposeConsents != null && purpose in 1..purposeConsents.length && purposeConsents[purpose - 1] == '1'

/**
 * The consent signals. Where consent rules apply they come from the real per-purpose answers in the TCF string, never from
 * "may request ads" alone (that only says a minimum was granted), using Google's Consent Mode mapping:
 * ad_storage = purpose 1; ad_user_data = 1 and 7; ad_personalization = 1, 3 and 4; analytics_storage = 1, 8, 9 and 10.
 * An unreadable string means denied. Switching analytics off in Settings ([userChoice] 0) always wins. Elsewhere analytics is
 * off until the person agreed to the first-run line ([userChoice] 1), and ads need no per-purpose answer.
 */
fun consentSignals(regulated: Boolean, canRequestAds: Boolean, userChoice: Int, purposeConsents: String?): ConsentSignals {
    if (regulated) {
        fun p(vararg n: Int) = n.all { tcfPurpose(purposeConsents, it) }
        return ConsentSignals(
            analyticsStorage = userChoice != 0 && p(1, 8, 9, 10),
            adStorage = p(1),
            adUserData = p(1, 7),
            adPersonalization = p(1, 3, 4),
        )
    }
    return ConsentSignals(analyticsStorage = userChoice == 1, adStorage = canRequestAds, adUserData = canRequestAds, adPersonalization = canRequestAds)
}
