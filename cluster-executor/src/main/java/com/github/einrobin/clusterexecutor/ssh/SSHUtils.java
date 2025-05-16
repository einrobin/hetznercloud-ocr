package com.github.einrobin.clusterexecutor.ssh;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public class SSHUtils {

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);

        return generator.generateKeyPair();
    }

    public static String getPrivateKeyOpenSSH(PrivateKey privateKey) {
        try (StringWriter stringWriter = new StringWriter();
             JcaPEMWriter writer = new JcaPEMWriter(stringWriter)) {
            writer.writeObject(privateKey);
            writer.flush();

            return stringWriter.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getPublicKeyOpenSSH(RSAPublicKey publicKey, String comment) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeString(out, "ssh-rsa");
        writeBigInt(out, publicKey.getPublicExponent());
        writeBigInt(out, publicKey.getModulus());

        String publicKeyBase64 = Base64.getEncoder().encodeToString(out.toByteArray());

        return "ssh-rsa " + publicKeyBase64 + " " + comment;
    }

    private static void writeString(ByteArrayOutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes("UTF-8");
        writeUInt32(out, bytes.length);
        out.write(bytes);
    }

    private static void writeBigInt(ByteArrayOutputStream out, BigInteger value) throws IOException {
        byte[] bytes = value.toByteArray();
        writeUInt32(out, bytes.length);
        out.write(bytes);
    }

    private static void writeUInt32(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
