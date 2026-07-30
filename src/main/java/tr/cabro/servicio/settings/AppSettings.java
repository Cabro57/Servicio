package tr.cabro.servicio.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Makine/kullanıcı tercihlerine ({@code config.json}) tek erişim noktası.
 * <p>
 * Uygulamanın her yerinden {@code AppSettings.get()} ile okunur, değişiklikten sonra
 * {@code AppSettings.save()} çağrılır. İşletme politikaları için
 * {@link tr.cabro.servicio.service.AppSettingService} kullanılır — ayrım için bkz. {@link AppConfig}.
 * <p>
 * {@link #init(File)} veritabanı ve arayüzden ÖNCE, {@code Servicio} kurulurken çağrılır;
 * tema kurulumu bu ayarları okuduğu için sıralamanın bozulmaması gerekir.
 */
public final class AppSettings {

    private static final Logger log = LoggerFactory.getLogger(AppSettings.class);

    private static JsonConfigStore store;
    private static AppConfig config;
    private static File dataFolder;

    private AppSettings() {}

    public static void init(File dataFolder) {
        AppSettings.dataFolder = dataFolder;
        AppSettings.store = new JsonConfigStore(new File(dataFolder, "config.json"));
        AppSettings.config = store.load();
        normalizeBackupPath();
    }

    /**
     * Eski sürümler yedek klasörünü her zaman mutlak yol olarak yazıyordu — varsayılan konumu
     * gösteriyorsa göreliye çevirir. Böylece veri klasörü taşındığında (ör. portable kurulumdan
     * kullanıcı klasörüne geçiş) yedek yolu artık var olmayan bir dizini göstermez.
     */
    private static void normalizeBackupPath() {
        String before = config.getBackup().getPath();
        setBackupDir(getBackupDir());
        if (!before.equals(config.getBackup().getPath())) {
            log.info("Yedek klasörü veri klasörüne göreli hale getirildi: {}", config.getBackup().getPath());
            save();
        }
    }

    /**
     * Aktif ayarlar. {@link #init(File)} çağrılmadan kullanılırsa (ör. bir birim testi) bellekte
     * kalan varsayılanlarla çalışır — hiçbir zaman {@code null} dönmez.
     */
    public static AppConfig get() {
        if (config == null) {
            log.warn("Ayarlar yüklenmeden okundu, varsayılanlar kullanılıyor");
            config = new AppConfig();
        }
        return config;
    }

    public static void save() {
        if (store != null && config != null) {
            store.save(config);
        }
    }

    /** Yedeklerin yazılacağı klasör. Göreli saklanan yol veri klasörüne göre çözülür. */
    public static File getBackupDir() {
        String path = get().getBackup().getPath();
        File candidate = new File(path);
        return candidate.isAbsolute() ? candidate : new File(dataFolder, path);
    }

    /**
     * Yedek klasörünü ayarlar. Klasör veri klasörünün içindeyse göreli olarak saklanır; böylece
     * uygulama başka bir makineye/klasöre taşındığında yol kendiliğinden doğru kalır.
     */
    public static void setBackupDir(File dir) {
        String path = dir.getAbsolutePath();
        if (dataFolder != null) {
            String base = dataFolder.getAbsolutePath() + File.separator;
            if (path.startsWith(base)) {
                path = path.substring(base.length());
            }
        }
        get().getBackup().setPath(path);
    }
}
