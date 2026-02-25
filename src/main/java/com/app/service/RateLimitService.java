package com.app.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;

@ApplicationScoped
public class RateLimitService {

    private static final int MAX_SUBMISSIONS_PER_IP_PER_DAY = 5;

    @Inject
    EntityManager em;

    /**
     * Check whether the given IP address has exceeded the submission limit
     * in the last 24 hours. Counts both new submissions and updates.
     *
     * @return true if the IP is allowed to submit, false if rate-limited
     */
    public boolean isAllowed(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return true;
        }

        return getRemaining(ipAddress) > 0;
    }

    /**
     * Get the number of submissions remaining for this IP in the current 24h window.
     */
    public int getRemaining(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return MAX_SUBMISSIONS_PER_IP_PER_DAY;
        }

        LocalDateTime since = LocalDateTime.now().minusHours(24);

        long newSubmissions = em.createQuery(
                "SELECT COUNT(s) FROM Submission s WHERE s.ipAddress = :ip AND s.submittedAt >= :since",
                Long.class)
                .setParameter("ip", ipAddress)
                .setParameter("since", since)
                .getSingleResult();

        long updates = em.createQuery(
                "SELECT COUNT(u) FROM SubmissionUpdate u WHERE u.ipAddress = :ip AND u.createdAt >= :since",
                Long.class)
                .setParameter("ip", ipAddress)
                .setParameter("since", since)
                .getSingleResult();

        long used = newSubmissions + updates;
        return Math.max(0, MAX_SUBMISSIONS_PER_IP_PER_DAY - (int) used);
    }
}
