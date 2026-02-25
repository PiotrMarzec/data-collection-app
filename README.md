# Data Collection App

A Quarkus/Java web app that validates incoming links (via MD5 signature) and collects email addresses.

## Flow

1. User arrives via link: `http://localhost:8080/?dataId=abc123&signature=<md5hash>`
2. Backend verifies the signature: `MD5(dataId + secret)`
3. If valid → Welcome page is shown
4. User enters their email → Submit
5. Backend saves `(dataId, email)` to PostgreSQL
6. If the link is invalid or already used → Error page

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **PostgreSQL** running on `localhost:5432`

## Database Setup

```sql
CREATE DATABASE data_collection;
CREATE USER app_user WITH PASSWORD 'app_password';
GRANT ALL PRIVILEGES ON DATABASE data_collection TO app_user;
-- On PostgreSQL 15+, also run:
\c data_collection
GRANT ALL ON SCHEMA public TO app_user;
```

Flyway will automatically create the `submissions` table on first startup.

## Configuration

Edit `src/main/resources/application.properties`:

| Property               | Description                          | Default                |
|------------------------|--------------------------------------|------------------------|
| `app.signature.secret` | Secret key used for MD5 signatures   | `my-super-secret-key`  |
| `quarkus.datasource.*` | PostgreSQL connection settings       | localhost:5432         |

**⚠️ Change `app.signature.secret` in production!**

## Run (Dev Mode)

```bash
./mvnw quarkus:dev
```

The app starts at **http://localhost:8080**.

## Generate a Test Link

Use the dev-only helper endpoint:

```bash
curl "http://localhost:8080/api/generate-link?dataId=test123"
```

Response:
```json
{
  "dataId": "test123",
  "signature": "a1b2c3...",
  "link": "/?dataId=test123&signature=a1b2c3..."
}
```

Open the full link in your browser to test the flow.

## API Endpoints

| Method | Path                 | Description                          |
|--------|----------------------|--------------------------------------|
| GET    | `/api/verify`        | Verify dataId + signature            |
| POST   | `/api/submit`        | Submit email for a verified dataId   |
| GET    | `/api/generate-link` | (DEV) Generate a signed link         |

## Project Structure

```
src/main/java/com/app/
├── dto/
│   └── EmailSubmissionRequest.java   # Request validation DTO
├── entity/
│   ├── Submission.java               # JPA entity (Panache)
│   └── SubmissionUpdate.java         # Audit log entity
├── resource/
│   └── SubmissionResource.java       # REST endpoints
└── service/
    ├── RateLimitService.java         # IP-based rate limiting
    └── SignatureService.java         # MD5 signature logic

src/main/resources/
├── application.properties            # Configuration
├── db/migration/
│   ├── V1__create_submissions_table.sql
│   ├── V2__add_update_count.sql
│   └── V3__add_audit_fields_and_updates_table.sql
└── META-INF/resources/
    └── index.html                    # Frontend (single-page)
```

## Run with Docker

The easiest way to run the app. Requires only Docker and Docker Compose.

```bash
docker compose up --build
```

This starts:
- **PostgreSQL 16** on port `5432`
- **The app** on port `8080`

Flyway runs automatically on startup and creates all tables.

To stop:

```bash
docker compose down          # keeps data
docker compose down -v       # removes data volume too
```

### Environment Variables

Override any setting via environment variables in `docker-compose.yml`:

| Variable                          | Description                    | Default                |
|-----------------------------------|--------------------------------|------------------------|
| `QUARKUS_DATASOURCE_JDBC_URL`     | PostgreSQL connection URL      | (set in compose)       |
| `QUARKUS_DATASOURCE_USERNAME`     | DB username                    | `app_user`             |
| `QUARKUS_DATASOURCE_PASSWORD`     | DB password                    | `app_password`         |
| `APP_SIGNATURE_SECRET`            | Secret for MD5 signatures      | `my-super-secret-key`  |

## Production Notes

- Change `APP_SIGNATURE_SECRET` to a strong, random value
- Change the database password
- Remove or secure the `/api/generate-link` endpoint
- Rate limiting: 5 submissions per IP per 24 hours (configurable in `RateLimitService`)
- Email updates: max 5 per dataId (configurable in `SubmissionResource`)
- MD5 is used here for simplicity; for higher security, consider HMAC-SHA256
