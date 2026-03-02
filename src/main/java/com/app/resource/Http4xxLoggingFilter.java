package com.app.resource;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

@Provider
public class Http4xxLoggingFilter implements ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(Http4xxLoggingFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        int status = responseContext.getStatus();
        if (status >= 400 && status < 500) {
            MDC.put("event", "http.4xx");
            MDC.put("httpStatus", status);
            MDC.put("method", requestContext.getMethod());
            MDC.put("path", requestContext.getUriInfo().getPath());
            LOG.warn("4xx response");
            MDC.clear();
        }
    }
}
