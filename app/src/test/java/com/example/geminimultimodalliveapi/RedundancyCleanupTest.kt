package com.example.geminimultimodalliveapi

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Tests to verify code deduplication of HIGH priority redundancies:
 * 1. isNotificationServiceEnabled() removed from MainActivity and SettingsActivity
 * 2. GoogleSignInOptions config centralized in GoogleSignInHelper
 * 3. showReAuthDialog() logic extracted (each activity uses GoogleSignInHelper.buildGso)
 * 4. Inline dp*density in CalendarManager replaced with dpToPx()
 */
class RedundancyCleanupTest {

    private val srcRoot = "src/main/java/com/example/geminimultimodalliveapi"

    /**
     * Verify no private isNotificationServiceEnabled() remains in Activity files.
     * It should only exist in utils/NotificationUtils.kt.
     */
    @Test
    fun testIsNotificationServiceEnabledNotDuplicated() {
        val mainActivitySrc = File("$srcRoot/MainActivity.kt").readText()
        val settingsActivitySrc = File("$srcRoot/SettingsActivity.kt").readText()
        val utilSrc = File("$srcRoot/utils/NotificationUtils.kt").readText()

        assertFalse(
            "MainActivity should not contain private isNotificationServiceEnabled",
            mainActivitySrc.contains("private fun isNotificationServiceEnabled()")
        )
        assertFalse(
            "SettingsActivity should not contain private isNotificationServiceEnabled",
            settingsActivitySrc.contains("private fun isNotificationServiceEnabled()")
        )
        assertTrue(
            "NotificationUtils.kt should contain the isNotificationServiceEnabled function",
            utilSrc.contains("fun Context.isNotificationServiceEnabled(")
        )
    }

    /**
     * Verify GoogleSignInOptions config is centralized in GoogleSignInHelper.
     * No activity/manager should inline-build GSO with requestScopes anymore.
     */
    @Test
    fun testGoogleSignInOptionsNotDuplicated() {
        val mainActivitySrc = File("$srcRoot/MainActivity.kt").readText()
        val settingsActivitySrc = File("$srcRoot/SettingsActivity.kt").readText()
        val calendarManagerSrc = File("$srcRoot/calendar/CalendarManager.kt").readText()
        val documentManagerSrc = File("$srcRoot/document/DocumentManager.kt").readText()
        val helperSrc = File("$srcRoot/utils/GoogleSignInHelper.kt").readText()

        val gsoPattern = "GoogleSignInOptions.Builder"

        assertFalse(
            "MainActivity should not inline-build GoogleSignInOptions",
            mainActivitySrc.contains(gsoPattern)
        )
        assertFalse(
            "SettingsActivity should not inline-build GoogleSignInOptions",
            settingsActivitySrc.contains(gsoPattern)
        )
        assertFalse(
            "CalendarManager should not inline-build GoogleSignInOptions",
            calendarManagerSrc.contains(gsoPattern)
        )
        assertFalse(
            "DocumentManager should not inline-build GoogleSignInOptions",
            documentManagerSrc.contains(gsoPattern)
        )
        assertTrue(
            "GoogleSignInHelper.kt should contain the GSO builder",
            helperSrc.contains(gsoPattern)
        )
    }

    /**
     * Verify inline density calculations in CalendarManager replaced with dpToPx.
     */
    @Test
    fun testCalendarManagerUseDpToPx() {
        val calendarManagerSrc = File("$srcRoot/calendar/CalendarManager.kt").readText()
        assertFalse(
            "CalendarManager should not contain inline displayMetrics.density calculations",
            calendarManagerSrc.contains("displayMetrics.density")
        )
    }
}
