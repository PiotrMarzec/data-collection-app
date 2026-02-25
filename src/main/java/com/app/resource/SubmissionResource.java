package com.app.resource;

import com.app.dto.EmailSubmissionRequest;
import com.app.entity.Submission;
import com.app.entity.SubmissionUpdate;
import com.app.service.RateLimitService;
import com.app.service.SignatureService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.vertx.core.http.HttpServerRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SubmissionResource {

    private static final int MAX_UPDATES = 5;

    @Inject
    SignatureService signatureService;

    @Inject
    RateLimitService rateLimitService;

    /**
     * GET /api/verify?dataId=xxx&signature=yyy
     *
     * Validates the signature for the given dataId.
     * Returns whether this is a new submission or an update,
     * and how many updates remain.
     */
    @GET
    @Path("/verify")
    public Response verify(@QueryParam("dataId") String dataId,
                           @QueryParam("signature") String signature) {

        if (!signatureService.verify(dataId, signature)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Invalid or missing signature"))
                    .build();
        }

        Submission existing = Submission.findByDataId(dataId);

        if (existing != null && !"NEW".equals(existing.status)) {
            var lockedResp = new HashMap<String, Object>();
            lockedResp.put("valid", true);
            lockedResp.put("dataId", dataId);
            lockedResp.put("locked", true);
            lockedResp.put("status", existing.status);
            lockedResp.put("currentEmail", existing.email);
            if (existing.resultUrl != null) {
                lockedResp.put("resultUrl", existing.resultUrl);
            }
            return Response.ok(lockedResp).build();
        }

        if (existing != null && existing.updateCount >= MAX_UPDATES) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of(
                            "error", "Maximum number of email updates reached. No further changes are allowed.",
                            "maxUpdatesReached", true
                    ))
                    .build();
        }

        if (existing != null) {
            return Response.ok(Map.of(
                    "valid", true,
                    "dataId", dataId,
                    "isUpdate", true,
                    "currentEmail", existing.email,
                    "updatesRemaining", MAX_UPDATES - existing.updateCount
            )).build();
        }

        return Response.ok(Map.of(
                "valid", true,
                "dataId", dataId,
                "isUpdate", false
        )).build();
    }

    /**
     * POST /api/submit
     *
     * Saves or updates the email address associated with the dataId.
     * Updates are allowed up to MAX_UPDATES times.
     * Rate-limited to 5 submissions per IP per 24 hours.
     * Captures IP address and User-Agent for audit purposes.
     */
    @POST
    @Path("/submit")
    @Transactional
    public Response submit(@Valid EmailSubmissionRequest request,
                           @Context HttpServerRequest httpRequest,
                           @Context HttpHeaders headers) {

        // Re-verify the signature
        if (!signatureService.verify(request.dataId, request.signature)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Invalid signature"))
                    .build();
        }

        String ipAddress = resolveClientIp(httpRequest);
        String userAgent = headers.getHeaderString("User-Agent");

        // Check IP rate limit
        if (!rateLimitService.isAllowed(ipAddress)) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(Map.of(
                            "error", "Too many submissions from this IP address. Please try again later.",
                            "rateLimited", true
                    ))
                    .build();
        }

        Submission existing = Submission.findByDataId(request.dataId);

        if (existing != null) {
            // Check status lock
            if (!"NEW".equals(existing.status)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(Map.of(
                                "error", "This submission is locked and cannot be modified.",
                                "locked", true,
                                "status", existing.status
                        ))
                        .build();
            }

            // Check update limit
            if (existing.updateCount >= MAX_UPDATES) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(Map.of(
                                "error", "Maximum number of email updates reached. No further changes are allowed.",
                                "maxUpdatesReached", true
                        ))
                        .build();
            }

            // Log the update in the audit table
            SubmissionUpdate update = new SubmissionUpdate();
            update.submission = existing;
            update.email = request.email;
            update.ipAddress = ipAddress;
            update.userAgent = userAgent;
            update.persist();

            // Update the main record
            existing.email = request.email;
            existing.ipAddress = ipAddress;
            existing.userAgent = userAgent;
            existing.updateCount++;
            existing.updatedAt = LocalDateTime.now();
            existing.persist();

            int remaining = MAX_UPDATES - existing.updateCount;
            return Response.ok(Map.of(
                    "success", true,
                    "message", "Email updated successfully",
                    "updatesRemaining", remaining
            )).build();
        }

        // New submission
        Submission submission = new Submission();
        submission.dataId = request.dataId;
        submission.email = request.email;
        submission.ipAddress = ipAddress;
        submission.userAgent = userAgent;
        submission.updateCount = 0;
        submission.submittedAt = LocalDateTime.now();
        submission.persist();

        return Response.ok(Map.of(
                "success", true,
                "message", "Email registered successfully"
        )).build();
    }

    /**
     * GET /api/generate-link?dataId=xxx
     *
     * Utility endpoint (DEV ONLY) to generate a valid signed link.
     * Remove or protect this in production!
     */
    @GET
    @Path("/generate-link")
    public Response generateLink(@QueryParam("dataId") String dataId) {
        if (dataId == null || dataId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "dataId is required"))
                    .build();
        }

        String signature = signatureService.generate(dataId);
        return Response.ok(Map.of(
                "dataId", dataId,
                "signature", signature,
                "link", "/?dataId=" + dataId + "&signature=" + signature
        )).build();
    }

    /**
     * Resolve the real client IP, respecting X-Forwarded-For
     * for deployments behind a reverse proxy / load balancer.
     */
    private String resolveClientIp(HttpServerRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.remoteAddress() != null
                ? request.remoteAddress().host()
                : null;
    }
}
