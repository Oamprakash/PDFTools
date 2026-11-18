package com.oam.pdftools

/** ⚠ Replace before release. */
object Config {
    const val PRIVACY_URL = "https://oamprakash.github.io/pdftools/privacy.html"   // GitHub Pages (docs/privacy.html)
    const val SUPPORT_EMAIL = "support@example.com"                  // TODO: your support address
}

/** Whether any app screen is on screen (set by MainActivity); used to decide on "ready" notifications. */
object AppState { @Volatile var visible = false }
