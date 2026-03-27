package tr.cabro.servicio.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlite3.SQLitePlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.argument.LocalDateTimeArgumentFactory;
import tr.cabro.servicio.database.mapper.SQLiteDateMapper;
import tr.cabro.servicio.database.mapper.SQLiteDateTimeMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DatabaseManager {

    private static HikariDataSource dataSource;
    private static final String DB_FILE_NAME = "database.db";
    private static Jdbi jdbi;

    // Veritabanı şifresini oturum boyunca RAM'de güvenle tutmak için
    private static String currentDbKey = null;

    // --- BAŞLATMA VE AYARLAR (Config + Initializer Birleşimi) ---

    /**
     * Veritabanını verilen şifre (dbKey) ile açmayı dener.
     * @return Şifre doğruysa ve DB açıldıysa true, aksi halde false.
     */
    public static boolean initialize(String dbKey) {
        try {
            currentDbKey = dbKey; // Olası kopmalarda tekrar bağlanmak için hafızaya al

            // 1. Klasör kontrolü
            File dbFolder = new File(Servicio.getInstance().getDataFolder(), "database");
            if (!dbFolder.exists()) dbFolder.mkdirs();

            File dbFile = new File(dbFolder, DB_FILE_NAME);
            boolean isFirstRun = !dbFile.exists();
            String dbPath = dbFile.getAbsolutePath();

            // 2. HikariCP Ayarları (SQLite Odaklı)
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbPath);

            // --- ŞİFRELEME (SQLCipher) AYARI ---
            if (dbKey != null && !dbKey.isEmpty()) {
                config.addDataSourceProperty("pragma.key", dbKey);
            }

            config.setPoolName("Servicio-SQLite-Pool");
            config.setMaximumPoolSize(1); // SQLite için tek bağlantı
            config.setConnectionTimeout(30000);
            config.setLeakDetectionThreshold(2000);

            // Performans Ayarları (WAL Modu)
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("foreign_keys", "true");
            config.addDataSourceProperty("busy_timeout", "30000");

            dataSource = new HikariDataSource(config);

            // --- 3. ŞİFREYİ TEST ET ---
            // Eğer şifre yanlışsa,getConnection() anında SQLException fırlatır!
            try (Connection testConn = dataSource.getConnection()) {
                Servicio.getLogger().info("Veritabanı şifresi doğrulandı ve kilidi açıldı.");
            }

            jdbi = Jdbi.create(dataSource);

            // Gerekli eklentileri yükle
            jdbi.installPlugin(new SqlObjectPlugin());
            jdbi.installPlugin(new SQLitePlugin());
            jdbi.registerArgument(new LocalDateTimeArgumentFactory());
            jdbi.registerColumnMapper(LocalDateTime.class, new SQLiteDateTimeMapper());
            jdbi.registerColumnMapper(LocalDate.class, new SQLiteDateMapper());

            // 4. Migration (Flyway) ve İlk Yedek
            if (!isFirstRun) {
                Servicio.getLogger().info("Migration öncesi güvenlik yedeği alınıyor...");
                backup("pre-migrate");
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

            return true; // Her şey başarılı, giriş yapılabilir

        } catch (Exception e) {
            Servicio.getLogger().warn("Veritabanı açılamadı veya şifre yanlış: {}", e.getMessage());
            // Şifre yanlışsa veya dosya bozuksa havuzu kapatıp kilitlenmeyi önlüyoruz
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            return false; // Giriş başarısız
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            // Eğer bağlantı koptuysa, hafızadaki şifreyle yeniden bağlan
            if (currentDbKey != null) {
                initialize(currentDbKey);
            } else {
                throw new SQLException("Güvenlik İhlali: Veritabanı henüz bir şifre ile başlatılmadı!");
            }
        }
        return dataSource.getConnection();
    }

    public static Jdbi getJdbi() {
        if (jdbi == null) {
            if (currentDbKey != null) {
                initialize(currentDbKey);
            } else {
                throw new RuntimeException("Güvenlik İhlali: JDBI şifresiz başlatılamaz!");
            }
        }
        return jdbi;
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

            String targetPath = new File(backupDir, fileName).getAbsolutePath().replace("\\", "/");

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                File existingFile = new File(targetPath);
                if (existingFile.exists()) existingFile.delete();

                // Canlı yedek alma komutu (Şifreli DB'yi şifreli olarak yedekler)
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

            // 3. WAL artıklarını temizle
            new File(dbFile.getAbsolutePath() + "-wal").delete();
            new File(dbFile.getAbsolutePath() + "-shm").delete();

            // 4. Sistemi hafızadaki aktif şifreyle tekrar ayağa kaldır
            initialize(currentDbKey);
            Servicio.getLogger().info("Geri yükleme başarılı: {}", backupFile.getName());

        } catch (Exception e) {
            Servicio.getLogger().error("Geri yükleme kritik hata: {}", e.getMessage());
            if (currentDbKey != null) initialize(currentDbKey);
        }
    }
}