package tr.cabro.servicio.application.panels;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.Theme;

import javax.swing.*;
import java.util.Set;

public class SettingsMainPanel extends JPanel {
    private JComboBox<String> theme_combo;
    private JPanel main_panel;
    private JPanel theme_panel;

    public SettingsMainPanel() {
        init();

        add(main_panel);
    }

    private void init() {
        loadThemes();
    }

    private void loadThemes() {
        Set<String> themes =  Servicio.getSettings().getTemplate().getThemes().keySet();

        for (String theme : themes) {
            theme_combo.addItem(theme);
        }

        String selectedTheme = Servicio.getSettings().getTemplate().getSelected_theme();
        if (selectedTheme != null) {
            theme_combo.setSelectedItem(selectedTheme);
        }

        theme_combo.addActionListener(e -> {
            String selected_theme = (String) theme_combo.getSelectedItem();
            if (selected_theme != null && !selected_theme.equals(Servicio.getSettings().getTemplate().getSelected_theme())) {
                Theme.apply(selected_theme);
                Servicio.getSettings().getTemplate().setSelected_theme(selected_theme);
            }
        });
    }
}
