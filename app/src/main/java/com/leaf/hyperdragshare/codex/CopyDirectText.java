package com.leaf.hyperdragshare.codex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recovers an HTTP URL whose visible RAG chips were separated by spaces. */
final class CopyDirectText {
    private static final Pattern HTTP_START =
            Pattern.compile("(?i)https?[\\s\\u00A0]*:[\\s\\u00A0]*/[\\s\\u00A0]*/");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+[\\s\\u00A0]*@[\\s\\u00A0]*[A-Za-z0-9.\\-]+"
                    + "[\\s\\u00A0]*\\.[\\s\\u00A0]*[A-Za-z]{2,63}");
    private static final String URL_CHARACTERS =
            ".-_~:/?#[]@!$&'()*+,;=%";
    private static final String URL_BRIDGE_CHARACTERS =
            "./:_~?#[]@!$&'()*+,;=%";

    private CopyDirectText() {}

    static String findHttpUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher start = HTTP_START.matcher(text);
        while (start.find()) {
            int end = start.end();
            while (end < text.length()) {
                char value = text.charAt(end);
                if (Character.isLetterOrDigit(value) || URL_CHARACTERS.indexOf(value) >= 0) {
                    end++;
                    continue;
                }
                if (Character.isHighSurrogate(value) && end + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(end + 1))) {
                    end += 2;
                    continue;
                }
                if (!isSpace(value)) break;
                int next = end;
                while (next < text.length() && isSpace(text.charAt(next))) next++;
                if (next >= text.length()
                        || (!isUrlBridge(text.charAt(end - 1)) && !isUrlBridge(text.charAt(next)))) break;
                end = next;
            }
            String compact = compactWhitespace(text.substring(start.start(), end));
            compact = trimSentencePunctuation(compact);
            if (compact.length() > compactWhitespace(start.group()).length()) return compact;
        }
        return null;
    }

    static String findEmail(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = EMAIL.matcher(text);
        return matcher.find() ? compactWhitespace(matcher.group()) : null;
    }

    static String normalizeEmail(String value) {
        return value == null ? null : compactWhitespace(value);
    }

    private static boolean isSpace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private static boolean isUrlBridge(char value) {
        return URL_BRIDGE_CHARACTERS.indexOf(value) >= 0;
    }

    private static String compactWhitespace(String value) {
        StringBuilder compact = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!isSpace(current)) compact.append(current);
        }
        return compact.toString();
    }

    private static String trimSentencePunctuation(String value) {
        int end = value.length();
        while (end > 0 && ".,;:!?)]}'\"".indexOf(value.charAt(end - 1)) >= 0) end--;
        return value.substring(0, end);
    }
}
