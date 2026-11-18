package com.shilapi.xcertplay.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiRadioHelperTest {
    @Test fun parseAcceptsTheOkProtocolLine() {
        assertTrue(WifiRadioHelper.parse("DIPLAY_WIFI_RADIO_V1|OK"))
    }

    @Test fun parseAcceptsOkAmongHelperNoise() {
        assertTrue(WifiRadioHelper.parse("WARNING: linker\nDIPLAY_WIFI_RADIO_V1|OK\n"))
    }

    @Test fun parseAcceptsOkWithSurroundingWhitespace() {
        assertTrue(WifiRadioHelper.parse("  DIPLAY_WIFI_RADIO_V1|OK  \n"))
    }

    @Test fun parseRejectsFail() {
        assertFalse(WifiRadioHelper.parse("DIPLAY_WIFI_RADIO_V1|FAIL"))
    }

    @Test fun parseRejectsNullAndEmpty() {
        assertFalse(WifiRadioHelper.parse(null))
        assertFalse(WifiRadioHelper.parse(""))
    }

    @Test fun parseRejectsLookalikeLines() {
        assertFalse(WifiRadioHelper.parse("XDIPLAY_WIFI_RADIO_V1|OK"))
        assertFalse(WifiRadioHelper.parse("DIPLAY_WIFI_RADIO_V1|OKAY"))
        assertFalse(WifiRadioHelper.parse("DIPLAY_WIFI_RADIO_V1|OK|extra"))
    }

    @Test fun commandEndsWithEnableAction() {
        val cmd = WifiRadioHelper.command("/data/app/x.apk", "com.example", WifiRadioHelper.RadioAction.ENABLE)
        assertTrue(cmd.endsWith("com.example enable"))
        assertTrue(cmd.contains("app_process /system/bin "))
        assertTrue(cmd.contains("WifiRadioHelperMain"))
    }

    @Test fun commandEndsWithDisableAction() {
        val cmd = WifiRadioHelper.command("/data/app/x.apk", "com.example", WifiRadioHelper.RadioAction.DISABLE)
        assertTrue(cmd.endsWith("com.example disable"))
    }

    @Test fun commandQuotesCodePathWithApostrophe() {
        val cmd = WifiRadioHelper.command("/data/app/o'clock.apk", "com.example", WifiRadioHelper.RadioAction.ENABLE)
        assertTrue(cmd.startsWith("CLASSPATH='/data/app/o'\"'\"'clock.apk' "))
    }
}
