# Aufgabe 5 – Kyverno Policy as Code

Kyverno installiert im NS `policy`, ClusterPolicies deklarativ im Ops-Repo,
ArgoCD-verwaltet. Bestehende Workloads bleiben grün, ein Demo-Manifest zeigt
Enforcement live.

## Akzeptanzkriterien → Umsetzung

| # | Kriterium | Erfüllt durch |
|---|-----------|--------------|
| 1 | Kyverno via Helm in NS `policy` | Umbrella-Chart `helm/policy/` (Dependency `kyverno 3.3.5`) + `argocd/application-policy.yaml` |
| 2 | ≥ 3 passende ClusterPolicies | vier Policies unter `helm/policy/templates/`: `require-resource-limits`, `disallow-latest-tag`, `require-run-as-non-root`, `require-probes` |
| 3 | Verstoss wird von Kyverno abgelehnt | Demo-Manifest `helm/policy/samples/bad-deployment.yaml` verletzt alle vier Regeln; `kubectl apply` liefert Admission-Fehler mit Auflistung |
| 4 | Deklarativ im Ops-Repo | Chart, ArgoCD-Application und Sample-Manifest liegen im Git-Repo `user-mgmt-ops` |

## Policies

| Policy | Was sie prüft | Wo sie greift |
|--------|---------------|---------------|
| `require-resource-limits` | Jeder Container braucht CPU + Memory Requests + Limits | Alle Pods außer `kube-*`, `policy`, `argocd`, `monitoring`, `loadtest` |
| `disallow-latest-tag` | Image-Tag muss explizit sein und darf nicht `:latest` heißen | Alle Pods außer System-Namespaces |
| `require-run-as-non-root` | `runAsNonRoot: true` auf Pod- oder Container-Ebene | Alle Pods außer System-Namespaces |
| `require-probes` | `readinessProbe` + `livenessProbe` auf jedem Container | Deployments + StatefulSets außer System-Namespaces |

Alle Policies laufen im Mode `validationFailureAction: Enforce` und mit
`background: true` (Reports auch für bereits existierende Ressourcen).

## Anpassungen im user-mgmt-Chart

Damit die neuen Policies den bestehenden `user-mgmt` Release nicht blockieren:

- `templates/backend-deployment.yaml` und `templates/frontend-deployment.yaml`
  bekommen `spec.securityContext.runAsNonRoot: true` +
  `seccompProfile.type: RuntimeDefault`, und Container-Level
  `allowPrivilegeEscalation: false` + `capabilities.drop: [ALL]`.
- Beide Images laufen ohnehin schon als `USER app` (siehe `docker/Dockerfile`
  und `frontend/Dockerfile`), also keine Runtime-Regression.

## Struktur

```
user-mgmt-ops/helm/policy/
├── Chart.yaml                   # depends on kyverno 3.3.5
├── values.yaml                  # controller sizing + resourceFilters (Ausnahmen für kube-system, argocd, ...)
├── templates/
│   ├── require-resource-limits.yaml
│   ├── disallow-latest-tag.yaml
│   ├── require-run-as-non-root.yaml
│   └── require-probes.yaml
├── samples/
│   └── bad-deployment.yaml      # verletzt alle vier Regeln, für Enforcement-Demo
└── README.md
```

## Bootstrap

```sh
cd user-mgmt-ops/helm/policy && helm dependency build
kubectl apply -f user-mgmt-ops/argocd/application-policy.yaml
```

## Enforcement-Nachweis

```sh
kubectl get clusterpolicy
# NAME                       BACKGROUND   VALIDATE ACTION   READY   AGE
# disallow-latest-tag        true         Enforce           True    1m
# require-probes             true         Enforce           True    1m
# require-resource-limits    true         Enforce           True    1m
# require-run-as-non-root    true         Enforce           True    1m

kubectl apply -f user-mgmt-ops/helm/policy/samples/bad-deployment.yaml -n default
# Error from server: error when creating "...":
# admission webhook "validate.kyverno.svc-fail" denied the request:
#
# resource Deployment/default/kyverno-demo-bad was blocked due to the following policies:
#
# disallow-latest-tag: ...
# require-resource-limits: ...
# require-probes: ...
# require-run-as-non-root: ...
```

`kubectl get policyreport,clusterpolicyreport -A` zeigt zusätzlich die
Background-Scan-Ergebnisse für bereits laufende Workloads.

## Roll-out neuer Policies

1. Neues YAML unter `helm/policy/templates/` committen → ArgoCD deployed automatisch.
2. Erst im Mode `Audit` starten, `PolicyReport` prüfen, dann auf `Enforce` heben.
