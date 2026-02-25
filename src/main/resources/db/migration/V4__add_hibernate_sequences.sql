-- Hibernate 6 (Quarkus 3.x) uses sequences named {table}_SEQ with an
-- allocation size of 50 by default. BIGSERIAL creates {table}_id_seq,
-- so these sequences must be created explicitly.
CREATE SEQUENCE IF NOT EXISTS submissions_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS submission_updates_SEQ START WITH 1 INCREMENT BY 50;
