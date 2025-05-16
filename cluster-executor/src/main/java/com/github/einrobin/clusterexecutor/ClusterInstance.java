package com.github.einrobin.clusterexecutor;

import com.jcraft.jsch.JSchException;
import com.github.einrobin.clusterexecutor.ssh.SSHSession;

public interface ClusterInstance {

    String name();

    String host();

    SSHSession connectSSH() throws JSchException;
}
