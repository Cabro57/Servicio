package tr.cabro.servicio.database;

import lombok.Getter;
import tr.cabro.servicio.Servicio;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {
    @Getter
    private static Connection connection;

    public static void connect(DatabaseType dbType) throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        switch (dbType) {
            case MySQL:
                connection = DriverManager.getConnection("jdbc:mysql://localhost/db", "user", "pass");
                break;
            case SQLite:
                File dbFile = new File(Servicio.getInstance().getDataFolder(), "database/database.db");

                // Klasörü yoksa oluştur
                File dbDir = dbFile.getParentFile();
                if (!dbDir.exists() && dbDir.mkdirs()) {
                    Servicio.getInstance().getLogger().info("Veritabanı klasörü oluşturuldu: " + dbDir.getAbsolutePath());
                }

                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                break;
            default:
                throw new IllegalArgumentException("Unsupported DB");
        }
    }

    public static void disconnect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
