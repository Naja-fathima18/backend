# parking-fee

Parking fee service: per-visit fee and daily collection with a per-car daily cap.

## Requirements

- JDK 21
- No global Maven install is needed; use the Maven Wrapper (`./mvnw` on macOS/Linux/Git Bash, `mvnw.cmd` on Windows).
- No database or Docker is needed.

## Build and test

| Task | Command |
|---|---|
| Compile | `./mvnw -q -B test-compile` |
| Unit tests | `./mvnw -B test` |
| One test | `./mvnw -B test -Dtest=GlobalExceptionHandlerTest` |
| Full check (unit + `*IT` integration tests + formatting) | `./mvnw -B verify` |
| Format code | `./mvnw -q spotless:apply` |
| Check formatting | `./mvnw -q -B spotless:check` |

## Run locally

```
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The service listens on port 8080:

```
curl http://localhost:8080/actuator/health
```

## Errors

Errors are returned as RFC 7807 `application/problem+json`. Validation failures return `400` with an `errors`
list of `{ "field", "message" }` entries; unexpected failures return `500` with a generic detail.
