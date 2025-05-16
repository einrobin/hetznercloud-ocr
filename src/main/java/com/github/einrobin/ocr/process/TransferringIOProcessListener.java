package com.github.einrobin.ocr.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class TransferringIOProcessListener implements IOProcessListener {

    private final Path inputPath;
    private final Path outputPath;

    public TransferringIOProcessListener(Path inputPath, Path outputPath) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
    }

    @Override
    public InputStream openInput() throws IOException {
        return Files.newInputStream(this.inputPath);
    }

    @Override
    public OutputStream openOutput() throws IOException {
        Files.createDirectories(this.outputPath.getParent());
        return Files.newOutputStream(this.outputPath);
    }

    @Override
    public void onSuccess() throws IOException {
        Files.delete(this.inputPath);
    }
}
