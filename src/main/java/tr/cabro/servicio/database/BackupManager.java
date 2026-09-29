package tr.cabro.servicio.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.util.CryptoUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Yedek alma, yedekleri listeleme, eski otomatik yedekleri temizleme ve güvenli geri yükleme.
 * <p>
 * Yedek biçimi: {@code servicio-yyyyMMdd-HHmmss-<tür>-v<sürüm>.zip} (sürüm, yedeği alan uygulamanın
 * sürümü; güncelleme öncesi yedekte "bu sürüme güncellenmeden önce" anlamına gelir). İçinde canlı veritabanının
 * {@code VACUUM INTO} kopyası ({@code database.db}), cihaz erişim bilgilerini çözen anahtar
 * ({@code device-access.key}), makine ayarları ({@code config.json}, yalnızca başvuru için; geri
 * yüklemede uygulanmaz) ve {@code manifest.json} (sürüm, şema, tarih, kayıt sayıları) bulunur.
 * Eski biçimdeki düz {@code .db} yedekler geri yüklenebilir ama otomatik temizliğe girmez.
 */
public final class BackupManager {

    /** Yedeğin neden alındığı. Yalnızca {@link #AUTO} otomatik temizlikte silinebilir. */
    @Getter
    public enum Kind {
        AUTO("auto", "Otomatik"),
        MANUAL("manual", "Elle"),
        PRE_MIGRATE("pre-migrate", "Güncelleme öncesi"),
        PRE_RESTORE("pre-restore", "Geri yükleme öncesi"),
        LEGACY(null, "Eski biçim"),
        /** Klasöre elle konmuş, adı Servicio biçiminde olmayan zip; geri yüklemede doğrulanır. */
        OTHER(null, "Diğer dosya");

        private final String code;
        private final String label;

        Kind(String code, String label) {
            this.code = code;
            this.label = label;
        }

        static Kind ofCode(String code) {
            for (Kind k : values()) {
                if (code.equals(k.code)) return k;
            }
            return null;
        }
    }

    /**
     * Klasördeki bir yedek dosyası; tarih ve sürüm dosya adından okunur (kopyalanınca değişen mtime'dan
     * değil, zip açılmadan). {@code appVersion} eski adlarda ve dış dosyalarda {@code null}.
     */
    public record Entry(File file, Kind kind, LocalDateTime createdAt, String appVersion) {
    }

    /** Doğrulanmış, uygulanmaya hazır geri yükleme; önizleme bilgisi de burada. */
    public record RestorePlan(File source, File preparedDb, byte[] key, LocalDateTime createdAt,
                              String appVersion, String schemaVersion, long customers, long workOrders) {
        public boolean hasKey() {
            return key != null;
        }
    }

    // Otomatik yedek saklama kuralı: en yeni 20 her zaman, daha eskilerden son 30 günün her gününden biri.
    private static final int KEEP_LATEST = 20;
    private static final int KEEP_DAILY_DAYS = 30;

    private static final String DB_ENTRY = "database.db";
    private static final String KEY_ENTRY = "device-access.key";
    private static final String CONFIG_ENTRY = "config.json";
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String KEY_FILE_NAME = "device-access.key";

    private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter LEGACY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final Pattern NAME = Pattern.compile(
            "servicio-(\\d{8}-\\d{6})(?:-\\d+)?-([a-z-]+?)(?:-v([0-9][0-9A-Za-z.-]*))?\\.zip");
    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Yedek alma ve geri yükleme aynı anda çalışmasın (zamanlayıcı + elle yedek + geri yükleme). */
    private static final Object LOCK = new Object();

    /** Son otomatik yedekte yaşanan sorun; başarılı yedekte temizlenir. Durum çubuğu ve Ayarlar gösterir. */
    @Getter
    private static volatile String lastProblem;

    private BackupManager() {
    }

    // ------------------------------------------------------------------ yedek alma

