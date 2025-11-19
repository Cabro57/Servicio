package tr.cabro.servicio.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import tr.cabro.servicio.Servicio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DatabaseManager {

    private static HikariDataSource dataSource;
    private static final String DB_FILE_NAME = "database.db";

    // --- BAŞLATMA VE AYARLAR (Config + Initializer Birleşimi) ---

    public static void initialize() {
        // 1. Klasör kontrolü
        File dbFolder = new File(Servicio.getInstance().getDataFolder(), "database");
        if (!dbFolder.exists()) dbFolder.mkdirs();

        File dbFile = new File(dbFolder, DB_FILE_NAME);
        boolean isFirstRun = !dbFile.exists();
        String dbPath = dbFile.getAbsolutePath();

        // 2. HikariCP Ayarları (SQLite Odaklı)
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setPoolName("Servicio-SQLite-Pool");
        config.setMaximumPoolSize(5); // SQLite için tek bağlantı genellikle en sağlıklısıdır

        config.setConnectionTimeout(30000);

        config.setLeakDetectionThreshold(2000);

        // Performans Ayarları (WAL Modu)
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("foreign_keys", "true");

        config.addDataSourceProperty("busy_timeout", "30000");

        dataSource = new HikariDataSource(config);

        // 3. Migration (Flyway) ve İlk Yedek
        try {
            // Eğer veritabanı önceden varsa (ilk çalışma değilse), migration öncesi güvenlik yedeği al
            if (!isFirstRun) {
                Servicio.getLogger().info("Migration öncesi güvenlik yedeği alınıyor...");
                backup("pre-migrate-" + System.currentTimeMillis());
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();

            MigrateResult result = flyway.migrate();

            if (result.migrationsExecuted > 0) {
                Servicio.getLogger().info("Migration tamamlandı. Versiyon: {} -> {}",
                        result.initialSchemaVersion, result.targetSchemaVersion);
            }

        } catch (Exception e) {
            Servicio.getLogger().error("Veritabanı başlatma hatası: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) initialize();
        return dataSource.getConnection();
    }

    // --- YEDEKLEME VE GERİ YÜKLEME İŞLEMLERİ ---

    public static void backup(String fileName) {
        try {
            String backupDir = Servicio.getSettings().getBackup().getPath();
            new File(backupDir).mkdirs();

            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
            }
            if (!fileName.endsWith(".db")) fileName += ".db";

            // Windows uyumluluğu için ters slaş düzeltmesi
            String targetPath = new File(backupDir, fileName).getAbsolutePath().replace("\\", "/");

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                // Eğer aynı isimde dosya varsa önce onu siliyoruz ki VACUUM hata vermesin
                File existingFile = new File(targetPath);
                if (existingFile.exists()) existingFile.delete();

                // Canlı yedek alma komutu
                stmt.execute("VACUUM INTO '" + targetPath + "'");
            }
            Servicio.getLogger().info("Yedek alındı: {}", fileName);

        } catch (Exception e) {
            Servicio.getLogger().error("Yedekleme başarısız: {}", e.getMessage());
        }
    }

    public static void backup() {
        backup(null);
    }

    public static void restore(File backupFile) {
        if (!backupFile.exists()) return;

        Servicio.getLogger().warn("Geri yükleme başlatılıyor. Veritabanı kapatılıyor...");

        try {
            // 1. Havuzu kapat (Dosya kilidini kaldır)
            shutdown();

            // 2. Dosyayı kopyala
            File dbFile = new File(Servicio.getInstance().getDataFolder() + "/database", DB_FILE_NAME);
            Files.copy(backupFile.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 3. WAL artıklarını temizle (Database tutarlılığı için önemli)
            new File(dbFile.getAbsolutePath() + "-wal").delete();
            new File(dbFile.getAbsolutePath() + "-shm").delete();

            // 4. Sistemi tekrar ayağa kaldır
            initialize();
            Servicio.getLogger().info("Geri yükleme başarılı: {}", backupFile.getName());

        } catch (Exception e) {
            Servicio.getLogger().error("Geri yükleme kritik hata: {}", e.getMessage());
            // Hata olsa bile sistemi tekrar açmayı dene
            initialize();
        }
    }
}