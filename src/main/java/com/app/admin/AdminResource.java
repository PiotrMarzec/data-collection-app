package com.app.admin;

import com.app.entity.Submission;
import com.app.entity.SubmissionUpdate;
import com.app.service.SignatureService;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    AdminTokenStore tokenStore;

    @Inject
    SignatureService signatureService;

    @ConfigProperty(name = "app.admin.password")
    String adminPassword;

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    @POST
    @Path("/login")
    public Response login(LoginRequest req) {
        if (req == null || !adminPassword.equals(req.password())) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid password"))
                    .build();
        }
        return Response.ok(new LoginResponse(tokenStore.generate())).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@HeaderParam("Authorization") String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            tokenStore.invalidate(auth.substring(7));
        }
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Link generation
    // -------------------------------------------------------------------------

    @POST
    @Path("/generate-link")
    public Response generateLink(GenerateLinkRequest req) {
        if (req == null || req.dataId() == null || req.dataId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "dataId is required"))
                    .build();
        }
        String signature = signatureService.generate(req.dataId());
        return Response.ok(new GenerateLinkResponse(
                req.dataId(),
                signature,
                "/?dataId=" + req.dataId() + "&signature=" + signature
        )).build();
    }

    // -------------------------------------------------------------------------
    // Submissions
    // -------------------------------------------------------------------------

    @GET
    @Path("/submissions")
    public Response listSubmissions(
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {

        String q = search == null ? "" : search.trim();
        var query = q.isEmpty()
                ? Submission.findAll(Sort.by("createdAt").descending())
                : Submission.find(
                        "lower(email) like ?1 or lower(dataId) like ?1",
                        Sort.by("createdAt").descending(),
                        "%" + q.toLowerCase() + "%");

        long total = query.count();
        List<SubmissionDto> data = query.page(page, size).<Submission>list()
                .stream().map(SubmissionDto::from).toList();

        return Response.ok(new PagedResponse(data, total)).build();
    }

    @GET
    @Path("/submissions/{id}/history")
    public Response getHistory(@PathParam("id") Long id) {
        Submission submission = Submission.findById(id);
        if (submission == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<SubmissionUpdateDto> history = SubmissionUpdate
                .find("submission.id = ?1", Sort.by("createdAt").ascending(), id)
                .list()
                .stream().map(u -> SubmissionUpdateDto.from((SubmissionUpdate) u)).toList();
        return Response.ok(history).build();
    }

    @PUT
    @Path("/submissions/{id}")
    @Transactional
    public Response editSubmission(@PathParam("id") Long id, EditRequest req) {
        Submission submission = Submission.findById(id);
        if (submission == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (req == null || req.email() == null || req.email().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "email is required"))
                    .build();
        }
        String newStatus = req.status() != null ? req.status() : submission.status;
        if (!List.of("NEW", "PROCESSING", "DONE", "EXPIRED").contains(newStatus)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "status must be NEW, PROCESSING, DONE, or EXPIRED"))
                    .build();
        }
        if ("DONE".equals(newStatus) && (req.resultUrl() == null || req.resultUrl().isBlank())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "resultUrl is required when status is DONE"))
                    .build();
        }

        boolean transitioningToDone = "DONE".equals(newStatus) && !"DONE".equals(submission.status);
        LocalDateTime newExpiration = null;
        if (req.expirationDate() != null && !req.expirationDate().isBlank()) {
            newExpiration = LocalDate.parse(req.expirationDate()).atStartOfDay();
        } else if (transitioningToDone) {
            newExpiration = LocalDateTime.now().plusDays(30);
        }

        submission.email = req.email().trim();
        submission.status = newStatus;
        if ("DONE".equals(newStatus)) {
            submission.resultUrl = req.resultUrl().trim();
        }
        if (newExpiration != null) {
            submission.expirationDate = newExpiration;
        }
        submission.updatedAt = LocalDateTime.now();
        submission.persist();
        return Response.ok(SubmissionDto.from(submission)).build();
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    record LoginRequest(String password) {}
    record LoginResponse(String token) {}
    record GenerateLinkRequest(String dataId) {}
    record GenerateLinkResponse(String dataId, String signature, String link) {}
    record EditRequest(String email, String status, String resultUrl, String expirationDate) {}

    record SubmissionDto(
            Long id,
            String dataId,
            String email,
            int updateCount,
            String ipAddress,
            String status,
            String resultUrl,
            LocalDateTime expirationDate,
            LocalDateTime submittedAt,
            LocalDateTime updatedAt,
            LocalDateTime createdAt) {

        static SubmissionDto from(Submission s) {
            return new SubmissionDto(
                    s.id, s.dataId, s.email, s.updateCount,
                    s.ipAddress, s.status, s.resultUrl, s.expirationDate,
                    s.submittedAt, s.updatedAt, s.createdAt);
        }
    }

    record SubmissionUpdateDto(
            Long id,
            String email,
            String ipAddress,
            String userAgent,
            LocalDateTime createdAt) {

        static SubmissionUpdateDto from(SubmissionUpdate u) {
            return new SubmissionUpdateDto(u.id, u.email, u.ipAddress, u.userAgent, u.createdAt);
        }
    }

    record PagedResponse(List<SubmissionDto> data, long total) {}
}
