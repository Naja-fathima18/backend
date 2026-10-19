# be-interview-prep

Spring Boot 3.5 / Java 21 backend hosting five interview exercises behind one consistent REST and error contract.

## Build, test and run

Requires only JDK 21; Maven is provided by the wrapper. The database is in-memory H2 (PostgreSQL mode), so no database setup is needed.

```bash
./mvnw -B test
./mvnw -B verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl http://localhost:8080/actuator/health
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

| # | Question | PR link |
|---|---|---|
| 1 | Library API | [#11](https://github.com/Naja-fathima18/backend/pull/11) |
| 2 | Expense Tracker | [#9](https://github.com/Naja-fathima18/backend/pull/9) |
| 3 | File Upload Service | [#10](https://github.com/Naja-fathima18/backend/pull/10) |
| 4 | API Rate Limiting | [#12](https://github.com/Naja-fathima18/backend/pull/12) |
| 5 | Appointment Booking | |

Video:
