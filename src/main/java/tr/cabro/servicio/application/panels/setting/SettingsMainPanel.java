package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.Theme;

import javax.swing.*;
import java.util.Set;

public class SettingsMainPanel extends JPanel {
    private JComboBox<String> theme_combo;
    private JPanel theme_panel;

    public SettingsMainPanel() {
        init();
    }

    private void init() {
        initComponent();

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

    private void initComponent() {
        setLayout(new MigLayout("fillx,insets 5,gapy 10", "[grow]", "[][][][grow]"));

        JPanel theme_panel = new JPanel(new MigLayout("fillx,insets 5", "[grow]", "[]"));
        theme_panel.setBorder(BorderFactory.createTitledBorder("Tema"));

        theme_combo = new JComboBox<>();
        theme_panel.add(theme_combo, "growx");

        add(theme_panel, "growx, wrap");

        JPanel message_panel = new JPanel(new MigLayout("fill,insets 5"));
        message_panel.setBorder(BorderFactory.createTitledBorder("Mesaj Şablonları"));

        add(message_panel, "growx, wrap");

        JPanel extra_panel = new JPanel(new MigLayout("fill,insets 5"));
        add(extra_panel, "growx, wrap");

        add(new JLabel(), "pushy,growy");
    }
}
