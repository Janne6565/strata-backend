<!-- AUTO-SYNCED from agents KB: projects/strata.md @ 24e07ab.
     Do NOT edit here — edit the source in ~/projects/agents and re-run scripts/sync-conventions.sh. -->

# Strata

Multi-tenant, in-cluster Kubernetes database browser — auto-discovers databases running in the cluster and lets granted users browse/query them without kubeconfig or port-forwarding.

- **Live:** https://strata.jannekeipert.de
- **Repos:** github.com/janne6565/{strata-backend, strata-frontend, strata-deployment}
- **Local:** clone the repo(s) listed above into `~/projects/strata/` — single-repo → directly into `~/projects/strata/`, multi-repo → one subfolder per repo (`~/projects/strata/<repo-name>/`). Always `git pull` before reading. See [repo conventions](README.md#local-repos--clone-on-demand-pull-before-reading).
- **Cluster:** namespace `strata`

## Idea
Strata runs INSIDE a Kubernetes cluster, auto-discovers the databases running there, and lets authenticated users browse and query only the ones they've been granted — no kubeconfig, connection strings, or port-forwarding required. Supports PostgreSQL, MySQL, MongoDB, Redis, InfluxDB, and Loki. RBAC provides namespace- and database-level grants, with a global safe-mode that forces read-only on production namespaces.

## Stack
- **Frontend:** React 19, TypeScript, Vite 8, Bun. Redux Toolkit, TanStack Router, TanStack Query, axios, orval-generated API client, Tailwind (v4), shadcn/ui (via shadcn CLI + Radix), lucide-react, react-i18next. Vitest + Testing Library, ESLint, Prettier.
- **Backend:** Spring Boot 4.1.0 / Java 25, Maven. Spring Security, Spring Data JPA, JWT (jjwt), Flyway, springdoc OpenAPI. **fabric8 kubernetes-client** for in-cluster discovery. Engine adapters for PostgreSQL, MySQL, MongoDB, Redis, InfluxDB, Loki. Testcontainers (incl. k3s) for tests.
- **Infra / Deploy:** Docker, nginx, Kubernetes, Kustomize, ArgoCD (auto-sync/self-heal/prune), cert-manager (Let's Encrypt), GitHub Actions CI (build/test, push image to GHCR).

## Notable (stands out vs other projects)
- **fabric8 kubernetes-client auto-discovery** — the defining feature. `DiscoveryService` + `KubernetesScanner` walk the cluster and resolve credentials from Secrets/ConfigMaps referenced by workload env (`CredentialReader`/`CredentialResolver`). Config at `configuration/kubernetes/KubernetesClientConfig.java`.
- **Dedicated RBAC service account** (`strata-backend`) with a ClusterRole granting read-only cluster access including `get/list` on Secrets for credential resolution — see deployment files below.
- **Global "safe-mode" for production namespaces** — `KubernetesProperties` fields `prodNamespacePatterns` + `readOnlySafeMode`; enforced in `GrantEvaluator.prodSafeModeBlocks()` so writes are blocked on matching namespaces.
- Six database engines behind a single query UI, resolved by driver string in `EngineRegistry` (Loki speaks the HTTP API, others JDBC or native drivers).
- Audit logging (`AuditService` / `AuditLogEntity`) and user management built in.
- Deployment uses a **single `main` overlay** — no prod/staging split; everything lives in the `strata` namespace.

## Notes for agents
- **Multi-tenant RBAC:** grants live in `AccessGrantEntity` (has `scopeType` NAMESPACE-or-datasource, a `namespace` field and a `datasource` FK, plus `readOnly`). Access decisions go through `services/core/GrantService.java` + `GrantEvaluator.java` (admins see all; most-restrictive-wins). Policy layer under `security/authorization/`.
- **Deployment RBAC files** (all under `strata-deployment/base/backend/`): `rbac.yaml` (ClusterRole/Binding `strata-backend-discovery`), `serviceaccount.yaml` (SA `strata-backend`), `discovery-config.yaml` (ConfigMap of image-ref→driver detectors, mounted as extra Spring config).
- Detector configmap (`discovery-config.yaml`) currently defines detectors for postgres, redis, influxdb, cockroachdb, and loki — MySQL and MongoDB engines exist in code but have no detector entry there yet.
- **Drag-and-drop datasource grouping** is hand-rolled native HTML5 DnD (`src/pages/groups/GroupBoard.tsx` + `GroupZone.tsx`, state in `store/groupsSlice.ts`) — no dnd-kit/react-dnd library.
- **API client is orval-generated** (`orval.config.ts`); don't hand-edit generated files.
- Versions are cutting-edge (Spring Boot 4.1, Java 25, springdoc 3.x, React 19, Vite 8) — check `pom.xml`/`package.json` before assuming older APIs.
- Delivery is Kustomize + ArgoCD only — no Helm charts anywhere.
