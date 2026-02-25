package com.app.service;

import com.app.entity.Submission;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class ExpirationScheduler {

    @Scheduled(every = "1h")
    @Transactional
    void expireSubmissions() {
        Submission.update(
                "status = 'EXPIRED' where status = 'DONE' and expirationDate <= ?1",
                LocalDateTime.now());
    }
}
