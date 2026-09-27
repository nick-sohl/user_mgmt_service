# Aufgabe 1 – Observability

Zentraler Observability-Stack mit `kube-prometheus-stack`. Prometheus scraped
Cluster + `user_mgmt_service`; Grafana visualisiert die Metriken; Alertmanager
verschickt Alerts über einen Webhook-Receiver.

## Akzeptanzkriterien → Umsetzung

| # | Kriterium | Erfüllt durch |
|---|-----------|--------------|
| 1 | `kube-prometheus-stack` per Helm in NS `monitoring` | Umbrella-Chart `helm/monitoring/` (Dependency `kube-prometheus-stack 65.5.0`) + `argocd/application-monitoring.yaml` mit `namespace: monitoring` |
| 2 | CPU + Memory pro Pod via Prometheus | `kube-state-metrics`, `node-exporter`, `kubelet.serviceMonitor.cAdvisor: true` (values.yaml) |
| 3 | Spring Boot Metriken + ServiceMonitor (Request Rate, Response Time, Error Rate) | Micrometer/Actuator-Deps in `build.gradle`, `application.properties` (`management.endpoints.web.exposure.include=prometheus,…`), Security-Whitelist für `/actuator/prometheus`, `backend-servicemonitor.yaml` |
| 4 | Zwei Grafana Dashboards | `helm/monitoring/dashboards/user-mgmt-service.json` (Req Rate / p50-p95-p99 / Error Rate / JVM / In-Flight), `helm/monitoring/dashboards/user-mgmt-cluster.json` (CPU/Mem per Pod, Restarts, Not-Ready Pods) |
| 5 | PrometheusRule mit Alert + Alertmanager-Kanal | `backend-prometheusrule.yaml` (Alerts `UserMgmtHighErrorRate`, `UserMgmtHighLatencyP95`, `UserMgmtPodDown`) + `alertmanager.config.receivers[user-mgmt-webhook]` |
| 6 | Deklarative `values.yaml` im Ops-Repo | `user-mgmt-ops/helm/monitoring/values.yaml` |

## Änderungen im Detail

### `user_mgmt_service`

- **`build.gradle`** – neue Dependencies:
  - `spring-boot-starter-actuator` (4.1.0-M3)
  - `micrometer-registry-prometheus`
- **`src/main/resources/application.properties`** – Actuator-/Metriken-Config:
  - `management.endpoints.web.exposure.include=health,info,prometheus`
  - `management.metrics.tags.application=user-mgmt-service`
  - `management.metrics.distribution.percentiles-histogram.http.server.requests=true`
  - `spring.application.name=user-mgmt-service`
- **`WebSecurityConfig.java`** – Whitelist:
  - `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus` sind ohne JWT erreichbar.

### `user-mgmt-ops` (Helm-Chart `user-mgmt`)

- **`templates/backend-service.yaml`** – Port heisst jetzt `http` (ServiceMonitor-Referenzierung).
- **`templates/backend-deployment.yaml`** – Container-Port erhielt `protocol: TCP` neben `name: http`.
- **`templates/backend-servicemonitor.yaml`** – neu, opt-in via `backend.monitoring.enabled`.
- **`templates/backend-prometheusrule.yaml`** – neu, drei Alerts (5xx-Rate, p95-Latency, Pod-Down).
- **`values.yaml`** – Block `backend.monitoring` (path, interval, `additionalLabels.release: monitoring`).

### `user-mgmt-ops` (neues Chart `monitoring`)

- **`helm/monitoring/Chart.yaml`** – Dependency auf `kube-prometheus-stack 65.5.0`.
- **`helm/monitoring/values.yaml`**:
  - Prometheus scraped nur Objekte mit Label `release: monitoring` (matched vom user-mgmt-Chart).
  - Retention `7d`, PVC `10Gi`.
  - Alertmanager: Route auf Webhook-Receiver `user-mgmt-webhook` (URL per Override anpassen).
  - Grafana: Sidecar picked ConfigMaps mit Label `grafana_dashboard: "1"` auf.
  - `kube-state-metrics`, `node-exporter`, `kubelet.serviceMonitor.cAdvisor: true` aktiv.
- **`helm/monitoring/templates/grafana-dashboards.yaml`** – ConfigMap-Rendering für alle JSON-Dashboards in `dashboards/`.
- **`helm/monitoring/dashboards/user-mgmt-service.json`** – applikationsspezifisch.
- **`helm/monitoring/dashboards/user-mgmt-cluster.json`** – Pod-Ressourcen.

### ArgoCD

- **`argocd/application-monitoring.yaml`** – neue Application, syncronisiert `helm/monitoring/` in NS `monitoring`, `CreateNamespace=true`, `ServerSideApply=true`.

### Repo-Housekeeping

- **`.gitignore`** – ignoriert `helm/*/charts/` und `*.tgz` (werden von `helm dependency build` erzeugt).
- **`README.md`** – Abschnitt „Monitoring" ergänzt.

## Bootstrap

```sh
# 1. Helm-Dependency einmalig ziehen (ArgoCD tut das automatisch beim Sync)
cd user-mgmt-ops/helm/monitoring && helm dependency build

# 2. ArgoCD Application anwenden
kubectl apply -f user-mgmt-ops/argocd/application-monitoring.yaml
```

Danach:

```sh
kubectl -n monitoring port-forward svc/monitoring-grafana 3000:80
# admin / prom-operator
```

## Verifikation (nach Deployment)

- `kubectl -n user-mgmt-prod get servicemonitor,prometheusrule`
- Grafana → Explore → PromQL: `http_server_requests_seconds_count{application="user-mgmt-service"}` liefert Serien.
- Prometheus → Alerts → `UserMgmtHighErrorRate`/`UserMgmtHighLatencyP95`/`UserMgmtPodDown` gelistet.
- Alertmanager → Silences/Receivers zeigt `user-mgmt-webhook`.
