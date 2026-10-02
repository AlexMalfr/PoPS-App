package com.malfreyt.alexandre.pops_app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuildVersionTest {
    @Suppress("DEPRECATION")
    @Test fun displayedVersionMatchesInstalledPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals(installed.versionName, BuildConfig.VERSION_NAME)
        assertEquals(installed.versionCode, BuildConfig.VERSION_CODE)
        assertTrue(BuildConfig.VERSION_NAME.startsWith("0.${BuildConfig.VERSION_CODE}+"))
    }
}
