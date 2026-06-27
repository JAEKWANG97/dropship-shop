# Dropship Shop API

Spring Boot backend for the Dropship Shop MVP.

## Stack

- Java 21
- Spring Boot 4.1
- Gradle
- PostgreSQL for local and production profiles
- H2 for tests only

## Local Profile

Run the API with the `local` profile:

```sh
cd apps/api
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Default local database settings:

```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=dropship_shop
DB_USERNAME=dropship
DB_PASSWORD=dropship
```

The local profile reads the values above from environment variables and falls back to those defaults.

## Health Checks

```sh
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

## Tests

```sh
cd apps/api
./gradlew test
```

The test profile uses an in-memory H2 database in PostgreSQL compatibility mode.
