package tr.cabro.servicio.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import tr.cabro.servicio.Servicio;

import javax.sql.DataSource;

public class DatabaseConfig {
    private static HikariDataSource dataSource;
    private static DatabaseType dbType;

    public static void init(DatabaseType type) {
        dbType = type;
        HikariConfig config = new HikariConfig();

        switch (type) {
            case MySQL:
                config.setJdbcUrl("jdbc:mysql://localhost:3306/servicio?useSSL=false&serverTimezone=UTC");
                config.setUsername("user");
                config.setPassword("pass");
                break;

            case SQLite:
                String dbPath = Servicio.getInstance().getDataFolder().getAbsolutePath() + "/database/database.db";
                config.setJdbcUrl("jdbc:sqlite:" + dbPath);
                break;
        }

        config.setMaximumPoolSize(10);
        config.setPoolName("Servicio-DB-Pool");

        dataSource = new HikariDataSource(config);
    }

    public static DataSource getDataSource() {
        return dataSource;
    }

    public static DatabaseType getDbType() {
        return dbType;
    }

    public static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
