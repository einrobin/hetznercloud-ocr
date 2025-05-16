package com.github.einrobin.clusterexecutor.ssh;

import com.jcraft.jsch.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

public class SSHSession implements AutoCloseable {

    private final JSch jsch;

    private final String host;
    private final int port;
    private final String username;

    private Session session;

    private SSHSession(JSch jsch, String host, int port, String username) {
        this.jsch = jsch;
        this.host = host;
        this.port = port;
        this.username = username;
    }

    public static SSHSession open(String host, int port, String username, KeyPair sshKey) throws JSchException {
        JSch jsch = new JSch();
        jsch.addIdentity(
                "inMemory",
                SSHUtils.getPrivateKeyOpenSSH(sshKey.getPrivate()).getBytes(StandardCharsets.UTF_8),
                sshKey.getPublic().getEncoded(),
                null
        );

        return new SSHSession(jsch, host, port, username);
    }

    private Session getSession() throws JSchException {
        if (this.session != null) {
            return this.session;
        }

        Session session = this.jsch.getSession(this.username, this.host, this.port);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000);
        return this.session = session;
    }

    public ChannelShell openShell() throws JSchException {
        return (ChannelShell) this.getSession().openChannel("shell");
    }

    public ChannelExec openExec() throws JSchException {
        return (ChannelExec) this.getSession().openChannel("exec");
    }

    public byte[] exec(String command) throws JSchException, InterruptedException {
        ChannelExec exec = this.openExec();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        exec.setCommand(command);
        exec.setOutputStream(stream, true);
        exec.setExtOutputStream(stream, true);
        exec.setErrStream(stream, true);

        exec.connect(5000);

        while (exec.isConnected()) {
            Thread.sleep(100);
        }

        return stream.toByteArray();
    }

    public ChannelSftp openSftp() throws JSchException {
        ChannelSftp shell = (ChannelSftp) this.getSession().openChannel("sftp");
        shell.connect();
        return shell;
    }

    public void awaitReady() throws JSchException {
        String message = "I am ready";
        JSchException out = null;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        for (int i = 0; i < 15; i++) {
            try {
                ChannelExec channel = this.openExec();
                channel.setCommand("echo \"" + message + "\"");
                channel.setOutputStream(stream);
                channel.connect(5000);

                while (channel.isConnected()) {
                    Thread.sleep(100);
                }

                if (stream.toString().equals(message + "\n")) {
                    return; // ready
                }
            } catch (JSchException e) {
                out = e;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // next try
            stream.reset();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // 15 retries all failed
        if (out != null) {
            throw out;
        } else {
            throw new IllegalStateException("SSH server was not ready within the given interval");
        }
    }

    @Override
    public void close() {
        if (this.session != null) {
            this.session.disconnect();
            this.session = null;
        }
    }
}
