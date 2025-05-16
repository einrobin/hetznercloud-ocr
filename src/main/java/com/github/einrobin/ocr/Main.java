package com.github.einrobin.ocr;

import com.github.einrobin.ocr.ocrmypdf.OCRmyPDFConfig;
import com.github.einrobin.ocr.process.OCRProcess;
import com.github.einrobin.clusterexecutor.ClusterExecutor;
import com.github.einrobin.clusterexecutor.ClusterTask;
import com.github.einrobin.clusterexecutor.cloud.CloudClusterExecutor;
import com.github.einrobin.clusterexecutor.hetzner.HetznerCloudClusterProvider;
import com.github.einrobin.ocr.process.TransferringIOProcessListener;
import com.github.einrobin.ocr.utils.FolderWatcher;
import io.github.sinuscosinustan.hetznercloud.HetznerCloudAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Map<String, Object> config;
        try {
            config = CloudConfigLoader.loadConfig(Path.of("config.yml"));
        } catch (IOException e) {
            LOGGER.error("Failed to load config from config.yml", e);
            return;
        }

        @SuppressWarnings("unchecked") OCRmyPDFConfig ocrConfig = CloudConfigLoader.getOCRConfig(
                (Map<String, Object>) config.get("ocrmypdf")
        );
        CloudClusterExecutor executor = CloudConfigLoader.createExecutor(config, ocrConfig);

        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));
        executor.init();

        Path inputDirectory = Path.of("input");
        Path outputDirectory = Path.of("output");

        try {
            Files.createDirectories(inputDirectory);
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        FolderWatcher watcher = new FolderWatcher(
                inputDirectory,
                inputPath -> {
                    Path outputPath = outputDirectory.resolve(inputDirectory.relativize(inputPath));

                    executor.scheduleTask(new ClusterTask(new OCRProcess(
                            new TransferringIOProcessListener(inputPath, outputPath),
                            ocrConfig
                    ), inputPath.getFileName().toString()));
                }
        );

        try {
            watcher.startWatching();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}