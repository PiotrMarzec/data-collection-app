# Data Collection App

A Quarkus/Java web app with simple SPA frontend that allows valid incoming links and collects user email addresses.

## User Flow

- User arrives via link (generated via external app), example: `http://example.com/?dataId=abc123&signature=<signature>`
- Backend verifies the signature: `MD5(dataId + secret)`
- If invalid -> Error page
- If valid → Welcome page is shown
- User enters their email → Submit
- If the data id does not exist in database a new submission is created (data id, email, date created, user agent, ip address)
- If data id exists in the database
- If the submission still has a "new" status the user can update the email address for up to 5 times (we have a full audit log)
- If the submission is "processing" the user can't update the email address
- If the submission is "done" the user is show the link to their results and information about the link expiration date
- If the submission is "expired" the user should be able to create a new submission

## Admin panel

- Admin panel has simple password authentication
- Admin panel allows to browse and edit all submissions

## Anti abuse

App has simple anti abuse behaviour:

- Only 5 submissions can be created from one IP address
- Each submission allows only 5 email adress updates

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

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **PostgreSQL** running on `localhost:5432`

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
