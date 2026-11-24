# Scooter Rental Backend (Java Spring Boot + PostgreSQL + MQTT)

Implements REST endpoints for the existing [Scooter-IoT-Sim](https://github.com/isamsami/Scooter-IoT-Sim) simulator and the provided `scooter_rental` PostgreSQL schema. **This is a student prototype**, not a production rental/payment platform.

## Prerequisites
- Java 17+, Maven 3.9+ (or install via IntelliJ)
- PostgreSQL with the supplied SQL dump imported into a database named `scooter_rental`
- Mosquitto broker listening at `localhost:1883`
- The Java scooter simulator running on the same broker

## First-time setup (Windows PowerShell)

1. Create an empty database `scooter_rental` in pgAdmin (or `createdb -U postgres scooter_rental`). Import the uploaded `scooter_rental(1).sql` using **psql** (not the pgAdmin query window, because the dump contains `COPY FROM stdin` commands):

   ```powershell
   psql -U postgres -d scooter_rental -f "C:\path\to\scooter_rental(1).sql"
   ```

   If the schema already exists in your local database, **do not reimport**; doing so creates duplicate-table errors.

2. Apply the additive backend migration:

   ```powershell
   psql -U postgres -d scooter_rental -f .\sql\001_extensions.sql
   ```

3. Set your local password and URLs (do **not** commit passwords):

   ```powershell
   $env:DB_USER="postgres"
   $env:DB_PASSWORD="YOUR_POSTGRES_PASSWORD"
   $env:DATABASE_URL="jdbc:postgresql://localhost:5432/scooter_rental"
   $env:MQTT_URL="tcp://localhost:1883"
   ```

4. Start Mosquitto (`mosquitto -v`), then start this backend:

   ```powershell
   mvn spring-boot:run
   ```

5. In a separate terminal, clone the simulator and run it with `mvn exec:java` from the simulator's repository folder. It sends telemetry every 2 seconds. The backend automatically inserts scooters 1–10 on receiving valid telemetry.

6. Visit `http://localhost:8080/api/health` to check the API.

**Important:** the simulator uses `localhost:1883` and hardcodes 10 scooters and their IDs. Running the simulator in a different PC/container requires changing its broker URL, or making the broker reachable accordingly. Do not run two simulator copies with the same scooter IDs.

## Demo REST requests (PowerShell)

```powershell
$base="http://localhost:8080/api"
$signup=@{name="Demo User";email="demo@example.com";password="Passw0rd123!"}|ConvertTo-Json
Invoke-RestMethod "$base/auth/register" -Method Post -ContentType "application/json" -Body $signup
$login=@{email="demo@example.com";password="Passw0rd123!"}|ConvertTo-Json
$auth=Invoke-RestMethod "$base/auth/login" -Method Post -ContentType "application/json" -Body $login
$h=@{Authorization="Bearer $($auth.accessToken)"}
Invoke-RestMethod "$base/scooters" -Headers $h
$ride=Invoke-RestMethod "$base/rides/start" -Method Post -Headers $h -ContentType "application/json" -Body '{"qrCode":"SCT-001"}'
# Wait for a few telemetry cycles, then:
Invoke-RestMethod "$base/rides/$($ride.ride_id)/end" -Method Post -Headers $h
Invoke-RestMethod "$base/rides/$($ride.ride_id)/payment/demo-complete" -Method Post -Headers $h
Invoke-RestMethod "$base/rides" -Headers $h
```

To make your own registered user an admin **locally**, execute the following in PostgreSQL (not through an unauthenticated API):

```sql
UPDATE users SET role='admin' WHERE email='demo@example.com';
```

Log in again after promoting the user. This is a development-only admin bootstrap procedure.

## API reference

All endpoints except `/api/health`, `/api/auth/register`, and `/api/auth/login` require `Authorization: Bearer <token>`.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Health |
| POST | `/api/auth/register` | Create account; `{name,email,password}` |
| POST | `/api/auth/login` | Return accessToken; `{email,password}` |
| GET | `/api/auth/me` | Logged-in user |
| POST | `/api/auth/logout` | Revoke access token |
| GET | `/api/scooters?status=available` | Find scooters |
| GET | `/api/scooters/{id}` | Scooter details |
| GET | `/api/scooters/{id}/telemetry?limit=30` | Recent telemetry |
| POST | `/api/rides/start` | Start a ride `{qrCode:"SCT-001"}` |
| POST | `/api/rides/{id}/end` | End a ride |
| GET | `/api/rides` | Own ride history |
| GET | `/api/rides/{id}/payment` | Own payment |
| POST | `/api/rides/{id}/payment/demo-complete` | **Simulated** payment completion; no actual charge |
| GET | `/api/admin/overview` | Fleet counts and demo revenue |
| GET | `/api/admin/users` | Accounts, excluding password hashes |
| GET | `/api/admin/rides` | All rides |
| GET | `/api/admin/maintenance` | Maintenance records |
| POST | `/api/admin/maintenance` | `{scooterId,issueDescription}` |
| PATCH | `/api/admin/maintenance/{id}` | `{status:"resolved"}` |
| POST | `/api/admin/scooters/{id}/command` | Admin `LOCK` only (unlock requires a ride) |

## MQTT contract (from simulator source)

- Telemetry topic: `scooters/{id}/telemetry`, QoS 1
- Commands: `scooters/{id}/command`, UTF-8 strings `LOCK` and `UNLOCK`, QoS 1
- Example telemetry:

  ```json
  {"scooterId":1,"qrCode":"SCT-001","batteryLevel":80,"latitude":43.9454,"longitude":-78.8964,"status":"available","speedKmh":0.0,"timestamp":1795450000000}
  ```

The telemetry listener upserts scooter IDs 1–10, updates location/battery, logs recent measurements and adds approximate GPS distances to active rides. Pricing is a configurable **demo assumption** in `RideService`: $1 unlock + $0.25 per started minute, minimum 1 minute. No external payment provider is integrated.

## Known limitations / production work

- Command publish acknowledgement is **not confirmation that hardware unlocked**. Real use needs command IDs, scooter acknowledgements, timeout/retry compensation, offline detection, and robust distributed transaction recovery. If MQTT publish succeeds but SQL fails, the simulator might unlock without a ride.
- Payments are simulated; never represent them as real card authorizations or charges.
- The simulator derives status from battery/lock state and does not emit explicit command acknowledgement, so maintenance status and lock transitions are approximations. Its telemetry lacks distance/odometer; distance is estimated from changing GPS coordinates, which can differ from simulated speed.
- Telemetry messages can arrive out of order and are not authenticated per scooter. Add device identity/authentication and timestamp ordering for production.
- Use TLS, broker ACLs, database least privilege, rate limiting, secrets management, audit logs, token cleanup, test coverage, and proper role provisioning before any internet deployment.
- Single Java service for easy demo; can be split into microservices once contracts are tested.
