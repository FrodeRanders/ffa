package se.fk.mimer.pipeline.transform.json;

import se.fk.mimer.migration.MigrationEngine;
import se.fk.mimer.migration.MimerMigrations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JsonMigrator {
    private final ObjectMapper mapper;

    public JsonMigrator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Result migrateIfNeeded(Path rawJson, Path outDir) throws IOException {
        JsonNode root = mapper.readTree(rawJson.toFile());
        int before = MigrationEngine.readSchemaVersion(root);
        if (before >= MimerMigrations.CURRENT) {
            return new Result(rawJson, before, before, List.of());
        }

        MigrationEngine engine = new MigrationEngine();
        MigrationEngine.Result migrated = engine.applyUpToCurrent(
                root,
                MimerMigrations.all(),
                MimerMigrations.CURRENT
        );

        Files.createDirectories(outDir);
        Path out = outDir.resolve("migrated.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), migrated.root);

        int after = MigrationEngine.readSchemaVersion(migrated.root);
        return new Result(out, before, after, migrated.audit);
    }

    public record Result(Path path, int beforeVersion, int afterVersion, List<MigrationEngine.AuditEntry> audit) {}
}
