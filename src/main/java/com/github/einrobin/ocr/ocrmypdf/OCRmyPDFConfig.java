package com.github.einrobin.ocr.ocrmypdf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record OCRmyPDFConfig(String[] languages, boolean skipText, OutputType outputType) {

    public Map<String, String> params() {
        Map<String, String> params = new HashMap<>();

        params.put("-l", String.join("+", this.languages));
        params.put("--output-type", this.outputType.getName());
        if (this.skipText) {
            params.put("--skip-text", "");
        }

        return params;
    }

    public String cloudinitConfig() {
        List<String> packages = new ArrayList<>(1);
        packages.add("ocrmypdf");
        for (String language : this.languages) {
            packages.add("tesseract-ocr-" + language);
        }

        return """
                #cloud-config
                package_update: true
                package_upgrade: true
                packages:
                """ + packages.stream().map(pkg -> "- " + pkg).collect(Collectors.joining("\n"));
    }
}
