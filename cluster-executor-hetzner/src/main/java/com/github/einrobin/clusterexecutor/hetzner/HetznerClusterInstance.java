package com.github.einrobin.clusterexecutor.hetzner;

import com.github.einrobin.clusterexecutor.Tickable;
import com.github.einrobin.clusterexecutor.cloud.CloudClusterExecutor;
import com.github.einrobin.clusterexecutor.cloud.CloudClusterInstance;
import com.github.einrobin.clusterexecutor.cloud.CloudClusterInstanceMeta;
import com.github.einrobin.clusterexecutor.cloud.CloudClusterInstanceState;
import com.github.einrobin.clusterexecutor.ssh.SSHSession;
import com.jcraft.jsch.JSchException;
import io.github.sinuscosinustan.hetznercloud.exception.APIRequestException;
import io.github.sinuscosinustan.hetznercloud.objects.general.APIErrorCode;
import io.github.sinuscosinustan.hetznercloud.objects.general.Server;
import io.github.sinuscosinustan.hetznercloud.objects.response.ServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HetznerClusterInstance implements CloudClusterInstance, Tickable {

    private static final long UPDATE_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final Logger LOGGER = LoggerFactory.getLogger(HetznerClusterInstance.class);

    private final CloudClusterExecutor executor;
    private final HetznerCloudClusterProvider provider;
    private final KeyPair sshKey;
    private Server server;

    private CloudClusterInstanceMeta meta;
    private long lastTick = System.currentTimeMillis();

    private CompletableFuture<CloudClusterInstanceState> runningValidator;

    public HetznerClusterInstance(CloudClusterExecutor executor, HetznerCloudClusterProvider provider, KeyPair sshKey, Server server) {
        this.executor = executor;
        this.provider = provider;
        this.sshKey = sshKey;
        this.server = server;
    }

    @Override
    public void setMeta(CloudClusterInstanceMeta meta) {
        this.meta = meta;
    }

    public Server server() {
        return this.server;
    }

    @Override
    public String name() {
        return this.server.getName();
    }

    @Override
    public String host() {
        return this.server.getPublicNet().getIpv4().getIp();
    }

    @Override
    public SSHSession connectSSH() throws JSchException {
        return SSHSession.open(this.host(), 22, "root", this.sshKey);
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() - this.lastTick < UPDATE_INTERVAL_MILLIS) {
            return;
        }

        this.lastTick = System.currentTimeMillis();

        ServerResponse response;
        try {
            response = this.provider.hetzner().getServer(this.server.getId());
        } catch (APIRequestException e) {
            if (e.getApiErrorResponse().getError().getCode() == APIErrorCode.not_found) {
                this.meta.setState(CloudClusterInstanceState.DELETED);
                return;
            } else {
                throw e;
            }
        }

        this.server = response.getServer();

        this.meta.setState(switch (this.server.getStatus()) {
            case "initializing", "off" -> CloudClusterInstanceState.CREATING;
            case "starting" -> CloudClusterInstanceState.STARTING;
            case "running" -> this.validateRunning();
            case "stopping" -> CloudClusterInstanceState.STOPPING;
            case "deleting" -> CloudClusterInstanceState.DELETING;
            case "migrating", "rebuilding", "unknown" ->
                    throw new IllegalStateException("Didn't expect server " + this.server.getName() + " to be in state " + this.server.getStatus());
            default ->
                    throw new IllegalStateException("Unknown state of server " + this.server.getName() + ": " + this.server.getStatus());
        });
    }

    private CloudClusterInstanceState validateRunning() {
        if (this.runningValidator == null) {
            this.runningValidator = new CompletableFuture<>();

            this.executor.instanceReadyExecutor().execute(() -> {
                LOGGER.info("Awaiting for instance {} to be ready...", this.name());

                try (SSHSession ssh = this.connectSSH()) {
                    ssh.awaitReady();

                    String readyCommand = this.provider.instanceConfig().instanceReadyCommand();

                    if (readyCommand != null && !readyCommand.isBlank()) {
                        String readyResult = new String(ssh.exec(readyCommand));
                        String expectedResult = this.provider.instanceConfig().instanceReadyResult();

                        if (!readyResult.contains(expectedResult)) {
                            throw new RuntimeException("Ready command output did not contain the expected text: " + expectedResult + ", got: " + readyResult);
                        }
                    }

                    LOGGER.info("Instance {} is ready now!", this.name());
                    this.runningValidator.complete(CloudClusterInstanceState.RUNNING);
                } catch (Throwable t) {
                    LOGGER.error("Instance {} failed to become ready", this.name(), t);
                    this.runningValidator.complete(CloudClusterInstanceState.FAILED);
                }
            });
        }

        return this.runningValidator.getNow(CloudClusterInstanceState.STARTING);
    }
}
