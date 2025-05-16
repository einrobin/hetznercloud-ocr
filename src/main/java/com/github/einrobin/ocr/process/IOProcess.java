package com.github.einrobin.ocr.process;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.github.einrobin.clusterexecutor.ClusterInstance;
import com.github.einrobin.clusterexecutor.ClusterTask;
import com.github.einrobin.clusterexecutor.ClusterTaskProcess;
import com.github.einrobin.clusterexecutor.ssh.SSHSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

public abstract class IOProcess implements ClusterTaskProcess {

    private final String fileExtension;
    private final IOProcessListener listener;

    protected IOProcess(String fileExtension, IOProcessListener listener) {
        this.fileExtension = fileExtension;
        this.listener = listener;
    }

    protected abstract boolean run(SSHSession ssh, ClusterTask task, String inputFile, String outputFile);

    @Override
    public final CompletableFuture<Void> run(ClusterTask task, ClusterInstance server) {
        return CompletableFuture.runAsync(() -> {
            try (SSHSession ssh = server.connectSSH()) {
                ssh.awaitReady();

                String inputFile = task.generateTempFile("input" + this.fileExtension);
                String outputFile = task.generateTempFile("output" + this.fileExtension);

                try (InputStream inputStream = this.listener.openInput()) {
                    ChannelSftp sftp = ssh.openSftp();
                    sftp.put(inputStream, inputFile);
                    sftp.disconnect();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                if (!this.run(ssh, task, inputFile, outputFile)) {
                    throw new RuntimeException("Task execution failed");
                }

                try (OutputStream outputStream = this.listener.openOutput()) {
                    ChannelSftp sftp = ssh.openSftp();
                    sftp.get(outputFile, outputStream);
                    sftp.disconnect();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    this.listener.onSuccess();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } catch (JSchException | SftpException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
