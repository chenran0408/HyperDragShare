package com.leaf.hyperdragshare.codex.touchprobe;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the root discovery script without trusting values interpolated back into a shell. */
public final class TouchDeviceMapParser {
    private static final Pattern MAP_LINE = Pattern.compile(
            "^([0-9a-fA-F]+)-([0-9a-fA-F]+)\\s+\\S+\\s+([0-9a-fA-F]+)\\s+\\S+\\s+\\S+\\s+(.+)$");
    private static final Pattern PID = Pattern.compile("(?m)^([0-9]+)$");
    private static final Pattern HASH = Pattern.compile("(?m)^([0-9a-fA-F]{64})\\s+");
    private static final Pattern TEXT_HASH = Pattern.compile(
            "(?m)^text_sha256=([0-9a-fA-F]{64})$");
    private static final Pattern LIBRARY_SIZE = Pattern.compile("(?m)^([0-9]+)$");
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{1,16}");

    private TouchDeviceMapParser() {}

    public static DeviceMap parse(String output) throws IllegalArgumentException {
        String value = output == null ? "" : output;
        String rootIdentity = section(value, "===ROOT===", "===PROPS===").trim();
        Map<String, String> properties = parseProperties(
                section(value, "===PROPS===", "===COMMANDS==="));
        Map<String, String> commands = parseCommands(
                section(value, "===COMMANDS===", "===PID==="));
        String pidText = section(value, "===PID===", "===MAPS===").trim();
        Matcher pidMatcher = PID.matcher(pidText);
        if (!pidMatcher.find()) {
            throw new IllegalArgumentException("Touch service is not running");
        }
        int pid = Integer.parseInt(pidMatcher.group(1));

        String mapsText = section(value, "===MAPS===", "===HASH===");
        String libraryPath = null;
        String baseHex = null;
        String mappingStart = null;
        String mappingEnd = null;
        List<String> libraryMaps = new ArrayList<>();
        for (String line : mapsText.split("\\R")) {
            Matcher matcher = MAP_LINE.matcher(line.trim());
            if (!matcher.matches()) continue;
            String path = matcher.group(4).trim();
            if (!path.endsWith("/" + TouchReportLayout.LIBRARY_NAME)) continue;
            libraryMaps.add(line.trim());
            if (libraryPath == null && "00000000".equalsIgnoreCase(matcher.group(3))) {
                baseHex = matcher.group(1);
                libraryPath = path;
                mappingStart = matcher.group(1);
                mappingEnd = matcher.group(2);
            }
        }
        if (baseHex == null || libraryPath == null || libraryMaps.isEmpty()) {
            throw new IllegalArgumentException("libtouchreport_alg.so mapping is unavailable");
        }

        BigInteger base = new BigInteger(baseHex, 16);
        BigInteger globalAddress = base.add(BigInteger.valueOf(TouchReportLayout.GLOBAL_POINTER_RVA));
        BigInteger expectedReport = base.add(BigInteger.valueOf(TouchReportLayout.STATIC_REPORT_RVA));

        String hashText = section(value, "===HASH===", null);
        Matcher hashMatcher = HASH.matcher(hashText);
        String libraryHash = hashMatcher.find()
                ? hashMatcher.group(1).toLowerCase(Locale.ROOT)
                : "";
        Matcher textHashMatcher = TEXT_HASH.matcher(section(value, "===CODE_SIGNATURE===", null));
        String codeSignature = textHashMatcher.find()
                ? textHashMatcher.group(1).toLowerCase(Locale.ROOT)
                : "";
        String sizeText = section(value, "===LIBRARY_SIZE===", "===CODE_SIGNATURE===").trim();
        Matcher sizeMatcher = LIBRARY_SIZE.matcher(sizeText);
        long librarySize = 0L;
        if (sizeMatcher.find()) {
            try {
                librarySize = Long.parseLong(sizeMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        return new DeviceMap(
                pid,
                rootIdentity,
                properties,
                commands,
                libraryPath,
                libraryHash,
                codeSignature,
                librarySize,
                libraryMaps,
                mappingStart,
                mappingEnd,
                base,
                globalAddress,
                expectedReport);
    }

    public static String addressHex(BigInteger value) {
        String address = "0x" + value.toString(16);
        if (!ADDRESS.matcher(address).matches()) {
            throw new IllegalArgumentException("Unsafe address");
        }
        return address;
    }

    private static String section(String value, String start, String end) {
        int begin = value.indexOf(start);
        if (begin < 0) return "";
        begin += start.length();
        int finish = end == null ? value.length() : value.indexOf(end, begin);
        if (finish < 0) finish = value.length();
        return value.substring(begin, finish);
    }

    private static Map<String, String> parseProperties(String text) {
        Map<String, String> properties = new HashMap<>();
        for (String line : text.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            properties.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return Collections.unmodifiableMap(properties);
    }

    private static Map<String, String> parseCommands(String text) {
        Map<String, String> commands = new HashMap<>();
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            int separator = value.indexOf('=');
            if (separator > 0) {
                commands.put(value.substring(0, separator), value.substring(separator + 1));
            } else {
                commands.put(value, "available");
            }
        }
        return Collections.unmodifiableMap(commands);
    }

    public static final class DeviceMap {
        public final int pid;
        public final String rootIdentity;
        public final Map<String, String> properties;
        public final Map<String, String> commands;
        public final String libraryPath;
        public final String libraryHash;
        public final String codeSignature;
        public final long librarySize;
        public final List<String> libraryMaps;
        public final String mappingStart;
        public final String mappingEnd;
        public final BigInteger baseAddress;
        public final BigInteger globalPointerAddress;
        public final BigInteger expectedReportAddress;

        DeviceMap(
                int pid,
                String rootIdentity,
                Map<String, String> properties,
                Map<String, String> commands,
                String libraryPath,
                String libraryHash,
                String codeSignature,
                long librarySize,
                List<String> libraryMaps,
                String mappingStart,
                String mappingEnd,
                BigInteger baseAddress,
                BigInteger globalPointerAddress,
                BigInteger expectedReportAddress) {
            this.pid = pid;
            this.rootIdentity = rootIdentity;
            this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
            this.commands = Collections.unmodifiableMap(new HashMap<>(commands));
            this.libraryPath = libraryPath;
            this.libraryHash = libraryHash;
            this.codeSignature = codeSignature == null ? "" : codeSignature;
            this.librarySize = Math.max(0L, librarySize);
            this.libraryMaps = Collections.unmodifiableList(new ArrayList<>(libraryMaps));
            this.mappingStart = mappingStart;
            this.mappingEnd = mappingEnd;
            this.baseAddress = baseAddress;
            this.globalPointerAddress = globalPointerAddress;
            this.expectedReportAddress = expectedReportAddress;
        }

        public boolean containsAddress(BigInteger address) {
            for (String line : libraryMaps) {
                Matcher matcher = MAP_LINE.matcher(line.trim());
                if (!matcher.matches()) continue;
                BigInteger start = new BigInteger(matcher.group(1), 16);
                BigInteger end = new BigInteger(matcher.group(2), 16);
                if (address.compareTo(start) >= 0 && address.compareTo(end) < 0) return true;
            }
            return false;
        }
    }
}
