package com.app.admin;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class AdminTokenStore {

    private final Set<String> tokens = Collections.synchronizedSet(new HashSet<>());

    public String generate() {
        String token = UUID.randomUUID().toString();
        tokens.add(token);
        return token;
    }

    public boolean validate(String token) {
        return token != null && tokens.contains(token);
    }

    public void invalidate(String token) {
        tokens.remove(token);
    }
}
