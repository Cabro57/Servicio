package tr.cabro.servicio.database;

import lombok.Getter;
import tr.cabro.servicio.Servicio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    /** Veritabanını güvenli şekilde (canlı) yedekler */
    public static void backup() {
        try {
            // Backup dizinini oluştur (yoksa)
            String backupDir = Servicio.getSettings().getBackup().getPath();
            Files.createDirectories(new File(backupDir).toPath());

            // Yedek dosya adı
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd-HHmmss"));
            String backupFile = backupDir + File.separator + timestamp + ".db";

            // VACUUM INTO ile canlı yedek al
            try (java.sql.Statement stmt = connection.createStatement()) {
                // SQLite için path'ler forward slash (/) olmalı
                stmt.execute("VACUUM INTO '" + backupFile.replace("\\", "/") + "'");
            }

            Servicio.getInstance().getLogger().info("SQLite yedeği oluşturuldu: " + backupFile);

        } catch (Exception e) {
            Servicio.getInstance().getLogger().severe("SQLite yedekleme hatası: " + e.getMessage());
        }
    }

    public static void restoreBackup(File backupFile) {
        try {
            if (!backupFile.exists()) {
                throw new IllegalArgumentException("Yedek dosyası bulunamadı: " + backupFile.getAbsolutePath());
            }

            // Aktif DB dosyası
            String dbUrl = connection.getMetaData().getURL().replace("jdbc:sqlite:", "");
            File dbFile = new File(dbUrl);

            // Bağlantıyı kapat
            disconnect();

            // Yedeği geri kopyala
            Files.copy(backupFile.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Bağlantıyı tekrar aç
            connect(DatabaseType.SQLite);

            Servicio.getInstance().getLogger().info("Veritabanı geri yüklendi: " + backupFile.getName());
        } catch (Exception e) {
            Servicio.getInstance().getLogger().severe("Geri yükleme hatası: " + e.getMessage());
            throw new RuntimeException("Geri yükleme başarısız: " + e.getMessage(), e);
        }
    }
}
