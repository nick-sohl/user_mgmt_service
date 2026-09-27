# Aufgabe 6 – Microservices (module_service, 40 %)

Erweitert die Landscape um einen Python/FastAPI `module_service` mit DO
Managed MySQL. `user_mgmt_service` orchestriert die Modulzuweisung über REST
(synchron, mit Timeout/Retry/Circuit Breaker). GitOps-Pipeline erweitert.

## Akzeptanzkriterien → Umsetzung

| # | Kriterium | Erfüllt durch |
|---|-----------|--------------|
| 1 | Neuer Endpoint zur Modulzuweisung im user_mgmt_service | `UserModuleController.PUT /users/{userId}/modules/{moduleId}` (`domain/module/UserModuleController.java`) |
| 2 | Vorab-Check der Verfügbarkeit via module_service | `ModuleServiceClient.isAvailable()` (GET `/api/v1/modules/{id}`), erst danach `assign()` |
| 3 | Synchron via REST + Timeout + Retry + Circuit Breaker | Spring `RestClient` + Resilience4j (`@Retry`, `@CircuitBreaker`, `@TimeLimiter`) – Konfig in `application.properties` unter `resilience4j.*` |
| 4 | Kein direkter DB-Zugriff des user_mgmt_service auf MySQL | user_mgmt_service verwendet weiterhin nur seine Postgres; MySQL-Zugriff liegt ausschliesslich beim module_service (kein Datasource-Bean, kein Secret) |
| 5 | E2E-Kommunikation + korrekte HTTP-Statuscodes | Erfolgsfall → 204; Modul unbekannt → 404 (`MODULE_NOT_FOUND`); Upstream-Down → 503 (`MODULE_SERVICE_UNAVAILABLE`) – siehe `@ExceptionHandler` im Controller |
| 6 | ServiceMonitor + Grafana Dashboard (RR, RT, ER) für module_service | `helm/module-service/templates/servicemonitor.yaml` + `dashboards/module-service.json` (Request Rate, p50/p95/p99, Error Rate, In-Flight, Restarts) |
| 7 | CPU/Memory Limits für stabile vertikale Skalierung | `values.yaml → resources` (requests 200m/256Mi, limits 1CPU/768Mi) + PDB `minAvailable: 1` |
| 8 | module_service erfüllt ClusterPolicies | Deployment mit `runAsNonRoot`, `seccompProfile`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`, expliziter Tag (kein `:latest`), Requests/Limits + Probes gesetzt |
| 9 | GitOps-Pipeline erweitert | `module_service/.github/workflows/deploy.yml` baut Image, pushed auf Docker Hub, bumpt `helm/module-service/values.yaml → image.tag` im Ops-Repo |

## Repo-Änderungen

### `module_service/` (Yagans Repo, geklont)

- **`app/main.py`** – `prometheus-fastapi-instrumentator` gibt `/metrics` frei; neuer `/health`-Endpoint (200 OK).
- **`pyproject.toml`** – Dependency `prometheus-fastapi-instrumentator>=7,<8`.
- **`Dockerfile`** – neu, multi-stage (build via `uv`, runtime `python:3.12-alpine`), non-root `app` User, `EXPOSE 8080`.
- **`.dockerignore`** – neu.
- **`.github/workflows/deploy.yml`** – neu, spiegelt den user_mgmt_service Workflow (build → push → yq-bump im Ops-Repo).

### `user_mgmt_service/`

- **`build.gradle`** – Resilience4j (`spring-boot3`, `micrometer`) + Spring AOP.
- **`src/main/resources/application.properties`** – `module-service.*` (Base-URL + Timeouts) und `resilience4j.retry/circuitbreaker/timelimiter.instances.module-service.*`.
- **`domain/module/`** – neu:
  - `ModuleServiceProperties` – `@ConfigurationProperties("module-service")`.
  - `ModuleServiceConfig` – RestClient-Bean mit Connect/Read-Timeout.
  - `ModuleServiceClient` – `isAvailable`, `assign` (+ async Variante) mit `@CircuitBreaker` + `@Retry` + `@TimeLimiter`; Fallbacks werfen `ModuleServiceUnavailableException`.
  - `ModuleNotFoundException`, `ModuleServiceUnavailableException` (Domain-Exceptions).
  - `UserModuleController` – neuer Endpoint + `@ExceptionHandler` für 404 / 503.

### `user-mgmt-ops/` (Ops-Repo)

- **`terraform/database.tf`** – neuer `digitalocean_database_cluster.mysql` (MySQL 8, `db-s-1vcpu-1gb`), inkl. `digitalocean_database_db`, `_user`, `_firewall`.
- **`terraform/variables.tf`** – Object-Variable `mysql` (analog zu `postgres`).
- **`terraform/outputs.tf`** – `mysql_host`, `_port`, `_database`, `_username`, `_password` (sensitive), `_sqlalchemy_url` (sensitive).
- **`terraform/terraform.tfvars.example`** – Beispiel-Block `mysql`.
- **`helm/module-service/`** – neues Chart mit `Chart.yaml`, `values.yaml`, `_helpers.tpl`, `deployment.yaml`, `service.yaml`, `pdb.yaml`, `db-secret.yaml`, `servicemonitor.yaml`, `prometheusrule.yaml`, `grafana-dashboards.yaml`, `dashboards/module-service.json`, `README.md`.
- **`argocd/application-module-service-prod.yaml`** – neue ArgoCD Application → NS `module-service-prod`.
- **`README.md`** – Layout-Abschnitt um `helm/module-service/` ergänzt.

## Flow

```
Client
  │  Bearer JWT
  ▼
