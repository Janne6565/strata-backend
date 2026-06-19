-- Strata state schema baseline (see ARCHITECTURE.md §4).
-- Roles, grants, the datasource catalog (discovered + manual, with lifecycle),
-- per-user groups, and the audit log. Enum-like columns use CHECK constraints
-- and map to JPA @Enumerated(STRING).

create table app_user (
    id            uuid primary key,
    username      varchar(255) not null unique,
    password_hash varchar(255) not null,
    role          varchar(16)  not null check (role in ('OWNER', 'ADMIN', 'USER')),
    enabled       boolean      not null default true,
    created_at    timestamptz  not null default now()
);

-- Unified catalog of every database Strata knows about. `id` is the stable internal
-- identity (FK target); `discovery_key` is the natural key used to match scans to rows.
create table datasource (
    id                    uuid primary key,
    discovery_key         varchar(512) not null unique,
    origin                varchar(16)  not null check (origin in ('DISCOVERED', 'MANUAL')),
    status                varchar(16)  not null check (status in ('PRESENT', 'MISSING')),
    namespace             varchar(255) not null,
    workload_kind         varchar(64),
    workload_name         varchar(255),
    service_name          varchar(255),
    service_port          integer,
    driver                varchar(64)  not null,
    engine_version        varchar(128),
    display_name          varchar(255) not null,
    read_only_capability  boolean      not null default false,
    detection_confidence  varchar(16),
    detected_via          varchar(255),
    credential_resolution jsonb,
    manual_overrides      jsonb,
    created_by            uuid references app_user (id),
    first_seen_at         timestamptz  not null default now(),
    last_seen_at          timestamptz  not null default now(),
    created_at            timestamptz  not null default now()
);
create index idx_datasource_namespace on datasource (namespace);
create index idx_datasource_status on datasource (status);

-- A grant targets either a whole namespace or a single datasource, with a read-only flag.
create table access_grant (
    id            uuid primary key,
    user_id       uuid        not null references app_user (id) on delete cascade,
    scope_type    varchar(16) not null check (scope_type in ('NAMESPACE', 'DATABASE')),
    namespace     varchar(255),
    datasource_id uuid references datasource (id) on delete cascade,
    read_only     boolean     not null default false,
    created_by    uuid references app_user (id),
    created_at    timestamptz not null default now(),
    constraint chk_grant_scope check (
        (scope_type = 'NAMESPACE' and namespace is not null and datasource_id is null)
        or (scope_type = 'DATABASE' and datasource_id is not null)
    )
);
create index idx_access_grant_user on access_grant (user_id);

-- Per-user logical groupings (labels only — do not touch the cluster).
create table db_group (
    id            uuid primary key,
    owner_user_id uuid         not null references app_user (id) on delete cascade,
    name          varchar(255) not null,
    position      integer      not null default 0,
    created_at    timestamptz  not null default now()
);
create index idx_db_group_owner on db_group (owner_user_id);

create table db_group_member (
    group_id      uuid not null references db_group (id) on delete cascade,
    datasource_id uuid not null references datasource (id) on delete cascade,
    primary key (group_id, datasource_id)
);

-- Audit trail: who did what, to which resource, with what outcome. Never stores secrets.
create table audit_log (
    id            uuid primary key,
    user_id       uuid references app_user (id),
    operation     varchar(64) not null,
    target_ref    varchar(512),
    namespace     varchar(255),
    outcome       varchar(16) not null,
    query_summary text,
    at            timestamptz not null default now()
);
create index idx_audit_log_user on audit_log (user_id);
create index idx_audit_log_at on audit_log (at);
