# Aufgabe 3 – Terraform IaC (DOKS import)

Der bestehende DigitalOcean Kubernetes Cluster wird deklarativ mit Terraform
verwaltet – **ohne Recreate** – über `import`-Block und
`terraform plan -generate-config-out=generated.tf`.

## Akzeptanzkriterien → Umsetzung

| # | Kriterium | Erfüllt durch |
|---|-----------|--------------|
| 1 | DigitalOcean Provider konfiguriert | `versions.tf` (`digitalocean/digitalocean ~> 2.44`), `providers.tf` mit `token = var.do_token` |
| 2 | Bestehender Cluster via `import` + `-generate-config-out` | `imports.tf` (`import { to = digitalocean_kubernetes_cluster.this; id = var.cluster_id }`); Workflow im README dokumentiert |
| 3 | `generated.tf` analysiert & bereinigt | `main.tf` enthält das gesäuberte Resource-Body (computed-only Felder wie `id`, `endpoint`, `status`, `kube_config` entfernt; Kommentar dokumentiert Bereinigungsschritte) |
| 4 | Wiederverwendbare Werte über Variablen | `variables.tf`: `cluster_name`, `region`, `kubernetes_version`, `node_pool` (Object-Type), `cluster_tags`, `ha` |
| 5 | Sensible Werte nicht im Repo | `.gitignore` schließt `*.tfvars` und `generated.tf` aus; `do_token` als `sensitive` markiert; `TF_VAR_do_token` als bevorzugter Weg im README |
| 6 | `fmt`/`validate`/`plan` sauber, keine Drift | `main.tf` mirror't DOKS-Defaults explizit (`surge_upgrade = true`, `auto_upgrade = false`); Node-Pool-Autoscale-Felder werden konditional gesetzt |

## Struktur

```
user-mgmt-ops/terraform/
├── versions.tf                # required_version + provider constraint
├── providers.tf               # DigitalOcean provider (token via var)
├── variables.tf               # do_token (sensitive), cluster_id, region, …
├── imports.tf                 # import-Block
├── main.tf                    # bereinigte Cluster-Resource (Ziel-State)
├── outputs.tf                 # cluster_id, endpoint (sensitive), version
├── terraform.tfvars.example   # Vorlage (keine Secrets)
├── .gitignore                 # blockt *.tfvars, state, generated.tf
└── README.md                  # Workflow inkl. Cleanup-Schritte
```

## Workflow (aus README)

```sh
# 1. Credentials via Env-Var (nicht ins Repo!)
export TF_VAR_do_token='dop_v1_...'

# 2. Init
terraform init

# 3. Erster Import — erzeugt generated.tf
terraform plan -generate-config-out=generated.tf

# 4. generated.tf analysieren, relevante Felder in main.tf mergen,
#    computed-only Felder entfernen, hardcoded Werte durch var.* ersetzen,
#    danach: rm generated.tf

# 5. Verifikation
terraform fmt -check -recursive
terraform validate
terraform plan            # muss "No changes" melden

# 6. Apply (idempotent auf existierendem Cluster)
terraform apply
```

## Bereinigungsregeln (im main.tf-Header dokumentiert)

- Computed-only Felder entfernen: `id`, `created_at`, `updated_at`, `status`,
  `ipv4_address`, `endpoint`, `kube_config`, `urn`.
- Hardcodierte Werte → `var.*` verschieben.
- Provider-Defaults explizit halten (`surge_upgrade`, `auto_upgrade`) →
  stabile künftige Pläne.

## Nicht im Repo

- `terraform.tfvars` – enthält u. a. `do_token` + `cluster_id`.
- `.terraform/`, `terraform.tfstate*` – Local Backend Artefakte.
- `generated.tf` – Zwischen-Output aus `-generate-config-out`.

## Fortsetzung durch Aufgabe 4

Die Managed PostgreSQL kommt als `database.tf` daneben (`digitalocean_database_cluster.postgres`),
verwendet die gleiche `var.region` und den gleichen Provider — kein neuer
Backend/State nötig.
