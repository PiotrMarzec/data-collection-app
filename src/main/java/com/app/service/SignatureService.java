package com.app.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@ApplicationScoped
public class SignatureService {

    @ConfigProperty(name = "app.signature.secret")
    String secret;

    /**
     * Verify that the provided signature matches MD5(dataId + secret).
     */
    public boolean verify(String dataId, String signature) {
        if (dataId == null || signature == null) {
            return false;
        }
        String expected = computeMd5(dataId + secret);
        return expected.equalsIgnoreCase(signature);
    }

    /**
     * Generate a valid signature for a given dataId (useful for testing).
     */
    public String generate(String dataId) {
        return computeMd5(dataId + secret);
    }

    private String computeMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
