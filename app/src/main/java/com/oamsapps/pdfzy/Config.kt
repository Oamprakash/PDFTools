package com.oamsapps.pdfzy

/** ⚠ Replace before release. */
object Config {
    const val PRIVACY_URL = "https://oamprakash.github.io/PDFTools/privacy.html"   // GitHub Pages (docs/privacy.html)
    const val SUPPORT_EMAIL = "pdfzy.help@proton.me"
}

/** Whether any app screen is on screen (set by MainActivity); used to decide on "ready" notifications. */
object AppState { @Volatile var visible = false }
