package tr.cabro.servicio.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import tr.cabro.servicio.Servicio;

public class DatabaseInitializer {

    public static void migrate() {
        try {
            Servicio.getInstance().getLogger().info("Migration öncesi yedek alınıyor...");
            DatabaseManager.backup();

            String dbPath = DatabaseManager.getConnection().getMetaData().getURL();

            Flyway flyway = Flyway.configure()
                    .dataSource(dbPath, "", "")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();

            MigrateResult result = flyway.migrate();
            Servicio.getInstance().getLogger().info(
                    String.format("Database migrated. From %s to %s (Applied: %d)",
                            result.initialSchemaVersion,
                            result.targetSchemaVersion,
                            result.migrationsExecuted
                    )
            );

        } catch (Exception e) {
            Servicio.getInstance().getLogger().severe("Migration error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
