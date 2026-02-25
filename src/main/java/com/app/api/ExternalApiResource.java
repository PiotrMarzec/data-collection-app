package com.app.api;

import com.app.entity.Submission;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.components.Components;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@OpenAPIDefinition(
        info = @Info(
                title = "Data Collection — External API",
                version = "1.0",
                description = """
                        REST API for external systems to integrate with the data-collection app.

                        **Authentication**: All requests must include an `Authorization: Bearer <token>` header.
                        The token is configured via the `APP_API_TOKEN` environment variable.
                        """
        ),
        security = @SecurityRequirement(name = "bearerAuth"),
        components = @Components(
                securitySchemes = @SecurityScheme(
                        securitySchemeName = "bearerAuth",
                        type = SecuritySchemeType.HTTP,
                        scheme = "bearer",
                        bearerFormat = "API Token",
                        description = "Predefined static token set via the APP_API_TOKEN environment variable."
                )
        )
)
@Tag(name = "Submissions", description = "Operations on submission records")
@Path("/api/external")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExternalApiResource {

    // -------------------------------------------------------------------------
    // List NEW submissions
    // -------------------------------------------------------------------------

    @GET
    @Path("/submissions")
    @Operation(
            summary = "List NEW submissions",
            description = "Returns all submissions whose status is `NEW`, sorted oldest-first. " +
                          "Use this endpoint to poll for submissions that are awaiting processing."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Array of submissions with status NEW",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = SubmissionDto[].class)
                    )
            ),
            @APIResponse(responseCode = "401", description = "Missing or invalid API token")
    })
    public Response listNewSubmissions() {
        List<SubmissionDto> data = Submission
                .find("status = 'NEW'", Sort.by("createdAt").ascending())
                .<Submission>list()
                .stream().map(SubmissionDto::from).toList();
        return Response.ok(data).build();
    }

    // -------------------------------------------------------------------------
    // Update a submission
    // -------------------------------------------------------------------------

    @PUT
    @Path("/submissions/{id}")
    @Transactional
    @Operation(
            summary = "Update a submission",
            description = """
                    Updates the status of a submission and optionally sets the result URL and expiration date.

                    **Validation rules:**
                    - `status` is required.
                    - `resultUrl` is required when `status` is `DONE`.
                    - `expirationDate` is optional; when transitioning to `DONE` and not provided, defaults to 30 days from now.
                    - Only `NEW`, `PROCESSING`, `DONE`, and `EXPIRED` are accepted as status values.
                    """
    )
    @Parameter(name = "id", description = "Internal database ID of the submission", required = true)
    @RequestBody(
            description = "Fields to update on the submission",
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = UpdateRequest.class)
            )
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Submission updated successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = SubmissionDto.class)
                    )
            ),
            @APIResponse(responseCode = "400", description = "Validation error — see response body for details"),
            @APIResponse(responseCode = "401", description = "Missing or invalid API token"),
            @APIResponse(responseCode = "404", description = "Submission not found")
    })
    public Response updateSubmission(@PathParam("id") Long id, UpdateRequest req) {
        Submission submission = Submission.findById(id);
        if (submission == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Submission not found"))
                    .build();
        }

        if (req == null || req.status() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "status is required"))
                    .build();
        }

        String newStatus = req.status();
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

    @Schema(name = "UpdateRequest", description = "Request body for updating a submission")
    record UpdateRequest(
            @Schema(
                    description = "New status for the submission.",
                    enumeration = {"NEW", "PROCESSING", "DONE", "EXPIRED"},
                    required = true
            )
            String status,

            @Schema(
                    description = "URL where the results can be accessed. Required when status is DONE.",
                    example = "https://example.com/results/abc123"
            )
            String resultUrl,

            @Schema(
                    description = "Expiration date for the results in YYYY-MM-DD format. " +
                                  "When omitted and transitioning to DONE, defaults to 30 days from now.",
                    example = "2026-03-31"
            )
            String expirationDate
    ) {}

    @Schema(name = "Submission", description = "A submission record")
    record SubmissionDto(
            @Schema(description = "Internal database ID") Long id,
            @Schema(description = "Unique external identifier for this submission link") String dataId,
            @Schema(description = "Email address submitted by the user") String email,
            @Schema(description = "Number of times the email has been updated") int updateCount,
            @Schema(description = "IP address from which the submission was made") String ipAddress,
            @Schema(description = "Current status", enumeration = {"NEW", "PROCESSING", "DONE", "EXPIRED"}) String status,
            @Schema(description = "URL to the results page. Present only when status is DONE.") String resultUrl,
            @Schema(description = "Date/time after which the submission transitions to EXPIRED") LocalDateTime expirationDate,
            @Schema(description = "Date/time the email was first submitted") LocalDateTime submittedAt,
            @Schema(description = "Date/time the record was last modified") LocalDateTime updatedAt,
            @Schema(description = "Date/time the submission record was created") LocalDateTime createdAt
    ) {
        static SubmissionDto from(Submission s) {
            return new SubmissionDto(
                    s.id, s.dataId, s.email, s.updateCount,
                    s.ipAddress, s.status, s.resultUrl, s.expirationDate,
                    s.submittedAt, s.updatedAt, s.createdAt);
        }
    }
}
