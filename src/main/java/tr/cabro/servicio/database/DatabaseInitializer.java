package tr.cabro.servicio.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import tr.cabro.servicio.Servicio;

public class DatabaseInitializer {

    public static void migrate() {
        try {
            Servicio.getLogger().info("Migration öncesi yedek alınıyor...");
            DatabaseManager.backup("pre-migrate-backup");

            Flyway flyway = Flyway.configure()
                    .dataSource(DatabaseConfig.getDataSource())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();

            MigrateResult result = flyway.migrate();

            Servicio.getLogger().info("Migration tamamlandı. From {} to {} (Applied: {})",
                    result.initialSchemaVersion, result.targetSchemaVersion, result.migrationsExecuted);

        } catch (Exception e) {
            Servicio.getLogger().error("Migration error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
