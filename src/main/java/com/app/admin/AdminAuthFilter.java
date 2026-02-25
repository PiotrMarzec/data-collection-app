package com.app.admin;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AdminAuthFilter implements ContainerRequestFilter {

    @Inject
    AdminTokenStore tokenStore;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();

        if (!path.startsWith("/admin") || path.startsWith("/admin/login")) {
            return;
        }

        String auth = ctx.getHeaderString("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            if (tokenStore.validate(token)) {
                return;
            }
        }

        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }
}
