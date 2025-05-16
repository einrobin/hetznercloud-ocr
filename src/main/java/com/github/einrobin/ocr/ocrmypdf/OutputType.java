package com.github.einrobin.ocr.ocrmypdf;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum OutputType {

    PDFA("pdfa"),
    PDF("pdf"),
    PDFA1("pdfa-1"),
    PDFA2("pdfa-2"),
    PDFA3("pdfa-3"),
    NONE("none");

    private final String name;

    OutputType(String name) {
        this.name = name;
    }

    public static OutputType getByName(String name) {
        for (OutputType type : values()) {
            if (type.getName().equals(name)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown output type: " + name + " (available: "
                + Arrays.stream(values()).map(OutputType::getName).collect(Collectors.joining(", ")) + ")");
    }

    public String getName() {
        return this.name;
    }
}
