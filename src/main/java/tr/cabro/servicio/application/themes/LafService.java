package tr.cabro.servicio.application.themes;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.IntelliJTheme;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.util.LoggingFacade;
import tr.cabro.servicio.settings.AppConfig;
import tr.cabro.servicio.settings.AppSettings;

import javax.swing.*;
import java.awt.*;

/**
 * Look&amp;Feel / tema seçiminin yüklenmesi ve saklanması.
 * <p>
 * Eskiden bu iş {@code DemoPreferences} sınıfındaydı ve tercihleri Windows registry'ye
 * ({@code /raven-flatlaf-demo} düğümü) yazıyordu — işletim sistemine özgüydü, yedeğe girmiyordu ve
 * uygulama kaldırılsa bile geride kalıyordu. Artık tema da diğer tercihler gibi
 * {@code config.json} içinde tutuluyor ve veri klasörüyle birlikte taşınıyor.
 * Eski registry değerleri ilk açılışta {@code ConfigMigrations} tarafından buraya taşınır.
 */
public final class LafService {

    /** IntelliJ tema dosyalarının {@code lafTheme} alanındaki ön eki. */
    public static final String RESOURCE_PREFIX = "res:";

    /**
     * Aktif IntelliJ temasının {@code UIManager} içinde tutulduğu geçici anahtar.
     * FlatLaf, hangi {@code .theme.json} dosyasının yüklü olduğunu başka türlü bildirmiyor;
     * tema listesinde doğru satırı seçili göstermek için gerekiyor.
     */
    public static final String THEME_UI_KEY = "__Servicio.flatlaf.theme";

    /** Yazı boyutu farkının kabul edilen aralığı (pt). */
    public static final int MIN_FONT_DELTA = -1;
    public static final int MAX_FONT_DELTA = 2;

    /** Vurgu rengi; {@code FlatLaf.setSystemColorGetter} üzerinden temaya aktarılır. */
    public static Color accentColor;

    /** Tema varsayılan yazı tipi; yazı boyutu farkı buna eklenir. */
    private static Font baseFont;

    private LafService() {}

    /** Kayıtlı temayı uygular ve sonraki değişikliklerin kaydedilmesi için dinleyiciyi kurar. EDT'de çağrılmalıdır. */
    public static void setup() {
        AppConfig.Ui ui = AppSettings.get().getUi();

        try {
            String lafClassName = ui.getLafClassName() != null ? ui.getLafClassName() : FlatLightLaf.class.getName();

            if (ui.getAccentColor() != null) {
                accentColor = new Color(ui.getAccentColor(), true);
            }
            // Getter her zaman kurulu: renk sonradan Ayarlar'dan seçilince yeniden kurmaya gerek kalmasın.
            // null dönerse tema kendi varsayılan vurgu rengini kullanır.
            FlatLaf.setSystemColorGetter(name -> name.equals("accent") ? accentColor : null);

            if (IntelliJTheme.ThemeLaf.class.getName().equals(lafClassName)) {
                String theme = ui.getLafTheme() != null ? ui.getLafTheme() : "";
                if (theme.startsWith(RESOURCE_PREFIX)) {
                    IntelliJTheme.setup(PanelThemes.class.getResourceAsStream(
                            PanelThemes.THEMES_PACKAGE + theme.substring(RESOURCE_PREFIX.length())));
                } else {
                    FlatLightLaf.setup();
                }
                if (!theme.isEmpty()) {
                    UIManager.getLookAndFeelDefaults().put(THEME_UI_KEY, theme);
                }
            } else {
                UIManager.setLookAndFeel(lafClassName);
            }
        } catch (Exception e) {
            LoggingFacade.INSTANCE.logSevere(null, e);
            FlatLightLaf.setup();
        }

        putFontSize(ui.getFontSizeDelta());

        UIManager.addPropertyChangeListener(e -> {
            if (e.getPropertyName().equals("lookAndFeel")) {
                AppSettings.get().getUi().setLafClassName(UIManager.getLookAndFeel().getClass().getName());
                AppSettings.save();
            }
        });
    }

