package tr.cabro.servicio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.database.repository.AppSettingRepository;
import tr.cabro.servicio.settings.ConfigMigrations;
import tr.cabro.servicio.settings.SettingKeys;

import java.util.HashMap;
import java.util.Map;

/**
 * İşletme ayarlarını ({@code app_settings} tablosu) yöneten servis.
 * <p>
 * Değerler açılışta tek sorguyla belleğe alınır; okuma sırasında veritabanına gidilmez
 * (bu ayarlar barkod üretimi gibi sık çağrılan yerlerde kullanılıyor). Yazma hem tabloyu
 * hem önbelleği günceller.
 * <p>
 * Makineye özel tercihler burada DEĞİL, {@link tr.cabro.servicio.settings.AppSettings}
 * üzerinden {@code config.json}'da tutulur.
 */
public class AppSettingService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingService.class);

    public static final String DEFAULT_BARCODE_PREFIX = "123456";
    public static final int DEFAULT_DEVICE_ACCESS_PURGE_HOURS = 24;
    public static final int DEFAULT_AUTO_LOCK_MINUTES = 5;

    private final AppSettingRepository repository;
    private final Map<String, String> cache = new HashMap<>();

    public AppSettingService(AppSettingRepository repository) {
        this.repository = repository;
        reload();
    }

    public void reload() {
        cache.clear();
        cache.putAll(repository.findAll());
    }

    /**
     * config.json'dan taşınmayı bekleyen eski işletme ayarlarını tabloya yazar.
     * Yalnızca tabloda henüz olmayan anahtarları yazar — birden fazla çalıştırılması güvenlidir.
     */
    public void consumePendingMigration() {
        Map<String, String> pending = ConfigMigrations.getPendingBusinessSettings();
        if (pending.isEmpty()) return;

        for (Map.Entry<String, String> entry : pending.entrySet()) {
            if (cache.containsKey(entry.getKey())) continue;
            setString(entry.getKey(), entry.getValue());
            log.info("Eski ayar config.json'dan veritabanına taşındı: {}", entry.getKey());
        }
        ConfigMigrations.clearPendingBusinessSettings();
    }

    // ── Tipli erişimciler ─────────────────────────────────────────────────────

    public String getBarcodePrefix() {
        return getString(SettingKeys.BARCODE_PREFIX, DEFAULT_BARCODE_PREFIX);
    }

    public void setBarcodePrefix(String prefix) {
        setString(SettingKeys.BARCODE_PREFIX, prefix);
    }

    public int getDeviceAccessPurgeHours() {
        return getInt(SettingKeys.DEVICE_ACCESS_PURGE_HOURS, DEFAULT_DEVICE_ACCESS_PURGE_HOURS);
    }

    public void setDeviceAccessPurgeHours(int hours) {
        setInt(SettingKeys.DEVICE_ACCESS_PURGE_HOURS, hours);
    }

    public int getAutoLockMinutes() {
        return getInt(SettingKeys.AUTO_LOCK_MINUTES, DEFAULT_AUTO_LOCK_MINUTES);
    }

    public void setAutoLockMinutes(int minutes) {
        setInt(SettingKeys.AUTO_LOCK_MINUTES, minutes);
    }

    // ── Genel erişim ──────────────────────────────────────────────────────────

    public String getString(String key, String defaultValue) {
        String value = cache.get(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public void setString(String key, String value) {
        repository.upsert(key, value);
        cache.put(key, value);
    }

    public int getInt(String key, int defaultValue) {
        String value = cache.get(key);
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("'{}' ayarı sayıya çevrilemedi ('{}'), varsayılan kullanılıyor", key, value);
            return defaultValue;
        }
    }

    public void setInt(String key, int value) {
        setString(key, String.valueOf(value));
    }
}
