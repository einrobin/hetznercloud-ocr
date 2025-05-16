package com.github.einrobin.clusterexecutor.hetzner;

import java.util.Map;

public record HetznerInstanceConfig(String image, String location, String serverType, String userData,
                                    String instanceReadyCommand, String instanceReadyResult, Map<String, String> labels) {
}
