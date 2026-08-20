package com.heartbeat.ping.service.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    @Test
    void writesHeaderAndRows() {
        String csv = CsvWriter.write(
                List.of("Name", "Value"),
                List.of(List.of("a", "1"), List.of("b", "2"))
        );

        assertEquals("Name,Value\r\na,1\r\nb,2\r\n", csv);
    }

    @Test
    void quotesFieldsContainingCommasQuotesOrNewlines() {
        String csv = CsvWriter.write(
                List.of("Note"),
                List.of(List.of("has,comma"), List.of("has\"quote"), List.of("has\nnewline"))
        );

        assertEquals("Note\r\n\"has,comma\"\r\n\"has\"\"quote\"\r\n\"has\nnewline\"\r\n", csv);
    }

    @Test
    void treatsNullFieldAsEmpty() {
        String csv = CsvWriter.write(List.of("A"), List.of(java.util.Collections.singletonList(null)));

        assertEquals("A\r\n\r\n", csv);
    }
}
