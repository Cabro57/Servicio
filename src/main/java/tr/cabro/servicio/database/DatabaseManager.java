package tr.cabro.servicio.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlite3.SQLitePlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.argument.LocalDateTimeArgumentFactory;
import tr.cabro.servicio.database.mapper.SQLiteBigDecimalMapper;
import tr.cabro.servicio.database.mapper.SQLiteDateMapper;
import tr.cabro.servicio.database.mapper.SQLiteDateTimeMapper;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;

public class DatabaseManager {

    private static HikariDataSource dataSource;
    private static final String DB_FILE_NAME = "database.db";
    private static Jdbi jdbi;
    private static volatile boolean restoring;
    private static volatile String latestSchemaVersion;

    // --- BAŞLATMA VE AYARLAR (Config + Initializer Birleşimi) ---

    public static void initialize() {
        try {
            // 1. Klasör kontrolü
            File dbFile = databaseFile();
            File dbFolder = dbFile.getParentFile();
            if (!dbFolder.exists()) dbFolder.mkdirs();

            boolean isFirstRun = !dbFile.exists();
            String dbPath = dbFile.getAbsolutePath();

            // 2. HikariCP Ayarları (SQLite Odaklı)
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbPath);

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

            // Jdbi bir kez kurulur ve bağlantıyı her seferinde o anki havuzdan ister. Geri yüklemede havuz
            // yenilense de servislerin ve ekranların elindeki repository'ler kapanmış havuza bağlı kalmaz.
            if (jdbi == null) {
                jdbi = Jdbi.create(() -> getConnection());

                // Gerekli eklentileri yükle
                jdbi.installPlugin(new SqlObjectPlugin());
                jdbi.installPlugin(new SQLitePlugin());
                jdbi.registerArgument(new LocalDateTimeArgumentFactory());
                jdbi.registerColumnMapper(LocalDateTime.class, new SQLiteDateTimeMapper());
                jdbi.registerColumnMapper(LocalDate.class, new SQLiteDateMapper());
                jdbi.registerColumnMapper(BigDecimal.class, new SQLiteBigDecimalMapper());
            }

            // 4. Migration (Flyway) ve migration öncesi yedek
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();

            MigrationInfoService info = flyway.info();
            latestSchemaVersion = Arrays.stream(info.all())
                    .filter(m -> m.getState().isResolved() && m.getVersion() != null)
                    .map(MigrationInfo::getVersion)
                    .max(Comparator.naturalOrder())
                    .map(MigrationVersion::getVersion)
                    .orElse(null);

            // Yalnızca gerçekten bekleyen migration varsa; her açılışta alınıp bir öncekini ezmesin.
            if (!isFirstRun && info.pending().length > 0) {
                Servicio.getLogger().info("Migration öncesi güvenlik yedeği alınıyor...");
                BackupManager.createQuietly(BackupManager.Kind.PRE_MIGRATE);
            }

            MigrateResult result = flyway.migrate();

            if (result.migrationsExecuted > 0) {
                Servicio.getLogger().info("Migration tamamlandı. Versiyon: {} -> {}",
                        result.initialSchemaVersion, result.targetSchemaVersion);
            }

        } catch (Exception e) {
            Servicio.getLogger().error("Veritabanı başlatma hatası", e);
            throw new RuntimeException(e);
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (restoring) throw new SQLException("Yedek geri yükleniyor; veritabanı geçici olarak kapalı.");
        if (dataSource == null || dataSource.isClosed()) initialize();
        return dataSource.getConnection();
    }

    /** Bağlantı havuzu durumu: {aktif, boşta, toplam}; havuz yoksa sıfırlar (Kaynak Kullanımı sayfası). */
    public static int[] poolStats() {
        try {
            if (dataSource != null && dataSource.getHikariPoolMXBean() != null) {
                var pool = dataSource.getHikariPoolMXBean();
                return new int[]{pool.getActiveConnections(), pool.getIdleConnections(), pool.getTotalConnections()};
            }
        } catch (Exception ignored) {
            // Havuz kapanırken okunamayabilir; sayfa "—" gösterir.
        }
        return new int[]{0, 0, 0};
    }

    public static Jdbi getJdbi() {
        if (restoring) throw new IllegalStateException("Yedek geri yükleniyor; veritabanı geçici olarak kapalı.");
        if (jdbi == null) initialize();
        return jdbi;
    }

    /**
     * Birden fazla repository'nin tek atomik işlemde yazması gerektiğinde kullanılır
     * (örn. POS satışı: sales + sale_items + stock_movements + payments).
     * <p>
     * {@code jdbi.onDemand(...)} ile alınan repository'ler HER ÇAĞRIDA kendi handle'ını
     * açar ve ortak transaction'a GİRMEZ. Bu yüzden transaction içinde kullanılacak
     * repository'ler {@code handle.attach(XRepository.class)} ile alınmalıdır:
     * <pre>{@code
     * DatabaseManager.inTransaction(handle -> {
     *     SaleRepository saleRepo = handle.attach(SaleRepository.class);
     *     ...
     *     return sale;
     * });
     * }</pre>
     */
    public static <R, X extends Exception> R inTransaction(HandleCallback<R, X> callback) throws X {
        return getJdbi().inTransaction(callback);
    }

    /** {@link #inTransaction} ile aynı, dönüş değeri olmayan işlemler için. */
    public static <X extends Exception> void useTransaction(HandleConsumer<X> consumer) throws X {
        getJdbi().useTransaction(consumer);
    }

    // --- GERİ YÜKLEME DESTEĞİ (bkz. BackupManager) ---

    /** Canlı veritabanı dosyası. */
    public static File databaseFile() {
        return new File(new File(Servicio.getInstance().getDataFolder(), "database"), DB_FILE_NAME);
    }

    /** Uygulamanın bildiği en yeni şema sürümü (ör. "28"); daha yeni sürümün yedeğini reddetmek için. */
    public static String getLatestSchemaVersion() {
        return latestSchemaVersion;
    }

    /**
     * Havuzu kapatır ve geri yükleme bitene kadar yeniden açılmasını engeller. Bu sırada bağlantı
     * isteyen arka plan işi, kapalı havuzu sessizce eski dosyayla yeniden açmak yerine hata alır.
     */
    static void beginRestore() {
        restoring = true;
        shutdown();
    }

    static void endRestore() {
        restoring = false;
    }
}
