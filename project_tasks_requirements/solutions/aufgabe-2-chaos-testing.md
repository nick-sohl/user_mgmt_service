# Aufgabe 2 – Chaos / Load Testing mit k6

Kontrolliert steigende Last gegen den `user_mgmt_service` mit **k6**, ausgeführt
als Kubernetes Job im Cluster. Die Auswirkungen sind live in Prometheus /
Grafana sichtbar, HPA + Load-Balancing werden bewiesen.

## Akzeptanzkriterien → Umsetzung

| # | Kriterium | Erfüllt durch |
|---|-----------|--------------|
| 1 | k6 im Cluster ausführbar + ≥ 1 Skript | `user-mgmt-ops/k6/scripts/user-mgmt-load.js` + Job `manifests/k6-job.yaml` (`grafana/k6:0.53.0`) |
| 2 | Kontrolliert steigende Last auf relevanten API-Endpoint | Szenario `register` (`ramping-vus` 1 → 60 VUs über 10 min) gegen `POST /users/register`; parallel dazu `health`-Baseline |
| 3 | Telemetrie in Prometheus/Grafana nachvollziehbar | Dashboard „User Management Service" (Task 1) zeigt Request Rate / p50-p95-p99 / Error Rate; Alerts `UserMgmtHighErrorRate` / `UserMgmtHighLatencyP95` reagieren auf die Last |
| 4 | HPA skaliert hoch + wieder runter | `values.yaml` → `backend.autoscaling` (min 2, max 4, CPU 70 %); k6-Ramp treibt CPU über den Trigger, HPA fährt hoch; nach Ramp-down (2 min bei `target: 0`) schrumpft er nach ~5 min |
| 5 | Verfügbarkeit + Load-Balancing während Skalierung | RollingUpdate `maxSurge: 0`, `maxUnavailable: 1`; PDB `minAvailable: 1`; Service `ClusterIP` mit Default-Session-Affinity `None` verteilt Requests round-robin via kube-proxy |

## Struktur

```
user-mgmt-ops/k6/
├── kustomization.yaml         # kustomize-Entry, generiert ConfigMap aus scripts/
├── manifests/
│   ├── namespace.yaml         # NS loadtest
│   └── k6-job.yaml            # Job (backoffLimit 0, ttl 1h, Ressourcen 1 CPU/512Mi)
├── scripts/
│   └── user-mgmt-load.js      # zwei Szenarien: health (5 VUs) + register (1→60)
└── README.md                  # Ausführung, HPA/Load-Balancing-Verifikation
```

## Ablauf

```sh
kubectl apply -k user-mgmt-ops/k6

# HPA und Pods live beobachten
kubectl -n user-mgmt-prod get hpa -w
kubectl -n user-mgmt-prod get pods -l app.kubernetes.io/component=backend -w

# k6-Ergebnis
kubectl -n loadtest logs -f job/k6-user-mgmt-load
```

## Was sichtbar wird

- **Prometheus/Grafana**: `http_server_requests_seconds_count{application="user-mgmt-service"}` steigt in der Ramp-Phase; Error-Ratio- und p95-Latency-Panels reagieren.
- **HPA**: `kubectl get hpa` zeigt `CPU: 70% → 120% → ...` und Replicas 2 → 3 → 4 während `register`-Peak; nach Ramp-down zurück auf 2 (Downscale-Stabilisierung ~5 min).
- **Alertmanager**: bei überzogener Last löst `UserMgmtHighErrorRate` bzw. `UserMgmtHighLatencyP95` aus und ruft den Webhook-Receiver auf.
- **k6-Threshold-Fail**: die Job-Logs listen p(95), Fehlerrate und Session-Statistiken; `--summary-trend-stats` gibt avg/min/med/p95/p99/max aus.

## Load-Balancing-Nachweis

Nach Skalierung auf ≥ 2 Backend-Pods:

```sh
kubectl -n user-mgmt-prod logs -l app.kubernetes.io/component=backend --tail=200 \
  | grep -oE 'RequestId=[^ ]+' | sort | uniq -c
```

zeigt eine vergleichbare Anzahl bearbeiteter Requests pro Pod, was die
Round-Robin-Verteilung durch `Service` + kube-proxy bestätigt.

## Cleanup

```sh
kubectl delete -k user-mgmt-ops/k6
```
