package tr.cabro.servicio.settings;

import tr.cabro.servicio.Servicio;

import javax.swing.*;
import java.awt.*;

public class Theme {

    public static void apply(String theme) {
        try {
            String themeClass = Servicio.getSettings().getTemplate().getThemes().get(theme);

            // Sistem teması kontrolü
            if (themeClass != null && themeClass.equals("UIManager.getSystemLookAndFeelClassName")) {
                themeClass = UIManager.getSystemLookAndFeelClassName();
            } else if (themeClass == null) {
                System.out.println("Geçersiz tema: " + theme);
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                return;
            }

            UIManager.setLookAndFeel(themeClass);

            // Swing bileşenlerini güncelle
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
                window.repaint();
            }

            System.out.println("[Themes] INFO | Tema başarıyla uygulandı: " + theme);
        } catch (Exception e) {
            System.err.println("[Themes] WARN | Tema uygulanamadı: " + theme + "\n" + e);
        }
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
