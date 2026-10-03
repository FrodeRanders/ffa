-- Ett separat backendindex; förmånens lokala cache finns fortfarande i public.ffa_dataleverans.
CREATE SCHEMA IF NOT EXISTS ffa_backend;
CREATE TABLE IF NOT EXISTS ffa_backend.dataleverans (
    dataleverans_id uuid PRIMARY KEY,
    topic text NOT NULL,
    korrelations_id text NOT NULL,
    objekt_id text NOT NULL,
    objekt_version bigint NOT NULL,
    metadata text NOT NULL,
    dokument_sha256 bytea NOT NULL,
    lagrad timestamptz NOT NULL DEFAULT clock_timestamp(),
    ordning bigint GENERATED ALWAYS AS IDENTITY UNIQUE
);
CREATE INDEX IF NOT EXISTS ffa_backend_process ON ffa_backend.dataleverans
    (topic, korrelations_id, objekt_version DESC, ordning DESC);
CREATE INDEX IF NOT EXISTS ffa_backend_objekt ON ffa_backend.dataleverans
    (topic, objekt_id, objekt_version DESC, ordning DESC);
