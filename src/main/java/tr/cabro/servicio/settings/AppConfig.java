package tr.cabro.servicio.settings;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.enums.BackupMode;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code config.json} dosyasının bire bir karşılığı olan düz veri sınıfı.
 * <p>
 * Buraya YALNIZCA makineye/kullanıcıya özel tercihler girer: pencere durumu, tema, tablo sayfa
 * boyutları, yedekleme hedefi, atlanan güncelleme sürümü. İşletmeye ait politikalar (barkod öneki,
 * cihaz erişim kodu silme süresi, otomatik kilit süresi) veritabanındaki {@code app_settings}
 * tablosunda tutulur — çünkü onların yedeğe girmesi ve geri yüklenince gelmesi gerekir.
 * Bkz. {@link tr.cabro.servicio.service.AppSettingService}.
 * <p>
 * Bu sınıf hiçbir uygulama nesnesine (özellikle {@code Servicio}) bağımlı olmamalıdır; ayarlar
 * uygulamanın en erken aşamasında, veritabanı ve UI kurulmadan önce yüklenir.
 */
@Getter
@Setter
public class AppConfig {

    /** Dosya biçimi sürümü. Yapı değiştiğinde artırılır ve {@link ConfigMigrations}'a geçiş eklenir. */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;

    private Ui ui = new Ui();
    private Tables tables = new Tables();
    private Backup backup = new Backup();
    private Update update = new Update();

    /**
     * Elle düzenlenmiş ya da eksik bir dosyadan sonra {@code null} kalan blokları varsayılana çeker.
     * Gson, JSON'da bulunmayan alanlara dokunmaz ama açıkça {@code null} yazılmış olanları null yapar.
     */
    public void normalize() {
        if (ui == null) ui = new Ui();
        if (tables == null) tables = new Tables();
        if (backup == null) backup = new Backup();
        if (update == null) update = new Update();
        ui.normalize();
        backup.normalize();
    }

    /** Arayüz tercihleri — pencere, tema, arama geçmişi. */
    @Getter
    @Setter
    public static class Ui {

        private boolean fullSize = false;
        private boolean skipExitConfirmation = false;

        /** Aktif Look&Feel sınıf adı (ör. {@code com.formdev.flatlaf.FlatLightLaf}). */
        private String lafClassName;

        /** IntelliJ tema dosyası referansı ({@code res:<dosya>.theme.json}); yalnızca ThemeLaf için anlamlı. */
        private String lafTheme;

        /** Vurgu rengi, {@link java.awt.Color#getRGB()} değeri. Boşsa tema varsayılanı kullanılır. */
        private Integer accentColor;

        private List<String> recentSearch = new ArrayList<>();
        private List<String> recentSearchFavorite = new ArrayList<>();

        /** Arayüz mesajlarının dili. {@code "auto" | "tr" | "en"}. */
        private String language = "auto";

        /** Tarih sırası ve sayı ayırıcıları için bölge. {@code "auto" | "tr-TR" | "en-US" | "en-GB" | "de-DE"}. */
        private String formatRegion = "auto";

        /** Sadece gösterim sembolü; iş her zaman TL bazlı hesaplanır. {@code "TRY" | "USD" | "EUR" | "GBP"}. */
        private String currencyCode = "TRY";

        void normalize() {
            if (recentSearch == null) recentSearch = new ArrayList<>();
            if (recentSearchFavorite == null) recentSearchFavorite = new ArrayList<>();
        }
    }

    /** Liste ekranlarında sayfa başına gösterilen kayıt sayıları. */
    @Getter
    @Setter
    public static class Tables {

        private int workOrderPageSize = 10;
        private int partPageSize = 10;
        private int customerPageSize = 10;
        private int devicePageSize = 10;
        private int supplierPageSize = 10;
    }

    /** Yedekleme tercihleri. */
    @Getter
    @Setter
    public static class Backup {

        /**
         * Yedek klasörü. Varsayılan olarak veri klasörüne GÖRELİ tutulur ({@code backups}); kullanıcı
         * dışarıdan bir klasör seçerse mutlak yol saklanır. Çözümleme {@link AppSettings#getBackupDir()}
         * üzerinden yapılır — veri klasörü taşınsa bile varsayılan yol bozulmaz.
         */
        private String path = "backups";

        private BackupMode mode = BackupMode.ON_START;
        private int interval = 15;

        void normalize() {
            if (path == null || path.isBlank()) path = "backups";
            if (mode == null) mode = BackupMode.ON_START;
        }
    }

    /** Güncelleme tercihleri. */
    @Getter
    @Setter
    public static class Update {

        /** Kullanıcının "bu sürümü atla" dediği sürüm; bu sürüm için tekrar bildirim gösterilmez. */
        private String skippedVersion;
    }
}
