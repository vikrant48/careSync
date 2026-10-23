package com.vikrant.careSync.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Devices are named by browser and system family, so browser updates don't look like new devices. */
class DeviceLabelTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String EDGE_WINDOWS = CHROME_WINDOWS + " Edg/130.0.0.0";
    private static final String SAFARI_IPHONE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";
    private static final String CHROME_ANDROID =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";

    @Test
    void namesCommonBrowsersAndSystems() {
        assertEquals("Chrome on Windows", DeviceLabel.of(CHROME_WINDOWS));
        assertEquals("Edge on Windows", DeviceLabel.of(EDGE_WINDOWS));
        assertEquals("Safari on iPhone/iPad", DeviceLabel.of(SAFARI_IPHONE));
        assertEquals("Chrome on Android", DeviceLabel.of(CHROME_ANDROID));
        assertEquals("Unknown device", DeviceLabel.of(null));
    }

    @Test
    void aBrowserUpdateIsTheSameDevice() {
        assertEquals(DeviceLabel.of(CHROME_WINDOWS), DeviceLabel.of(CHROME_WINDOWS.replace("130.0.0.0", "131.0.0.0")));
    }

    @Test
    void ipAddressesAreMaskedForEmails() {
        assertEquals("203.0.113.x", DeviceLabel.maskIp("203.0.113.42"));
        assertEquals("2001:db8:85a3:x", DeviceLabel.maskIp("2001:db8:85a3:8a2e"));
        assertEquals("unknown", DeviceLabel.maskIp(null));
    }
}
