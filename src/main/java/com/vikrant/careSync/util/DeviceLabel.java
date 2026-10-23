package com.vikrant.careSync.util;

/**
 * A short, human name for a browser's User-Agent ("Chrome on Android"). Only the browser and system families are
 * used, so a browser update does not look like a new device.
 */
public final class DeviceLabel {

    private DeviceLabel() {
    }

    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        return browser(userAgent) + " on " + system(userAgent);
    }

    static String browser(String ua) {
        // Order matters: Edge, Opera and Samsung Internet also say "Chrome"; Chrome also says "Safari"
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("SamsungBrowser/")) return "Samsung Internet";
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "Firefox";
        if (ua.contains("CriOS/") || ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        if (ua.contains("PostmanRuntime")) return "Postman";
        return "Browser";
    }

    static String system(String ua) {
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) return "iPhone/iPad";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "Mac";
        if (ua.contains("CrOS")) return "ChromeOS";
        if (ua.contains("Linux")) return "Linux";
        return "unknown system";
    }

    /** "203.0.113.x" / "2001:db8:85a3:x": enough to recognise, not the full address (for emails) */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return "unknown";
        int dot = ip.lastIndexOf('.');
        if (dot > 0 && !ip.contains(":")) return ip.substring(0, dot) + ".x";
        int colon = ip.lastIndexOf(':');
        return colon > 0 ? ip.substring(0, colon) + ":x" : "unknown";
    }
}