    /** Kullanıcı listeden bir IntelliJ teması seçtiğinde çağrılır. */
    public static void saveSelectedTheme(String resourceName) {
        AppSettings.get().getUi().setLafTheme(RESOURCE_PREFIX + resourceName);
        AppSettings.save();
    }

    /** Şu an yüklü IntelliJ temasının {@code res:...} referansı; core bir tema aktifse {@code null}. */
    public static String getActiveThemeResource() {
        return UIManager.getLookAndFeelDefaults().getString(THEME_UI_KEY);
    }

    /** Aktif tema bir IntelliJ teması mı? Bu temalar vurgu rengini kendi dosyalarında sabitler. */
    public static boolean isIntelliJThemeActive() {
        return UIManager.getLookAndFeel() instanceof IntelliJTheme.ThemeLaf;
    }

    public static void updateAccentColor(Color color) {
        accentColor = color;
        AppSettings.get().getUi().setAccentColor(color != null ? color.getRGB() : null);
        AppSettings.save();
    }

    /**
     * Çekirdek (FlatLaf) temalardan birine geçer; seçim {@code lookAndFeel} dinleyicisiyle kaydedilir.
     * EDT'de çağrılmalıdır.
     */
    public static void applyCoreTheme(String lafClassName) {
        if (lafClassName.equals(UIManager.getLookAndFeel().getClass().getName())) return;
        FlatAnimatedLafChange.showSnapshot();
        try {
            UIManager.setLookAndFeel(lafClassName);
        } catch (Exception e) {
            LoggingFacade.INSTANCE.logSevere(null, e);
        }
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    /**
     * Vurgu rengini kaydeder ve aktif temayı yeniden kurarak tüm pencerelere uygular.
     * {@code null} tema varsayılanına döner. EDT'de çağrılmalıdır.
     */
    public static void applyAccentColor(Color color) {
        updateAccentColor(color);
        FlatAnimatedLafChange.showSnapshot();
        reinstallCurrentLaf();
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    /**
     * Arayüz yazı boyutunu tema varsayılanına göre {@code delta} pt büyütür/küçültür, kaydeder ve
     * tüm pencerelere uygular. EDT'de çağrılmalıdır.
     */
    public static void applyFontSize(int delta) {
        delta = Math.max(MIN_FONT_DELTA, Math.min(MAX_FONT_DELTA, delta));
        AppSettings.get().getUi().setFontSizeDelta(delta);
        AppSettings.save();

        FlatAnimatedLafChange.showSnapshot();
        putFontSize(delta);
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    /**
     * {@code defaultFont} geliştirici varsayılanını ayarlar. Bu katman tema değişiminde korunur,
     * bu yüzden seçilen boyut yeni temaya da taşınır.
     */
    private static void putFontSize(int delta) {
        // Taban yazı tipi ilk çağrıda, henüz geçersiz kılma yokken alınır; sonraki tema kurulumları
        // geliştirici değerini temel alabileceği için tekrar okunmaz (boyut üst üste binmesin).
        if (baseFont == null) baseFont = UIManager.getLookAndFeelDefaults().getFont("defaultFont");
        UIManager.put("defaultFont", null);
        if (delta == 0) return;
        Font base = baseFont;
        if (base != null) {
            UIManager.put("defaultFont", base.deriveFont(base.getSize2D() + Math.max(MIN_FONT_DELTA, Math.min(MAX_FONT_DELTA, delta))));
        }
    }

    /** Vurgu rengi temanın yüklenmesi sırasında okunduğu için aynı temayı baştan kurar. */
    private static void reinstallCurrentLaf() {
        LookAndFeel current = UIManager.getLookAndFeel();
        String theme = getActiveThemeResource();
        try {
            if (current instanceof IntelliJTheme.ThemeLaf && theme != null && theme.startsWith(RESOURCE_PREFIX)) {
                IntelliJTheme.setup(PanelThemes.class.getResourceAsStream(
                        PanelThemes.THEMES_PACKAGE + theme.substring(RESOURCE_PREFIX.length())));
                UIManager.getLookAndFeelDefaults().put(THEME_UI_KEY, theme);
            } else {
                UIManager.setLookAndFeel(current.getClass().getName());
            }
        } catch (Exception e) {
            LoggingFacade.INSTANCE.logSevere(null, e);
        }
    }
}