user_mgmt_service (Spring Boot)
  │  RestClient(baseUrl=http://module-service-prod.module-service-prod.svc.cluster.local:8080)
  │  ├── GET /api/v1/modules/{moduleId}          ← Resilience4j Retry + CircuitBreaker
  │  └── PUT /api/v1/users/{userId}/modules/{id} ← Resilience4j Retry + CircuitBreaker
  ▼
module_service (FastAPI)
  │  SQLAlchemy + PyMySQL
  ▼
DO Managed MySQL (private_host)
```

## Resilience-Einstellungen

| Baustein | Wert | Zweck |
|----------|------|-------|
| Retry `max-attempts` | 3 | drei Anläufe |
| Retry `wait-duration` + `exp multiplier` | 200 ms × 2 | schneller erster Retry, dann Abstand |
| Retry `retry-exceptions` | `ResourceAccessException`, `HttpServerErrorException` | nur transient |
| Retry `ignore-exceptions` | `ModuleNotFoundException`, `HttpClientErrorException.NotFound` | 404 nicht wiederholen |
| Breaker `sliding-window-size` / `min-calls` | 20 / 10 | statistisch aussagekräftig |
| Breaker `failure-rate-threshold` | 50 % | klassisches Ratio |
| Breaker `wait-in-open-state` / `half-open-calls` | 30 s / 5 | half-open Sondierung |
| TimeLimiter | 3 s | harte wall-clock Grenze (async Pfad) |

Circuit-Breaker- und Retry-Metriken (`resilience4j_*_calls_total`, `_state`)
werden von Micrometer automatisch für Prometheus exportiert und sind über
`http_server_requests_seconds_*` hinaus im Grafana verfügbar.

## Verifikation

```sh
# Positiv
curl -X PUT -H "Authorization: Bearer $JWT" \
  https://user-mgmt-service.duckdns.org/backend/users/$UID/modules/$MID
# 204

# Modul unbekannt
curl -X PUT -H "Authorization: Bearer $JWT" \
  https://user-mgmt-service.duckdns.org/backend/users/$UID/modules/00000000-0000-0000-0000-000000000000
# 404 {"code":"MODULE_NOT_FOUND", "message":"..."}

# module_service komplett down
kubectl -n module-service-prod scale deploy module-service-prod --replicas=0
curl -X PUT -H "Authorization: Bearer $JWT" ...
# 503 {"code":"MODULE_SERVICE_UNAVAILABLE", ...}

# Grafana → Dashboard "Module Service" zeigt Live-Request-Rate + Latency.
# Circuit-Breaker Zustand via /actuator/circuitbreakers auf user_mgmt_service.
```

## Offene Punkte

- Managed MySQL requires TLS in Prod – aktueller PyMySQL-Client hat
  `ssl_disabled=True` als Default; für Prod `MYSQL_SSL_DISABLED=false` setzen
  und CA-Bundle mounten (siehe `values.yaml → database.sslDisabled`).
- module_service `schema.sql` läuft aktuell noch manuell; für Prod idealerweise
  Alembic-Migrationen im Startup-Init-Container oder in einem Job.
