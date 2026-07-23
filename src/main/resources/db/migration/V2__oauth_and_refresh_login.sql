-- Federated OIDC login (Phase B — "Login with Authentik").
-- Password login and OAuth login both resolve a local app_user and issue Strata's own JWTs;
-- OAuth-only users have no password, so relax the NOT NULL. Column types/names mirror V1.

-- OAuth-only accounts never set a password.
alter table app_user alter column password_hash drop not null;

-- Single-use CSRF state for the authorization-code flow (10-min TTL, evicted hourly).
create table oauth_state (
    state     varchar(64) primary key,
    issued_at timestamptz not null
);

-- Links an external provider identity to a local user. Email lives here, not on app_user.
create table oauth_identity (
    id               uuid         primary key,
    user_id          uuid         not null references app_user (id) on delete cascade,
    provider         varchar(32)  not null,
    provider_subject varchar(255) not null,
    email            varchar(255),
    created_at       timestamptz  not null default now(),
    constraint uq_oauth_identity unique (provider, provider_subject)
);
create index idx_oauth_identity_user on oauth_identity (user_id);
