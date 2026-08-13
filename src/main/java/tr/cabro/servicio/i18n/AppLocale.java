package tr.cabro.servicio.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.settings.AppSettings;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Uygulamanın aktif dil/bölge/para birimi durumunu tutan merkezi sınıf.
 * <p>
 * Windows'un Bölge ayarlarındaki gibi üç bağımsız tercih tutar:
 * <ul>
 *     <li>{@code uiLocale} — arayüz mesajlarının dili ({@link Messages}'in kullandığı bundle).</li>
 *     <li>{@code formatLocale} — tarih sırası ve sayı ayırıcıları için "bölge".</li>
 *     <li>{@code currencyCode} — sadece gösterim sembolü; iş her zaman TL bazlı hesaplanır.</li>
 * </ul>
 * Her biri ilk açılışta {@code "auto"} ise işletim sistemi ayarından tespit edilir;
 * kullanıcı Ayarlar'dan elle seçim yaparsa o alan sabit bir değere geçer.
 * <p>
 * Bu sınıfın kapsamındaki tüm tüketiciler (onay/hata diyalogları, toast, tarih/sayı
 * formatlayıcıları) her çağrıda güncel değeri okuduğu için, {@link #setUiLocale},
 * {@link #setFormatLocale} ya da {@link #setCurrencyCode} çağrıldığında değişiklik
 * restart gerekmeden bir sonraki gösterimde yansır.
 */
public final class AppLocale {

    private static final Logger log = LoggerFactory.getLogger(AppLocale.class);

    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private static final List<Locale> SUPPORTED_UI_LOCALES = List.of(
            new Locale("tr"), new Locale("en")
    );

    private static final List<Locale> SUPPORTED_FORMAT_LOCALES = List.of(
            new Locale("tr", "TR"),
            new Locale("en", "US"),
            new Locale("en", "GB"),
            new Locale("de", "DE")
    );

    private static final Map<String, String> CURRENCY_SYMBOLS = Map.of(
            "TRY", "₺",
            "USD", "$",
            "EUR", "€",
            "GBP", "£"
    );

    private static final Locale DEFAULT_UI_LOCALE = new Locale("tr");
    private static final Locale DEFAULT_FORMAT_LOCALE = new Locale("tr", "TR");
    private static final String DEFAULT_CURRENCY = "TRY";

    private static volatile Locale uiLocale = DEFAULT_UI_LOCALE;
    private static volatile Locale formatLocale = DEFAULT_FORMAT_LOCALE;
    private static volatile String currencyCode = DEFAULT_CURRENCY;
    private static volatile ResourceBundle bundle = loadBundle(DEFAULT_UI_LOCALE);

    private AppLocale() {}

    /** {@code AppSettings.init(...)}'ten hemen sonra, UI kurulmadan önce çağrılmalıdır. */
    public static void init() {
        var ui = AppSettings.get().getUi();

        uiLocale = resolveUiLocale(ui.getLanguage());
        formatLocale = resolveFormatLocale(ui.getFormatRegion());
        currencyCode = resolveCurrency(ui.getCurrencyCode());
        bundle = loadBundle(uiLocale);

        log.info("Aktif dil/bölge: arayüz={}, biçim={}, para birimi={}", uiLocale, formatLocale, currencyCode);
    }

    public static Locale uiLocale() {
        return uiLocale;
    }

    public static Locale formatLocale() {
        return formatLocale;
    }

    public static String currencyCode() {
        return currencyCode;
    }

    public static String currencySymbol() {
        return CURRENCY_SYMBOLS.getOrDefault(currencyCode, CURRENCY_SYMBOLS.get(DEFAULT_CURRENCY));
    }

    public static ResourceBundle messageBundle() {
        return bundle;
    }

    /**
     * Arayüz dilini ayarlar ve kalıcı hale getirir. {@code "auto"} verilirse tercih
     * her açılışta yeniden işletim sistemine göre tespit edilir.
     *
     * @param code {@code "auto" | "tr" | "en"}
     */
    public static void applyUiLanguage(String code) {
        uiLocale = resolveUiLocale(code);
        bundle = loadBundle(uiLocale);
        AppSettings.get().getUi().setLanguage(code);
        AppSettings.save();
    }

    /**
     * Tarih/sayı biçimi bölgesini ayarlar ve kalıcı hale getirir. {@code "auto"}
     * verilirse tercih her açılışta yeniden işletim sistemine göre tespit edilir.
     *
     * @param tag {@code "auto" | "tr-TR" | "en-US" | "en-GB" | "de-DE"}
     */
    public static void applyFormatRegion(String tag) {
        formatLocale = resolveFormatLocale(tag);
        AppSettings.get().getUi().setFormatRegion(tag);
        AppSettings.save();
    }

    /**
     * Gösterim para birimini ayarlar. Sadece sembolü değiştirir, TL bazlı
     * hesaplamayı etkilemez.
     *
     * @param code {@code "TRY" | "USD" | "EUR" | "GBP"}
     */
    public static void applyCurrencyCode(String code) {
        currencyCode = resolveCurrency(code);
        AppSettings.get().getUi().setCurrencyCode(currencyCode);
        AppSettings.save();
    }

    public static List<Locale> supportedUiLocales() {
        return SUPPORTED_UI_LOCALES;
    }

    public static List<Locale> supportedFormatLocales() {
        return SUPPORTED_FORMAT_LOCALES;
    }

    public static List<String> supportedCurrencyCodes() {
        return List.copyOf(CURRENCY_SYMBOLS.keySet());
    }

    private static Locale resolveUiLocale(String saved) {
        if (saved == null || "auto".equals(saved)) {
            String osLanguage = Locale.getDefault().getLanguage();
            return "en".equals(osLanguage) ? new Locale("en") : DEFAULT_UI_LOCALE;
        }
        return SUPPORTED_UI_LOCALES.stream()
                .filter(l -> l.getLanguage().equals(saved))
                .findFirst()
                .orElse(DEFAULT_UI_LOCALE);
    }

    private static Locale resolveFormatLocale(String saved) {
        if (saved == null || "auto".equals(saved)) {
            Locale osLocale = Locale.getDefault();
            return SUPPORTED_FORMAT_LOCALES.stream()
                    .filter(l -> l.toLanguageTag().equals(osLocale.toLanguageTag()))
                    .findFirst()
                    .or(() -> SUPPORTED_FORMAT_LOCALES.stream()
                            .filter(l -> l.getLanguage().equals(osLocale.getLanguage()))
                            .findFirst())
                    .orElse(DEFAULT_FORMAT_LOCALE);
        }
        return SUPPORTED_FORMAT_LOCALES.stream()
                .filter(l -> l.toLanguageTag().equals(saved))
                .findFirst()
                .orElse(DEFAULT_FORMAT_LOCALE);
    }

    private static String resolveCurrency(String saved) {
        return CURRENCY_SYMBOLS.containsKey(saved) ? saved : DEFAULT_CURRENCY;
    }

    private static ResourceBundle loadBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }
}
