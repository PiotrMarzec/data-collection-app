package com.app.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "submissions")
public class Submission extends PanacheEntity {

    @Column(name = "data_id", nullable = false, unique = true)
    public String dataId;

    @Column(nullable = false)
    public String email;

    @Column(name = "update_count", nullable = false)
    public int updateCount = 0;

    @Column(name = "ip_address", length = 45)
    public String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    public String userAgent;

    @Column(name = "submitted_at", nullable = false)
    public LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    public String status = "NEW";

    @Column(name = "result_url", columnDefinition = "TEXT")
    public String resultUrl;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    public List<SubmissionUpdate> updates = new ArrayList<>();

    /**
     * Find a submission by its dataId.
     */
    public static Submission findByDataId(String dataId) {
        return find("dataId", dataId).firstResult();
    }
}
