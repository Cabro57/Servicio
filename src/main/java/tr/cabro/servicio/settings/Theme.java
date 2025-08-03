package tr.cabro.servicio.settings;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import tr.cabro.servicio.Servicio;

import javax.swing.*;

public class Theme {

    private static final Settings settings = Servicio.getSettings();

    public static void apply(String theme) {
        if (theme.equals(UIManager.getLookAndFeel().getClass().getName())) {
            return;
        }
        FlatAnimatedLafChange.showSnapshot();
        try {
            UIManager.setLookAndFeel(settings.getTemplate().getThemes().get(theme));
        } catch (Exception ex) {
            Servicio.getInstance().getLogger().severe(ex.toString());
        }

        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();

        Servicio.getInstance().getLogger().severe("[Themes] INFO | Tema başarıyla uygulandı: " + theme);
    }

    public static String selected() {
        // Manager sınıfından seçili temayı al
        String selectedTheme = Servicio.getSettings().getTemplate().getSelected_theme();

        // Eğer seçili tema yoksa, varsayılan olarak "Light" temasını döndür
        if (selectedTheme == null || selectedTheme.isEmpty()) {
            return "Light";  // Varsayılan tema
        }

        return selectedTheme;
    }
}
