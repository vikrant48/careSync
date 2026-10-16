package com.vikrant.careSync.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Checks what an uploaded file really is. The extension and the client's Content-Type are only claims:
 * the first bytes must match the extension, and the stored content type comes from here, never from the client.
 * Stops HTML, SVG, scripts or executables uploaded under a .pdf / .jpg / .txt name.
 */
public final class FileContentValidator {

    /** A file type we accept: the canonical extension and the content type it is stored with. */
    public record DetectedFile(String extension, String contentType) {
    }

    private static final int SNIFF_BYTES = 8192;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt", "text/plain");

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] GIF87 = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89 = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OLE = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
            (byte) 0xE1 };
    private static final byte[] ZIP = { 'P', 'K', 0x03, 0x04 };

    private FileContentValidator() {
    }

    /**
     * @throws IllegalArgumentException when the extension is unknown or the content does not match it
     */
    public static DetectedFile inspect(MultipartFile file) throws IOException {
        String extension = extensionOf(file.getOriginalFilename());
        String contentType = CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw new IllegalArgumentException("File type (." + extension + ") is not allowed (unsupported extension)");
        }
        byte[] head = readHead(file);
        if (!matches(extension, head)) {
            throw new IllegalArgumentException(
                    "The file content does not match its ." + extension + " extension");
        }
        return new DetectedFile(extension.equals("jpeg") ? "jpg" : extension, contentType);
    }

    static boolean matches(String extension, byte[] head) {
        return switch (extension) {
            case "pdf" -> startsWith(head, PDF);
            case "png" -> startsWith(head, PNG);
            case "jpg", "jpeg" -> startsWith(head, JPEG);
            case "gif" -> startsWith(head, GIF87) || startsWith(head, GIF89);
            case "doc" -> startsWith(head, OLE);
            case "docx" -> startsWith(head, ZIP);
            case "txt" -> isPlainText(head);
            default -> false;
        };
    }

    /** Plain text: no NUL bytes, and nothing a browser would render as markup. */
    private static boolean isPlainText(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        String text = new String(head, StandardCharsets.UTF_8).stripLeading().toLowerCase(Locale.ROOT);
        return !(text.startsWith("<") || text.contains("<script") || text.contains("<svg")
                || text.contains("<html") || text.contains("<iframe"));
    }

    /** File name safe to store and to echo back in a Content-Disposition header. */
    public static String safeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"';<>]", "_").trim();
        if (name.isEmpty()) {
            return "file";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static byte[] readHead(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
