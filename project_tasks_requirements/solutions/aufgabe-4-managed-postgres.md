# Aufgabe 4 – Managed PostgreSQL (DigitalOcean)

Die im Cluster betriebene Postgres wird durch eine DigitalOcean Managed
Database ersetzt. Provisioniert über Terraform, konsumiert vom
`user_mgmt_service` ausschliesslich über ein Kubernetes-Secret.

## Akzeptanzkriterien → Umsetzung

| # | Kriterium | Erfüllt durch |
|---|-----------|--------------|
| 1 | In-Cluster Postgres → DO Managed PostgreSQL | `terraform/database.tf` (`digitalocean_database_cluster.postgres`, Version 16, `db-s-1vcpu-1gb`), zusätzlich `digitalocean_database_db` + `digitalocean_database_user` |
| 2 | Backend verbindet ausschliesslich über bereitgestellte Verbindungsdaten | `templates/backend-deployment.yaml`: `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` kommen aus dem neuen Secret `<release>-backend-db` |
| 3 | Credentials im K8s Secret, nicht hardcodiert | `templates/backend-db-secret.yaml` erzeugt das Secret; Values sind Platzhalter in `values.yaml`; Real-Values kommen per `--set-string` aus `terraform output` (Anleitung im `terraform/README.md`) |
| 4 | Bisherige Postgres-Ressourcen aus Deployment entfernt | Templates `postgres-statefulset.yaml`, `postgres-service.yaml`, `postgres-secret.yaml` gelöscht; Postgres-Helpers aus `_helpers.tpl` raus; `pdb.yaml` bereinigt; `postgres:`-Block aus allen `values*.yaml` entfernt |
| 5 | Managed DB via Terraform (DO Provider) provisioniert | `terraform/database.tf` inkl. `digitalocean_database_firewall.postgres` (Zugriff auf DOKS beschränkt) |

## Änderungen im Detail

### `user-mgmt-ops/terraform/`

- **`database.tf`** – neu:
  - `digitalocean_database_cluster.postgres` (Postgres 16, `db-s-1vcpu-1gb`, 1 Node, Maintenance-Window aus Var).
  - `digitalocean_database_db.user_mgmt` (Datenbank-Name aus `var.postgres.database`).
  - `digitalocean_database_user.user_mgmt` (App-User, DO generiert Password).
  - `digitalocean_database_firewall.postgres` – erlaubt nur den DOKS-Cluster plus optional operator-CIDRs.
- **`variables.tf`** – Object-Variable `postgres` (name, version, size, node_count, database, username, maintenance, allowed_ip_cidrs).
- **`outputs.tf`** – neue Outputs: `postgres_host`, `postgres_port`, `postgres_database`, `postgres_username`, `postgres_password` (sensitive), `postgres_jdbc_url` (sensitive).
- **`terraform.tfvars.example`** – Beispiel-Block für `postgres`.
- **`README.md`** – Abschnitt „Managed PostgreSQL (Aufgabe 4)" mit `helm upgrade --set-string`-Beispiel aus `terraform output`.

### `user-mgmt-ops/helm/user-mgmt/`

- **Gelöscht**: `templates/postgres-statefulset.yaml`, `templates/postgres-service.yaml`, `templates/postgres-secret.yaml`.
- **`templates/_helpers.tpl`** – `user-mgmt.postgres.*`-Helpers entfernt; neu `user-mgmt.backend.dbSecretName` und `user-mgmt.backend.jdbcUrl` (SSL-Mode aus Values).
- **`templates/backend-db-secret.yaml`** – neu: Opaque Secret mit `username`, `password`, `jdbc-url`.
- **`templates/backend-deployment.yaml`** – DB-Env-Vars zeigen jetzt alle auf `<release>-backend-db` (Keys `username`, `password`, `jdbc-url`).
- **`templates/backend-configmap.yaml`** – `spring-datasource-url` entfernt (jetzt im Secret).
- **`templates/pdb.yaml`** – Postgres-PDB-Sektion entfernt.
- **`templates/NOTES.txt`** – Component-Eintrag umgestellt von StatefulSet auf „DigitalOcean Managed PostgreSQL".
- **`values.yaml`** – kompletter `postgres:`-Block entfernt; neuer Block `backend.database` (host/port/name/sslMode/username/password – Platzhalter).
- **`values-prod.yaml`**, **`values-staging.yaml`** – jeweilige `postgres:`-Overrides entfernt.

## Deploy-Flow

```sh
cd user-mgmt-ops/terraform
export TF_VAR_do_token='dop_v1_...'
terraform apply                            # provisioniert Cluster (Import) + Managed DB

# Anschließend Helm-Deploy mit Credentials aus TF-Outputs
helm upgrade --install user-mgmt-prod ../helm/user-mgmt \
  --namespace user-mgmt-prod \
  -f ../helm/user-mgmt/values.yaml \
  -f ../helm/user-mgmt/values-prod.yaml \
  --set-string backend.database.host=$(terraform output -raw postgres_host) \
  --set-string backend.database.port=$(terraform output -raw postgres_port) \
  --set-string backend.database.name=$(terraform output -raw postgres_database) \
  --set-string backend.database.username=$(terraform output -raw postgres_username) \
  --set-string backend.database.password=$(terraform output -raw postgres_password)
```

Für ArgoCD-verwaltete Deploys: Secret via `external-secrets`/`sealed-secrets`
aus TF-Outputs materialisieren – `values.yaml`-Platzhalter bleiben unangetastet.

## Sanity Checks

- `helm lint . -f values.yaml -f values-prod.yaml` → OK.
- `helm template` produziert keinen StatefulSet/PVC mehr, aber ein
  `Secret <release>-backend-db` mit `stringData.jdbc-url` inkl.
  `?sslmode=require`.
- Backend-Deployment liest alle drei `SPRING_DATASOURCE_*` Env-Vars aus dem
  gleichen Secret.

## Offene Punkte (nach Deploy vom Betriebsteam zu prüfen)

- Ersten Migrationslauf (`spring.jpa.hibernate.ddl-auto=update`) auf leere DB
  ausführen; danach empfohlen: `ddl-auto=validate` in Prod.
- Falls Operator-Workstations Zugriff brauchen (psql-Debug), `postgres.allowed_ip_cidrs`
  in `terraform.tfvars` erweitern.
