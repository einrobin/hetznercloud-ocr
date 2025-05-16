package com.github.einrobin.ocr;

import com.github.einrobin.clusterexecutor.cloud.CloudClusterConfig;
import com.github.einrobin.clusterexecutor.cloud.CloudClusterExecutor;
import com.github.einrobin.clusterexecutor.hetzner.HetznerCloudClusterProvider;
import com.github.einrobin.clusterexecutor.hetzner.HetznerInstanceConfig;
import com.github.einrobin.ocr.ocrmypdf.OCRmyPDFConfig;
import com.github.einrobin.ocr.ocrmypdf.OutputType;
import io.github.sinuscosinustan.hetznercloud.HetznerCloudAPI;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unchecked")
public class CloudConfigLoader {

    public static Map<String, Object> loadConfig(Path path) throws IOException {
        Yaml yaml = new Yaml();

        try (InputStream inputStream = Files.newInputStream(path)) {
            return yaml.load(inputStream);
        }
    }

    public static CloudClusterConfig getCloudConfig(Map<String, Object> config) {
        return new CloudClusterConfig(
                ((Number) config.get("maxSimultaneousInstances")).intValue(),
                ((Number) config.get("maxSimultaneousTasksPerInstance")).intValue(),
                TimeUnit.MINUTES.toMillis(((Number) config.get("idleTimeMinutes")).intValue())
        );
    }

    public static OCRmyPDFConfig getOCRConfig(Map<String, Object> config) {
        return new OCRmyPDFConfig(
                ((List<String>) config.get("languages")).toArray(String[]::new),
                (Boolean) config.get("skipText"),
                OutputType.getByName((String) config.get("outputType"))
        );
    }

    public static HetznerInstanceConfig getHetznerInstanceConfig(Map<String, Object> config, OCRmyPDFConfig ocrConfig) {
        return new HetznerInstanceConfig(
                (String) config.get("image"),
                (String) config.get("location"),
                (String) config.get("serverType"),
                ocrConfig.cloudinitConfig(),
                "cloud-init status --wait || test $? -eq 2",
                "done",
                (Map<String, String>) config.get("labels")
        );
    }

    public static CloudClusterExecutor createExecutor(Map<String, Object> config, OCRmyPDFConfig ocrConfig) {
        Map<String, Object> cloudConfig = (Map<String, Object>) config.get("cloud");
        Map<String, Object> hetznerConfig = (Map<String, Object>) cloudConfig.get("hetzner");

        return new CloudClusterExecutor(
                new HetznerCloudClusterProvider(
                        new HetznerCloudAPI((String) hetznerConfig.get("hcloudToken")),
                        CloudConfigLoader.getHetznerInstanceConfig(
                                (Map<String, Object>) hetznerConfig.get("instances"),
                                ocrConfig
                        )
                ),
                CloudConfigLoader.getCloudConfig((Map<String, Object>) cloudConfig.get("limits"))
        );
    }
}
