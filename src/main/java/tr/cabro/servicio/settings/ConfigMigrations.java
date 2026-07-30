package tr.cabro.servicio.settings;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.model.enums.BackupMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Eski ayar depolarından yeni {@code config.json} biçimine tek seferlik, idempotent geçişler.
 * <p>
 * İki ayrı geçiş var:
 * <ol>
 *   <li><b>Şema v1 → v2:</b> okaeri döneminden kalan düz (gruplanmamış) anahtarlar yeni bloklara taşınır.</li>
 *   <li><b>Registry tasfiyesi:</b> Tema/vurgu rengi/arama geçmişi {@code /raven-flatlaf-demo}, atlanan
 *       güncelleme sürümü {@code /tr/cabro/servicio} düğümünde tutuluyordu. Değerler okunup dosyaya
 *       taşınır ve düğümler silinir — böylece ayarlar OS'a özgü olmaktan çıkıp veri klasörüyle
 *       birlikte taşınır/yedeklenir.</li>
 * </ol>
 * İşletme politikası ayarları ({@code barcode_prefix}, cihaz erişim kodu silme süresi, otomatik kilit
 * süresi) burada dosyadan çıkarılır ama veritabanına yazılamaz — bu sınıf çalıştığında veritabanı
 * henüz açık değildir. Değerler {@link #getPendingBusinessSettings()} içinde bekletilir ve servisler
 * ayağa kalkınca {@code AppSettingService} tarafından tüketilir
 * ({@code DeviceAccessCredentialService.migrateLegacyDevicePasswords} ile aynı desen).
 */
public final class ConfigMigrations {

    private static final Logger log = LoggerFactory.getLogger(ConfigMigrations.class);

    /** Tema/vurgu/arama geçmişinin tutulduğu eski düğüm (FlatLaf demo kodundan miras). */
    private static final String LEGACY_UI_NODE = "/raven-flatlaf-demo";

    /** Atlanan güncelleme sürümünün tutulduğu eski düğüm. */
    private static final String LEGACY_UPDATE_NODE = "/tr/cabro/servicio";

    /** Veritabanı hazır olunca {@code app_settings} tablosuna yazılacak eski işletme ayarları. */
    private static final Map<String, String> PENDING_BUSINESS_SETTINGS = new LinkedHashMap<>();

    private ConfigMigrations() {}

    /**
     * Ham JSON'u okunabilir bir {@link AppConfig}'e çevirir; gerekiyorsa şema geçişini uygular.
     *
     * @return yeni yapıya taşınmış config ve dosyanın yeniden yazılması gerekip gerekmediği
     */
    public static Result migrate(JsonObject root, Gson gson) {
        int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;

        AppConfig config;
        boolean changed;

        if (version < AppConfig.CURRENT_SCHEMA_VERSION) {
            log.info("Ayar dosyası şema v{} → v{} taşınıyor", version, AppConfig.CURRENT_SCHEMA_VERSION);
            config = fromLegacyFlatFormat(root);
            changed = true;
        } else {
            config = gson.fromJson(root, AppConfig.class);
            if (config == null) config = new AppConfig();
            changed = false;
        }

        config.normalize();
        config.setSchemaVersion(AppConfig.CURRENT_SCHEMA_VERSION);

        return new Result(config, changed);
    }

    /**
     * okaeri döneminin düz anahtarlarını yeni bloklara dağıtır. Eksik anahtarlar için
     * {@link AppConfig} alan varsayılanları geçerli kalır.
     */
    private static AppConfig fromLegacyFlatFormat(JsonObject root) {
        AppConfig config = new AppConfig();

        getBoolean(root, "full_size").ifPresent(config.getUi()::setFullSize);
        getBoolean(root, "skipExitConfirmation").ifPresent(config.getUi()::setSkipExitConfirmation);

        AppConfig.Tables tables = config.getTables();
        getInt(root, "workOrderPageSize").ifPresent(tables::setWorkOrderPageSize);
        getInt(root, "partPageSize").ifPresent(tables::setPartPageSize);
        getInt(root, "customerPageSize").ifPresent(tables::setCustomerPageSize);
        getInt(root, "devicePageSize").ifPresent(tables::setDevicePageSize);
        getInt(root, "supplierPageSize").ifPresent(tables::setSupplierPageSize);

        JsonElement backupElement = root.get("backup");
        if (backupElement != null && backupElement.isJsonObject()) {
            JsonObject backup = backupElement.getAsJsonObject();
            getString(backup, "path").ifPresent(config.getBackup()::setPath);
            getInt(backup, "interval").ifPresent(config.getBackup()::setInterval);
            getString(backup, "mode").ifPresent(mode -> {
                try {
                    config.getBackup().setMode(BackupMode.valueOf(mode));
                } catch (IllegalArgumentException e) {
                    log.warn("Bilinmeyen yedekleme modu '{}', varsayılan kullanılıyor", mode);
                }
            });
        }

        // İşletme politikaları artık dosyada değil veritabanında tutuluyor — değerleri bekletiyoruz.
        // Barkod öneki iki farklı anahtarla yazılmış olabilir: okaeri'nin @Variable anotasyonu JSON
        // anahtarını değiştirmediği için gerçek dosyalarda "barcodePrefix" duruyor, ama eski
        // sürümlerde "barcode_prefix" da görülebiliyor — ikisi de kontrol ediliyor.
        getString(root, "barcodePrefix")
                .or(() -> getString(root, "barcode_prefix"))
                .ifPresent(value -> PENDING_BUSINESS_SETTINGS.put(SettingKeys.BARCODE_PREFIX, value));
        getInt(root, "deviceAccessPurgeHours")
                .ifPresent(value -> PENDING_BUSINESS_SETTINGS.put(SettingKeys.DEVICE_ACCESS_PURGE_HOURS, String.valueOf(value)));
        getInt(root, "autoLockTimeoutMinutes")
                .ifPresent(value -> PENDING_BUSINESS_SETTINGS.put(SettingKeys.AUTO_LOCK_MINUTES, String.valueOf(value)));

        return config;
    }

    /**
     * Windows registry / Linux prefs dosyasındaki eski düğümleri config'e taşır ve siler.
     * Dosyada zaten bir değer varsa üzerine YAZMAZ — dosya her zaman daha günceldir.
     *
     * @return bir şey taşındıysa {@code true} (çağıran dosyayı kaydetmelidir)
     */
    public static boolean importFromLegacyPreferences(AppConfig config) {
        boolean changed = importLegacyUiNode(config);
        changed |= importLegacyUpdateNode(config);
        return changed;
    }

    private static boolean importLegacyUiNode(AppConfig config) {
        try {
            if (!Preferences.userRoot().nodeExists(LEGACY_UI_NODE)) return false;

            Preferences node = Preferences.userRoot().node(LEGACY_UI_NODE);
            AppConfig.Ui ui = config.getUi();

            if (ui.getLafClassName() == null) ui.setLafClassName(node.get("laf", null));
            if (ui.getLafTheme() == null) ui.setLafTheme(node.get("lafTheme", null));

            if (ui.getAccentColor() == null) {
                String accent = node.get("accent", null);
                if (accent != null) {
                    try {
                        ui.setAccentColor(Integer.parseInt(accent));
                    } catch (NumberFormatException e) {
                        log.warn("Eski vurgu rengi okunamadı: {}", accent);
                    }
                }
            }

            if (ui.getRecentSearch().isEmpty()) {
                ui.setRecentSearch(splitLegacyList(node.get("recentSearch", null)));
            }
            if (ui.getRecentSearchFavorite().isEmpty()) {
                ui.setRecentSearchFavorite(splitLegacyList(node.get("recentSearchFavorite", null)));
            }

            node.removeNode();
            node.flush();
            log.info("Eski tema/arama ayarları config.json'a taşındı, '{}' düğümü silindi", LEGACY_UI_NODE);
            return true;

        } catch (Exception e) {
            log.warn("Eski tema ayarları taşınamadı (yok sayılıyor): {}", e.getMessage());
            return false;
        }
    }

    private static boolean importLegacyUpdateNode(AppConfig config) {
        try {
            if (!Preferences.userRoot().nodeExists(LEGACY_UPDATE_NODE)) return false;

            Preferences node = Preferences.userRoot().node(LEGACY_UPDATE_NODE);
            if (config.getUpdate().getSkippedVersion() == null) {
                config.getUpdate().setSkippedVersion(node.get("update.skipped_version", null));
            }

            node.removeNode();
            node.flush();
            log.info("Eski güncelleme ayarı config.json'a taşındı, '{}' düğümü silindi", LEGACY_UPDATE_NODE);
            return true;

        } catch (Exception e) {
            log.warn("Eski güncelleme ayarı taşınamadı (yok sayılıyor): {}", e.getMessage());
            return false;
        }
    }

    /** Eski depo listeleri virgülle birleştirilmiş tek bir metin olarak tutuyordu. */
    private static List<String> splitLegacyList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) return result;
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    /**
     * Veritabanına taşınmayı bekleyen eski işletme ayarları. Tüketildikten sonra
     * {@link #clearPendingBusinessSettings()} çağrılmalıdır.
     */
    public static Map<String, String> getPendingBusinessSettings() {
        return PENDING_BUSINESS_SETTINGS;
    }

    public static void clearPendingBusinessSettings() {
        PENDING_BUSINESS_SETTINGS.clear();
    }

    // ── JSON okuma yardımcıları (eksik/hatalı tipli alanlarda sessizce boş döner) ─────────────

    private static java.util.Optional<String> getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return java.util.Optional.empty();
        return java.util.Optional.of(element.getAsString());
    }

    private static java.util.Optional<Integer> getInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(element.getAsInt());
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<Boolean> getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return java.util.Optional.empty();
        return java.util.Optional.of(element.getAsBoolean());
    }

    /** Geçiş sonucu: taşınmış config ve dosyanın yeniden yazılması gerekip gerekmediği. */
    public record Result(AppConfig config, boolean changed) {}
}
