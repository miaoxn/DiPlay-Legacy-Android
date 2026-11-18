package com.shilapi.xcertplay.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokenHardwareAvcDecoderTest {
    @Test fun atcDecoderIsBrokenOnAnyHardware() {
        assertTrue(isBrokenHardwareAvcDecoder("OMX.atc.video.decoder.avc", "autochipsac83xx"))
        assertTrue(isBrokenHardwareAvcDecoder("OMX.atc.video.decoder.avc", "something-else"))
    }

    @Test fun mtkDecoderIsBrokenOnlyOnAc83xxHardware() {
        assertTrue(isBrokenHardwareAvcDecoder("OMX.mtk.video.decoder.avc", "autochipsac83xx"))
        assertTrue(isBrokenHardwareAvcDecoder("OMX.mtk.video.decoder.avc", "AutoChipsAC83XX"))
        assertFalse(isBrokenHardwareAvcDecoder("OMX.mtk.video.decoder.avc", "mt8227"))
        assertFalse(isBrokenHardwareAvcDecoder("OMX.mtk.video.decoder.avc", "sun8i"))
        assertFalse(isBrokenHardwareAvcDecoder("OMX.mtk.video.decoder.avc", ""))
    }

    @Test fun decoderNameMatchIsCaseInsensitive() {
        assertTrue(isBrokenHardwareAvcDecoder("OMX.MTK.VIDEO.DECODER.AVC", "autochipsac83xx"))
    }

    @Test fun otherDecodersAreNotCovered() {
        assertFalse(isBrokenHardwareAvcDecoder("OMX.google.h264.decoder", "autochipsac83xx"))
        assertFalse(isBrokenHardwareAvcDecoder("c2.mtk.avc.decoder", "autochipsac83xx"))
        assertFalse(isBrokenHardwareAvcDecoder("OMX.k3.video.decoder.avc", "autochipsac83xx"))
        assertFalse(isBrokenHardwareAvcDecoder(null, "autochipsac83xx"))
    }
}