    /**
     * Yedek alır ve dosyayı döndürür. Ayarlanan klasöre (ör. takılı olmayan USB) yazılamıyorsa yedek
     * veri klasöründeki varsayılan {@code backups} klasörüne alınır ve {@link #getLastProblem()} set edilir.
     */
    public static File create(Kind kind) throws IOException {
        synchronized (LOCK) {
            File dir = AppSettings.getBackupDir();
            String problem = null;
            if (!isWritableDir(dir)) {
                File fallback = fallbackDir();
                if (sameDir(fallback, dir) || !isWritableDir(fallback)) {
                    throw new IOException("Yedek klasörüne yazılamıyor: " + dir.getAbsolutePath());
                }
                problem = "Yedek klasörüne ulaşılamadı (" + dir.getAbsolutePath() + "); yedek veri klasörüne alındı.";
                Servicio.getLogger().warn(problem);
                dir = fallback;
            }

            File tmpDb = new File(DatabaseManager.databaseFile().getParentFile(), "backup-tmp.db");
            Files.deleteIfExists(tmpDb.toPath());
            try {
                try (Connection conn = DatabaseManager.getConnection(); Statement st = conn.createStatement()) {
                    // VACUUM INTO parametre almaz; yoldaki tek tırnak SQL içinde ikilenerek kaçırılır.
                    String target = tmpDb.getAbsolutePath().replace("\\", "/").replace("'", "''");
                    st.execute("VACUUM INTO '" + target + "'");
                }
                DbInfo info = inspect(tmpDb);
                if (!info.integrityOk) {
                    // Yedek yine de alınır (elde kalan en iyi kopya olabilir); operatör uyarılır.
                    problem = "Veritabanında bütünlük sorunu tespit edildi; en son sağlam yedeği saklayın ve destek alın.";
                    Servicio.getLogger().error(problem);
                }

                LocalDateTime now = LocalDateTime.now();
                File zip = uniqueName(dir, now, kind);
                File partial = new File(dir, zip.getName() + ".part");
                writeZip(partial, tmpDb, kind, now, info);
                move(partial.toPath(), zip.toPath());

                Servicio.getLogger().info("Yedek alındı: {} ({})", zip.getName(), kind.getLabel());
                lastProblem = problem;
                return zip;
            } catch (SQLException e) {
                throw new IOException("Veritabanı kopyalanamadı: " + e.getMessage(), e);
            } finally {
                Files.deleteIfExists(tmpDb.toPath());
            }
        }
    }

    /** Arka plan yedekleri için: hata fırlatmaz, loglar ve {@link #getLastProblem()} üzerinden bildirir. */
    public static boolean createQuietly(Kind kind) {
        try {
            create(kind);
            if (kind == Kind.AUTO) prune();
            return true;
        } catch (Exception e) {
            lastProblem = "Son otomatik yedek alınamadı: " + e.getMessage();
            Servicio.getLogger().error("Yedekleme başarısız ({})", kind.getLabel(), e);
            return false;
        }
    }

