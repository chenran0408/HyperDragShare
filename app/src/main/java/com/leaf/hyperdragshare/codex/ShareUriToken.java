package com.leaf.hyperdragshare.codex;

import java.util.UUID;
import java.util.regex.Pattern;

final class ShareUriToken {
    static final String PNG_SUFFIX = ".png";
    static final String JPEG_SUFFIX = ".jpg";
    private static final Pattern IMAGE_SUFFIX = Pattern.compile("\\.[a-z0-9]{1,8}");

    private ShareUriToken() {}

    static String fileName(String token) {
        return fileName(token, PNG_SUFFIX);
    }

    static String fileName(String token, String suffix) {
        String parsed = parse(token);
        if (parsed == null || !parsed.equals(token)) {
            throw new IllegalArgumentException("Invalid share token");
        }
        if (suffix == null || !IMAGE_SUFFIX.matcher(suffix).matches()) {
            throw new IllegalArgumentException("Invalid image suffix");
        }
        return token + suffix;
    }

    static String parse(String pathSegment) {
        if (pathSegment == null) {
            return null;
        }
        String token = pathSegment;
        int dot = pathSegment.lastIndexOf('.');
        if (dot >= 0) {
            if (!IMAGE_SUFFIX.matcher(pathSegment.substring(dot)).matches()) return null;
            token = pathSegment.substring(0, dot);
        }
        try {
            return UUID.fromString(token).toString().equalsIgnoreCase(token) ? token : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static String suffix(String pathSegment) {
        if (parse(pathSegment) == null) {
            return null;
        }
        int dot = pathSegment.lastIndexOf('.');
        if (dot >= 0) return pathSegment.substring(dot);
        // Versions before the explicit .jpg URI suffix used a bare UUID for JPEG files.
        return JPEG_SUFFIX;
    }
}
