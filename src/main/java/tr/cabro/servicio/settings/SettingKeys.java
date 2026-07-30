package tr.cabro.servicio.settings;

/**
 * Veritabanındaki {@code app_settings} tablosunda kullanılan anahtarlar.
 * <p>
 * Buradaki ayarlar <b>işletmeye</b> aittir (makineye değil): yedeğe girer, yedek geri yüklenince
 * geri gelir ve ileride ortak bir arka uçla paylaşılabilir. Makineye özel tercihler için
 * {@link AppConfig} (config.json) kullanılır.
 */
public final class SettingKeys {

    /** Barkod numaralarının başına eklenen işletme öneki. */
    public static final String BARCODE_PREFIX = "barcode.prefix";

    /** Cihaz erişim kodunun (PIN/şifre/desen) teslimattan kaç saat sonra kalıcı silineceği. */
    public static final String DEVICE_ACCESS_PURGE_HOURS = "device_access.purge_hours";

    /** Hareketsizlik sonrası ekranın kaç dakikada kilitleneceği. */
    public static final String AUTO_LOCK_MINUTES = "security.auto_lock_minutes";

    private SettingKeys() {}
}
