package tr.cabro.servicio.database;

import tr.cabro.servicio.Servicio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DatabaseManager {

    public static Connection getConnection() throws SQLException {
        return DatabaseConfig.getDataSource().getConnection();
    }

    /** Fiziksel backup alma */
    public static void backup() {
        try {
            String backupDir = Servicio.getSettings().getBackup().getPath();
            Files.createDirectories(new File(backupDir).toPath());

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
            String backupFile = backupDir + File.separator + timestamp;

            switch (DatabaseConfig.getDbType()) {
                case SQLite:
                    backupFile += ".db";
                    try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                        stmt.execute("VACUUM INTO '" + backupFile.replace("\\", "/") + "'");
                    }
                    break;

                case MySQL:
                    backupFile += ".sql";
                    runProcess("mysqldump -uuser -ppass servicio -r " + backupFile);
                    break;
            }

            Servicio.getLogger().info("Yedek alındı: {}", backupFile);
        } catch (Exception e) {
            Servicio.getLogger().error("Backup hatası: {}", e.getMessage());
        }
    }

    /** Backup geri yükleme */
    public static void restoreBackup(File backupFile) {
        try {
            if (!backupFile.exists()) {
                throw new IllegalArgumentException("Yedek dosyası bulunamadı: " + backupFile.getAbsolutePath());
            }

            switch (DatabaseConfig.getDbType()) {
                case SQLite:
                    String dbPath = Servicio.getInstance().getDataFolder().getAbsolutePath() + "/database/database.db";
                    DatabaseConfig.close();
                    Files.copy(backupFile.toPath(), new File(dbPath).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    DatabaseConfig.init(DatabaseConfig.getDbType());
                    break;

                case MySQL:
                    runProcess("mysql -uuser -ppass servicio < " + backupFile.getAbsolutePath());
                    break;
            }

            Servicio.getLogger().info("Yedek geri yüklendi: {}", backupFile.getName());
        } catch (Exception e) {
            Servicio.getLogger().error("Restore hatası: {}", e.getMessage());
            throw new RuntimeException("Restore başarısız: " + e.getMessage(), e);
        }
    }

    private static void runProcess(String command) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[] { "bash", "-c", command });
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Komut başarısız: " + command);
        }
    }
}
