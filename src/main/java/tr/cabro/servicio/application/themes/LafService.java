package tr.cabro.servicio.application.themes;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.IntelliJTheme;
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

    /** Vurgu rengi; {@code FlatLaf.setSystemColorGetter} üzerinden temaya aktarılır. */
    public static Color accentColor;

    private LafService() {}

    /** Kayıtlı temayı uygular ve sonraki değişikliklerin kaydedilmesi için dinleyiciyi kurar. EDT'de çağrılmalıdır. */
    public static void setup() {
        AppConfig.Ui ui = AppSettings.get().getUi();

        try {
            String lafClassName = ui.getLafClassName() != null ? ui.getLafClassName() : FlatLightLaf.class.getName();

            if (ui.getAccentColor() != null) {
                accentColor = new Color(ui.getAccentColor(), true);
                FlatLaf.setSystemColorGetter(name -> name.equals("accent") ? accentColor : null);
            }

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

    public static void updateAccentColor(Color color) {
        accentColor = color;
        AppSettings.get().getUi().setAccentColor(color != null ? color.getRGB() : null);
        AppSettings.save();
    }
}