    private static void writeZip(File target, File db, Kind kind, LocalDateTime createdAt, DbInfo info) throws IOException {
        File dataFolder = Servicio.getInstance().getDataFolder();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target.toPath()))) {
            putFile(zip, DB_ENTRY, db);
            File key = new File(dataFolder, KEY_FILE_NAME);
            if (key.isFile()) putFile(zip, KEY_ENTRY, key);
            File config = new File(dataFolder, "config.json");
            if (config.isFile()) putFile(zip, CONFIG_ENTRY, config);

            JsonObject manifest = new JsonObject();
            manifest.addProperty("format", 1);
            manifest.addProperty("app", Servicio.getInstance().getAppVersion());
            manifest.addProperty("schema", info.schemaVersion);
            manifest.addProperty("createdAt", createdAt.toString());
            manifest.addProperty("kind", kind.getCode());
            manifest.addProperty("customers", info.customers);
            manifest.addProperty("workOrders", info.workOrders);
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zip.write(GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            Files.deleteIfExists(target.toPath());
            throw e;
        }
    }

    private static void putFile(ZipOutputStream zip, String name, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file.toPath(), zip);
        zip.closeEntry();
    }

    private static File uniqueName(File dir, LocalDateTime time, Kind kind) {
        String stamp = time.format(NAME_TIME);
        String version = Servicio.getInstance().getAppVersion().replaceAll("[^0-9A-Za-z.-]", "");
        String suffix = "-" + kind.getCode() + (version.isEmpty() ? "" : "-v" + version) + ".zip";
        File file = new File(dir, "servicio-" + stamp + suffix);
        for (int i = 2; file.exists(); i++) {
            file = new File(dir, "servicio-" + stamp + "-" + i + suffix);
        }
        return file;
    }

    // ------------------------------------------------------------------ listeleme ve temizlik

    /** Klasördeki yedekler, en yenisi başta. Tanınmayan dosyalar (ör. eski {@code .sql}) listelenmez. */
    public static List<Entry> list(File dir) {
        File[] files = dir.listFiles(File::isFile);
        if (files == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (File f : files) {
            Entry e = parse(f);
            if (e != null) entries.add(e);
        }
        entries.sort(Comparator.comparing(Entry::createdAt).reversed());
        return entries;
    }

    /**
     * Ayarlanan klasördeki ve (farklıysa) veri klasöründeki yedekler, en yenisi başta. Ayarlanan klasöre
     * ulaşılamadığında alınan yedekler veri klasörüne düşer; operatör onları da görmeli.
     */
    public static List<Entry> listAll() {
        File dir = AppSettings.getBackupDir();
        File fallback = fallbackDir();
        if (sameDir(dir, fallback)) return list(dir);
        List<Entry> entries = new ArrayList<>(list(dir));
        entries.addAll(list(fallback));
        entries.sort(Comparator.comparing(Entry::createdAt).reversed());
        return entries;
    }

    /** Yedek, ayarlanan klasör yerine veri klasörüne düşmüşse {@code true}. */
    public static boolean isInFallback(Entry entry) {
        return isInFallback(entry.file());
    }

    /** Dosya, ayarlanan klasör yerine veri klasörüne düşmüşse {@code true}. */
    public static boolean isInFallback(File file) {
        File dir = AppSettings.getBackupDir();
        return !sameDir(dir, fallbackDir()) && sameDir(fallbackDir(), file.getParentFile());
    }

    /**
     * İki yol aynı klasörü mü gösteriyor? {@link File#equals} yolları metin olarak karşılaştırır;
     * ".\.servicio" ile tam yol ya da farklı büyük/küçük harf aynı klasörken farklı sayılır.
     */
    private static boolean sameDir(File a, File b) {
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.toPath().toAbsolutePath().normalize().equals(b.toPath().toAbsolutePath().normalize());
        }
    }

    /**
     * Operatöre gösterilecek güncel yedekleme sorunu; yoksa {@code null}. Klasöre erişim her çağrıda
     * yeniden denetlenir (USB takılınca uyarı kendiliğinden kalkar). Dosya sistemine dokunduğu için
     * EDT'de çağrılmamalı.
     */
    public static String currentProblem() {
        File dir = AppSettings.getBackupDir();
        if (!sameDir(dir, fallbackDir()) && !isReachable(dir)) {
            return "Yedek klasörüne ulaşılamıyor (" + dir.getAbsolutePath() + "); yedekler veri klasörüne alınıyor.";
        }
        String problem = lastProblem;
        // Klasör yeniden erişilebilir olduysa eski "ulaşılamadı" uyarısı geçerliliğini yitirir.
        return problem != null && problem.startsWith("Yedek klasörüne ulaşılamadı") ? null : problem;
    }

    /** Klasör var ya da oluşturulabilir (üst klasörü var) mı; oluşturmaz. */
    private static boolean isReachable(File dir) {
        if (dir.isDirectory()) return true;
        File parent = dir.getParentFile();
        return parent != null && parent.isDirectory();
    }

    /** En yeni yedeğin tarihi (tanınmayan zip'ler hariç); hiç yedek yoksa {@code null}. */
    public static LocalDateTime latestBackupTime() {
        return listAll().stream()
                .filter(e -> e.kind() != Kind.OTHER)
                .findFirst().map(Entry::createdAt).orElse(null);
    }

    private static Entry parse(File f) {
        String name = f.getName();
        Matcher m = NAME.matcher(name);
        if (m.matches()) {
            Kind kind = Kind.ofCode(m.group(2));
            if (kind != null) return new Entry(f, kind, LocalDateTime.parse(m.group(1), NAME_TIME), m.group(3));
        }
        if (name.toLowerCase().endsWith(".zip")) {
            return new Entry(f, Kind.OTHER, LocalDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault()), null);
        }
        if (name.endsWith(".db")) {
            LocalDateTime time;
            try {
                time = LocalDateTime.parse(name.substring(0, name.length() - 3), LEGACY_TIME);
            } catch (Exception e) {
                time = LocalDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
            }
            return new Entry(f, Kind.LEGACY, time, null);
        }
        return null;
    }

    /**
     * Otomatik yedekleri saklama kuralına göre temizler: en yeni {@value #KEEP_LATEST} tanesi kalır;
     * daha eskilerden son {@value #KEEP_DAILY_DAYS} günün her gününün en yeni yedeği kalır; gerisi silinir.
     * Elle alınan, güncelleme/geri yükleme öncesi ve eski biçimdeki yedeklere dokunulmaz.
     */
    public static void prune() {
        synchronized (LOCK) {
            List<Entry> autos = list(AppSettings.getBackupDir()).stream()
                    .filter(e -> e.kind() == Kind.AUTO).toList();
            LocalDate oldestDay = LocalDate.now().minusDays(KEEP_DAILY_DAYS);
            Set<LocalDate> keptDays = new HashSet<>();
            int deleted = 0;
            for (int i = 0; i < autos.size(); i++) {
                Entry e = autos.get(i);
                LocalDate day = e.createdAt().toLocalDate();
                boolean keep = i < KEEP_LATEST || (!day.isBefore(oldestDay) && keptDays.add(day));
                if (i < KEEP_LATEST) keptDays.add(day);
                if (keep) continue;
                if (e.file().delete()) deleted++;
                else Servicio.getLogger().warn("Eski yedek silinemedi: {}", e.file().getName());
            }
            if (deleted > 0) Servicio.getLogger().info("Saklama kuralı: {} eski otomatik yedek silindi.", deleted);
        }
    }

    // ------------------------------------------------------------------ geri yükleme

    /**
     * Yedeği geçici bir dosyaya açar ve doğrular (SQLite dosyası mı, bütünlük, şema bu sürümden yeni mi).
     * Canlı veritabanına dokunmaz; arka planda çağrılır. Sorunlarda operatöre gösterilecek
     * {@link ValidationException} fırlatır.
     */
    public static RestorePlan prepare(File source) throws IOException {
        synchronized (LOCK) {
            File tmp = restoreTmpFile();
            Files.deleteIfExists(tmp.toPath());
            byte[] key = null;
            JsonObject manifest = null;
            try {
                if (source.getName().toLowerCase().endsWith(".zip")) {
                    try (ZipFile zip = new ZipFile(source)) {
                        ZipEntry db = zip.getEntry(DB_ENTRY);
                        if (db == null) throw new ValidationException("Bu dosya bir Servicio yedeği değil (içinde veritabanı yok).");
                        try (InputStream in = zip.getInputStream(db)) {
                            Files.copy(in, tmp.toPath());
                        }
                        ZipEntry keyEntry = zip.getEntry(KEY_ENTRY);
                        if (keyEntry != null) {
                            try (InputStream in = zip.getInputStream(keyEntry)) {
                                key = in.readAllBytes();
                            }
                        }
                        ZipEntry man = zip.getEntry(MANIFEST_ENTRY);
                        if (man != null) {
                            try (InputStream in = zip.getInputStream(man)) {
                                manifest = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
                            }
                        }
                    } catch (java.util.zip.ZipException e) {
                        throw new ValidationException("Yedek dosyası bozuk, açılamadı.");
                    }
                } else {
                    Files.copy(source.toPath(), tmp.toPath());
                }

                if (!hasSqliteHeader(tmp)) throw new ValidationException("Seçilen dosya bir veritabanı yedeği değil.");
                DbInfo info = inspect(tmp);
                if (!info.integrityOk) throw new ValidationException("Yedekteki veritabanı bozuk; bu yedek geri yüklenemez.");
                if (!info.hasCustomers) throw new ValidationException("Bu dosya bir Servicio veritabanı değil.");

                String latest = DatabaseManager.getLatestSchemaVersion();
                if (info.schemaVersion != null && latest != null
                        && MigrationVersion.fromVersion(info.schemaVersion).isNewerThan(latest)) {
                    throw new ValidationException("Bu yedek daha yeni bir Servicio sürümüyle alınmış (şema V" + info.schemaVersion
                            + "). Geri yüklemeden önce uygulamayı güncelleyin.");
                }

                Entry entry = parse(source);
                LocalDateTime createdAt = manifest != null && manifest.has("createdAt")
                        ? LocalDateTime.parse(manifest.get("createdAt").getAsString())
                        : entry != null ? entry.createdAt()
                        : LocalDateTime.ofInstant(Instant.ofEpochMilli(source.lastModified()), ZoneId.systemDefault());
                String app = manifest != null && manifest.has("app") ? manifest.get("app").getAsString()
                        : entry != null ? entry.appVersion() : null;
                return new RestorePlan(source, tmp, key, createdAt, app, info.schemaVersion, info.customers, info.workOrders);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(tmp.toPath());
                throw e;
            }
        }
    }

    /** Hazırlanıp onaylanmayan geri yüklemenin geçici dosyasını siler. */
    public static void discard(RestorePlan plan) {
        try {
            Files.deleteIfExists(plan.preparedDb().toPath());
        } catch (IOException ignored) {
            // Bir sonraki hazırlıkta zaten silinir.
        }
    }

    /**
     * Hazırlanmış yedeği uygular. Sıra: zamanlayıcıları durdur → geri yükleme öncesi yedek al (alınamazsa
     * dur) → havuzu kapat → mevcut veritabanını ve anahtarı kenara al → yedeği yerine koy → aç ve
     * migration'ları çalıştır. Açılış başarısız olursa kenara alınan dosyalar geri konur ve hata fırlatılır.
     * <p>
     * Bilerek çağıranın thread'inde (EDT) çalışır: geri yükleme sürerken başka ekranlar kapanan havuza
     * sorgu atmasın.
     */
    public static void apply(RestorePlan plan) throws IOException {
        synchronized (LOCK) {
            BackupScheduler.stop();
            DeviceAccessPurgeScheduler.stop();
            try {
                create(Kind.PRE_RESTORE);
            } catch (IOException e) {
                BackupScheduler.start();
                DeviceAccessPurgeScheduler.start();
                throw new ValidationException("Geri yüklemeden önce mevcut verinin yedeği alınamadı; işlem yapılmadı. "
                        + "Yedek klasörünü kontrol edin.");
            }

            Path db = DatabaseManager.databaseFile().toPath();
            Path keyFile = new File(Servicio.getInstance().getDataFolder(), KEY_FILE_NAME).toPath();
            List<Path[]> setAside = new ArrayList<>();
            Servicio.getLogger().warn("Geri yükleme başlatılıyor: {}", plan.source().getName());

            boolean applied = false;
            DatabaseManager.beginRestore();
            try {
                for (String suffix : new String[]{"", "-wal", "-shm"}) {
                    Path p = Path.of(db + suffix);
                    if (Files.exists(p)) setAside.add(aside(p));
                }
                if (plan.hasKey()) {
                    if (Files.exists(keyFile)) setAside.add(aside(keyFile));
                    Files.write(keyFile, plan.key());
                }
                move(plan.preparedDb().toPath(), db);
                DatabaseManager.endRestore();
                DatabaseManager.initialize();
                applied = true;
            } catch (Exception e) {
                Servicio.getLogger().error("Geri yükleme başarısız, önceki veriye dönülüyor", e);
                DatabaseManager.beginRestore();
                Files.deleteIfExists(db);
                Files.deleteIfExists(Path.of(db + "-wal"));
                Files.deleteIfExists(Path.of(db + "-shm"));
                if (plan.hasKey()) Files.deleteIfExists(keyFile);
                // Kenara alınan dosyalar yalnızca başarılı geri yüklemeden sonra silinir; geri dönüş de
                // başarısız olursa ".before-restore" dosyaları elle kurtarma için yerinde kalır.
                for (Path[] pair : setAside) move(pair[1], pair[0]);
                DatabaseManager.endRestore();
                DatabaseManager.initialize();
                throw new ValidationException("Yedek açılamadı; önceki veriler geri getirildi. Ayrıntılar log dosyasında.");
            } finally {
                if (applied) {
                    for (Path[] pair : setAside) Files.deleteIfExists(pair[1]);
                }
                DatabaseManager.endRestore();
                discard(plan);
                CryptoUtil.resetKeyCache();
                ServiceManager.initialize();
                BackupScheduler.start();
                DeviceAccessPurgeScheduler.start();
            }
            Servicio.getLogger().info("Geri yükleme başarılı: {}", plan.source().getName());
        }
    }

    private static Path[] aside(Path p) throws IOException {
        Path target = Path.of(p + ".before-restore");
        move(p, target);
        return new Path[]{p, target};
    }

    private static File restoreTmpFile() {
        return new File(DatabaseManager.databaseFile().getParentFile(), "restore-tmp.db");
    }

    // ------------------------------------------------------------------ yardımcılar

    private record DbInfo(boolean integrityOk, boolean hasCustomers, String schemaVersion, long customers, long workOrders) {
    }

    /** Veritabanı dosyasını ayrı bir bağlantıyla açıp bütünlük, şema sürümü ve kayıt sayılarını okur. */
    private static DbInfo inspect(File db) throws IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
             Statement st = conn.createStatement()) {
            boolean ok;
            try (ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
                ok = rs.next() && "ok".equalsIgnoreCase(rs.getString(1));
            }
            boolean hasCustomers = tableExists(st, "customers");
            String schema = null;
            if (tableExists(st, "flyway_schema_history")) {
                try (ResultSet rs = st.executeQuery("SELECT version FROM flyway_schema_history "
                        + "WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")) {
                    if (rs.next()) schema = rs.getString(1);
                }
            }
            long customers = hasCustomers ? count(st, "customers") : 0;
            long workOrders = tableExists(st, "work_orders") ? count(st, "work_orders") : 0;
            return new DbInfo(ok, hasCustomers, schema, customers, workOrders);
        } catch (SQLException e) {
            throw new ValidationException("Yedekteki veritabanı okunamadı: " + e.getMessage());
        }
    }

    private static boolean tableExists(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'")) {
            return rs.next();
        }
    }

    private static long count(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static boolean hasSqliteHeader(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            return Arrays.equals(in.readNBytes(SQLITE_HEADER.length), SQLITE_HEADER);
        }
    }

    /** Veri klasöründeki varsayılan yedek klasörü; ayarlanan klasöre ulaşılamazsa yedekler buraya alınır. */
    public static File fallbackDir() {
        return new File(Servicio.getInstance().getDataFolder(), "backups");
    }

    private static boolean isWritableDir(File dir) {
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        return Files.isWritable(dir.toPath());
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
