package com.github.enoti722.thermometer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Потоковый разбор ответов термометра. Границы USB-чтений не обязаны совпадать
 * с границами кадров протокола, поэтому незавершённый хвост хранится до следующего чтения.
 */
final class ThermometerFrameParser {
    private static final String FRAME_PREFIX = "~G";
    private static final int MAX_BUFFER_CHARS = 1024;
    private static final Pattern COMPLETE_DECIMAL_VALUE =
            Pattern.compile("[+-]?\\d+[.,]\\d+");

    private final StringBuilder buffer = new StringBuilder();

    List<String> append(byte[] data) {
        if (data == null || data.length == 0) {
            return Collections.emptyList();
        }

        buffer.append(new String(data, StandardCharsets.US_ASCII));
        List<String> values = new ArrayList<>();

        while (true) {
            int marker = buffer.indexOf(FRAME_PREFIX);
            if (marker < 0) {
                keepPossiblePrefixStart();
                break;
            }
            if (marker > 0) {
                buffer.delete(0, marker);
            }

            int nextMarker = buffer.indexOf(FRAME_PREFIX, FRAME_PREFIX.length());
            int lineEnd = findLineEnd(FRAME_PREFIX.length());
            if (lineEnd >= 0 && (nextMarker < 0 || lineEnd < nextMarker)) {
                addValue(values, buffer.substring(FRAME_PREFIX.length(), lineEnd));
                int consumed = lineEnd + 1;
                while (consumed < buffer.length()
                        && (buffer.charAt(consumed) == '\r' || buffer.charAt(consumed) == '\n')) {
                    consumed++;
                }
                buffer.delete(0, consumed);
            } else if (nextMarker >= 0) {
                addValue(values, buffer.substring(FRAME_PREFIX.length(), nextMarker));
                buffer.delete(0, nextMarker);
            } else {
                break;
            }
        }

        // Старый код принимал и ответ без перевода строки. Сохраняем совместимость,
        // но завершаем такой кадр только когда в текущем куске уже есть полное дробное число.
        if (buffer.indexOf(FRAME_PREFIX) == 0) {
            String candidate = buffer.substring(FRAME_PREFIX.length()).trim();
            if (COMPLETE_DECIMAL_VALUE.matcher(candidate).matches()) {
                values.add(candidate);
                buffer.setLength(0);
            }
        }

        trimOversizedGarbage();
        return values;
    }

    void reset() {
        buffer.setLength(0);
    }

    private int findLineEnd(int fromIndex) {
        for (int i = fromIndex; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (c == '\r' || c == '\n') {
                return i;
            }
        }
        return -1;
    }

    private void keepPossiblePrefixStart() {
        boolean trailingTilde = buffer.length() > 0 && buffer.charAt(buffer.length() - 1) == '~';
        buffer.setLength(0);
        if (trailingTilde) {
            buffer.append('~');
        }
    }

    private static void addValue(List<String> values, String raw) {
        String value = raw.trim();
        if (!value.isEmpty()) {
            values.add(value);
        }
    }

    private void trimOversizedGarbage() {
        if (buffer.length() <= MAX_BUFFER_CHARS) {
            return;
        }
        int lastMarker = buffer.lastIndexOf(FRAME_PREFIX);
        if (lastMarker > 0) {
            buffer.delete(0, lastMarker);
        } else {
            buffer.setLength(0);
        }
    }
}
