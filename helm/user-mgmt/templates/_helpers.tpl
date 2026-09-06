{{/*
Chart name.
*/}}
{{- define "user-mgmt.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name — release + chart name.
*/}}
{{- define "user-mgmt.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart identifier (used in labels).
*/}}
{{- define "user-mgmt.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Base selector labels shared by all components.
*/}}
{{- define "user-mgmt.selectorLabels" -}}
app.kubernetes.io/name: {{ include "user-mgmt.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "user-mgmt.labels" -}}
helm.sh/chart: {{ include "user-mgmt.chart" . }}
{{ include "user-mgmt.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Per-component names.
*/}}
{{- define "user-mgmt.backend.name" -}}
{{- printf "%s-backend" (include "user-mgmt.fullname" .) }}
{{- end }}

{{- define "user-mgmt.frontend.name" -}}
{{- printf "%s-frontend" (include "user-mgmt.fullname" .) }}
{{- end }}

{{- define "user-mgmt.postgres.name" -}}
{{- printf "%s-postgres" (include "user-mgmt.fullname" .) }}
{{- end }}

{{/*
Per-component selector labels (extend base with component tag).
*/}}
{{- define "user-mgmt.backend.selectorLabels" -}}
{{ include "user-mgmt.selectorLabels" . }}
app.kubernetes.io/component: backend
{{- end }}

{{- define "user-mgmt.frontend.selectorLabels" -}}
{{ include "user-mgmt.selectorLabels" . }}
app.kubernetes.io/component: frontend
{{- end }}

{{- define "user-mgmt.postgres.selectorLabels" -}}
{{ include "user-mgmt.selectorLabels" . }}
app.kubernetes.io/component: postgres
{{- end }}

{{/*
Per-component full labels.
*/}}
{{- define "user-mgmt.backend.labels" -}}
{{ include "user-mgmt.labels" . }}
app.kubernetes.io/component: backend
{{- end }}

{{- define "user-mgmt.frontend.labels" -}}
{{ include "user-mgmt.labels" . }}
app.kubernetes.io/component: frontend
{{- end }}

{{- define "user-mgmt.postgres.labels" -}}
{{ include "user-mgmt.labels" . }}
app.kubernetes.io/component: postgres
{{- end }}

{{/*
JDBC URL for the backend to reach the Postgres Service.
*/}}
{{- define "user-mgmt.postgres.jdbcUrl" -}}
{{- printf "jdbc:postgresql://%s:%d/%s" (include "user-mgmt.postgres.name" .) (int .Values.postgres.service.port) .Values.postgres.db.name }}
{{- end }}
