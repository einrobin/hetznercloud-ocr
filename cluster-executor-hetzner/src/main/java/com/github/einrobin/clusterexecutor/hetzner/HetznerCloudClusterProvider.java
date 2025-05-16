package com.github.einrobin.clusterexecutor.hetzner;

import com.github.einrobin.clusterexecutor.cloud.*;
import com.github.einrobin.clusterexecutor.cloud.exception.CloudInstanceCreateException;
import com.github.einrobin.clusterexecutor.ssh.SSHUtils;
import io.github.sinuscosinustan.hetznercloud.HetznerCloudAPI;
import io.github.sinuscosinustan.hetznercloud.exception.APIRequestException;
import io.github.sinuscosinustan.hetznercloud.objects.general.SSHKey;
import io.github.sinuscosinustan.hetznercloud.objects.general.Server;
import io.github.sinuscosinustan.hetznercloud.objects.request.CreateSSHKeyRequest;
import io.github.sinuscosinustan.hetznercloud.objects.request.CreateServerRequest;
import io.github.sinuscosinustan.hetznercloud.objects.response.CreateServerResponse;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import java.util.stream.Collectors;

public class HetznerCloudClusterProvider implements CloudClusterProvider {

    private final HetznerCloudAPI hetzner;
    private final HetznerInstanceConfig instanceConfig;

    private final String labelSelector;

    private SSHKey hetznerSshKey;
    private KeyPair sshKey;

    public HetznerCloudClusterProvider(HetznerCloudAPI hetzner, HetznerInstanceConfig instanceConfig) {
        this.hetzner = hetzner;
        this.instanceConfig = instanceConfig;

        this.labelSelector = instanceConfig.labels().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    public HetznerInstanceConfig instanceConfig() {
        return this.instanceConfig;
    }

    public HetznerCloudAPI hetzner() {
        return this.hetzner;
    }

    @Override
    public void init() {
        String publicKey;
        try {
            this.sshKey = SSHUtils.generateKeyPair();
            publicKey = SSHUtils.getPublicKeyOpenSSH((RSAPublicKey) this.sshKey.getPublic(), "autoscaler");
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }

        for (Server server : this.hetzner.getServers(this.labelSelector).getServers()) {
            this.hetzner.deleteServer(server.getId());
        }
        for (SSHKey key : this.hetzner.getSSHKeys(this.labelSelector).getSshKeys()) {
            this.hetzner.deleteSSHKey(key.getId());
        }

        this.hetznerSshKey = this.hetzner.createSSHKey(
                CreateSSHKeyRequest.builder()
                        .publicKey(publicKey)
                        .name("autoscaler-ssh-" + UUID.randomUUID().toString().split("-")[0])
                        .labels(this.instanceConfig.labels())
                        .build()
        ).getSshKey();
    }

    @Override
    public void shutdown() {
        this.hetzner.deleteSSHKey(this.hetznerSshKey.getId());
    }

    @Override
    public String name() {
        return "hetznercloud";
    }

    @Override
    public CloudClusterInstance createInstance(CloudClusterExecutor executor) throws CloudInstanceCreateException {
        CreateServerResponse response;
        try {
            response = this.hetzner.createServer(
                    CreateServerRequest.builder()
                            .image(this.instanceConfig.image())
                            .location(this.instanceConfig.location())
                            .startAfterCreate(true)
                            .serverType(this.instanceConfig.serverType())
                            .name("autoscaler-" + UUID.randomUUID().toString().split("-")[0])
                            .sshKey(this.hetznerSshKey.getId())
                            .userData(this.instanceConfig.userData())
                            .labels(this.instanceConfig.labels())
                            .build()
            );
        } catch (APIRequestException e) {
            throw new CloudInstanceCreateException("Failed to create Hetzner server", e);
        }

        Server server = response.getServer();

        return new HetznerClusterInstance(executor, this, this.sshKey, server);
    }

    @Override
    public void deleteInstance(CloudClusterInstanceMeta instance) {
        instance.setState(CloudClusterInstanceState.DELETING);

        HetznerClusterInstance hetznerInstance = (HetznerClusterInstance) instance.instance();
        this.hetzner.deleteServer(hetznerInstance.server().getId());

        instance.setState(CloudClusterInstanceState.DELETED);
    }
}
