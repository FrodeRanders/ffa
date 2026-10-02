-- JSON är opaque text. Signaturen och leveransmetadata är separata från dokumentet.
CREATE TABLE IF NOT EXISTS ffa_dataleverans (
    dataleverans_id uuid PRIMARY KEY,
    korrelations_id text NOT NULL CHECK (length(korrelations_id) > 0),
    objekt_id text NOT NULL,
    objekt_version bigint NOT NULL,
    forvantad_version bigint NOT NULL,
    topic text NOT NULL,
    skapad timestamptz NOT NULL,
    lagrad timestamptz NOT NULL DEFAULT clock_timestamp(),
    ordning bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    dokument text NOT NULL,
    signatur bytea NOT NULL,
    kafka_publicerad boolean NOT NULL DEFAULT false,
    kafka_publicerad_tid timestamptz,
    leveransforsok integer NOT NULL DEFAULT 0,
    senaste_fel text
);
CREATE INDEX IF NOT EXISTS ffa_process_senaste ON ffa_dataleverans(topic, korrelations_id, ordning DESC);
CREATE INDEX IF NOT EXISTS ffa_objekt_senaste ON ffa_dataleverans(topic, objekt_id, ordning DESC);
CREATE INDEX IF NOT EXISTS ffa_vantande ON ffa_dataleverans(topic, ordning) WHERE NOT kafka_publicerad;

-- Version först hindrar återställt historiskt backenddata från att skymma nyare lokal data.
CREATE INDEX IF NOT EXISTS ffa_process_version ON ffa_dataleverans(topic, korrelations_id, objekt_version DESC, ordning DESC);
CREATE INDEX IF NOT EXISTS ffa_objekt_version ON ffa_dataleverans(topic, objekt_id, objekt_version DESC, ordning DESC);
