package com.github.enoti722.thermometer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public class ThermometerFrameParserTest {
    private final ThermometerFrameParser parser = new ThermometerFrameParser();

    @Test
    public void parsesLineTerminatedFrame() {
        assertEquals(Collections.singletonList("25.5"), append("~G25.5\r\n"));
    }

    @Test
    public void parsesLegacyUnterminatedDecimalFrame() {
        assertEquals(Collections.singletonList("-7.2"), append("~G-7.2"));
    }

    @Test
    public void joinsFrameSplitAcrossUsbReads() {
        assertEquals(Collections.emptyList(), append("noise~"));
        assertEquals(Collections.emptyList(), append("G-1"));
        assertEquals(Collections.singletonList("-12.4"), append("2.4\n"));
    }

    @Test
    public void parsesSeveralFramesFromOneUsbRead() {
        assertEquals(Arrays.asList("18.1", "18.2", "18.3"),
                append("~G18.1\n~G18.2\r\n~G18.3\n"));
    }

    @Test
    public void nextMarkerCompletesFrameWithoutLineEnding() {
        assertEquals(Collections.emptyList(), append("~G21"));
        assertEquals(Arrays.asList("21", "22"), append("~G22\n"));
    }

    @Test
    public void resetDropsFragmentFromPreviousConnection() {
        assertEquals(Collections.emptyList(), append("~G1"));
        parser.reset();
        assertEquals(Collections.singletonList("9.5"), append("~G9.5\n"));
    }

    private java.util.List<String> append(String value) {
        return parser.append(value.getBytes(StandardCharsets.US_ASCII));
    }
}
