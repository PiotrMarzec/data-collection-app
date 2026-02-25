package com.app.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
public class ApiAuthFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "app.api.token")
    String apiToken;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();

        if (!path.startsWith("/api/external")) {
            return;
        }

        String auth = ctx.getHeaderString("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            if (apiToken.equals(token)) {
                return;
            }
        }

        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"error\":\"Invalid or missing API token\"}")
                .type("application/json")
                .build());
    }
}
