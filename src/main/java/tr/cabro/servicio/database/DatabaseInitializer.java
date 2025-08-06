package tr.cabro.servicio.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import tr.cabro.servicio.Servicio;

public class DatabaseInitializer {

    public static void migrate() {
        try {
            Servicio.getLogger().info("Migration öncesi yedek alınıyor...");
            DatabaseManager.backup();

            String dbPath = DatabaseManager.getConnection().getMetaData().getURL();

            Flyway flyway = Flyway.configure()
                    .dataSource(dbPath, "", "")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();

            MigrateResult result = flyway.migrate();
            Servicio.getLogger().info("Database migrated. From {} to {} (Applied: {})", result.initialSchemaVersion, result.targetSchemaVersion, result.migrationsExecuted);

        } catch (Exception e) {
            Servicio.getLogger().error("Migration error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
