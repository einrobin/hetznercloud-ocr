package com.github.einrobin.ocr.process;

import com.github.einrobin.clusterexecutor.ClusterTask;
import com.github.einrobin.clusterexecutor.ssh.SSHSession;
import com.github.einrobin.ocr.ocrmypdf.OCRmyPDFConfig;
import com.jcraft.jsch.JSchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OCRProcess extends IOProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(OCRProcess.class);
    private final OCRmyPDFConfig config;

    public OCRProcess(IOProcessListener listener, OCRmyPDFConfig config) {
        super(".pdf", listener);
        this.config = config;
    }

    private String buildOcrCommand(String inputFile, String outputFile) {
        List<String> params = new ArrayList<>(4);

        params.add("ocrmypdf");

        this.config.params().forEach((key, value) -> {
            params.add(key);
            if (!value.isEmpty()) {
                params.add(value);
            }
        });

        params.add(inputFile);
        params.add(outputFile);

        params.add("2>&1");

        return String.join(" ", params);
    }

    @Override
    protected boolean run(SSHSession ssh, ClusterTask task, String inputFile, String outputFile) {
        try {
            byte[] result = ssh.exec(this.buildOcrCommand(inputFile, outputFile));

            LOGGER.info("==================== OCRmyPDF result ====================");
            System.out.println(new String(result, StandardCharsets.UTF_8));
            LOGGER.info("==================== OCRmyPDF result ====================");
        } catch (JSchException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return true;
    }
}
