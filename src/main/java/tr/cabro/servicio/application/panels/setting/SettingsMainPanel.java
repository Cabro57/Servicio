package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.Settings;
import tr.cabro.servicio.settings.Theme;
import tr.cabro.servicio.util.barcode.BarcodeConfig;

import javax.swing.*;
import java.util.Set;

public class SettingsMainPanel extends JPanel {

    public SettingsMainPanel() {
        init();
    }

    private void init() {
        initComponent();
        initBarcode();
        initPin();

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

    private void initBarcode() {
        BarcodeConfig config = Servicio.getSettings().getBarcode();

        prefix.setText(config.getPrefix());
        length.setValue(config.getNumberLength());
        separator.setText(config.getSeparator());

        useDate.setSelected(config.isUseDate());
        useDate.addActionListener(e -> {
            if (useDate.isSelected()) {
                dateFormatter.setText(config.getDateFormatter());
                dateFormatter.setEditable(true);
            } else {
                dateFormatter.setEditable(false);
            }
        });

        if (!useDate.isSelected()) {
            dateFormatter.setEditable(false);
        }

        barcodeUpdate.addActionListener(e -> {
            config.setPrefix(prefix.getText());
            config.setNumberLength((Integer) length.getValue());
            config.setSeparator(separator.getText());

            config.setUseDate(useDate.isSelected());
            config.setDateFormatter(dateFormatter.getText());

            Servicio.getSettings().setBarcode(config);
            Servicio.getSettings().save();

            Toast.show(this, Toast.Type.SUCCESS, "Başarılı şekilde barkod ayarları güncellendi.");
        });

    }

    private void initPin() {
        Settings.PinConfig config = Servicio.getSettings().getPinConfig();

        pinField.setValue(config.getPin());
        timeoutSpinner.setValue(config.getTimeout());

        pinUpdate.addActionListener(e -> {
            config.setPin((Integer) pinField.getValue());
            config.setTimeout((Integer) timeoutSpinner.getValue());

            Servicio.getSettings().setPinConfig(config);
            Servicio.getSettings().save();

            Servicio.getLogger().info("Pin ayarları güncellendi.");
            Toast.show(this, Toast.Type.SUCCESS, "Pin ayarları güncellendi");
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

        JPanel barcode_panel = new JPanel(new MigLayout("fill,insets 5", "[fill][fill][fill]", "[][][]"));
        barcode_panel.setBorder(BorderFactory.createTitledBorder("Barkod Ayarları"));

        prefix = new JTextField();
        length = new JSpinner();
        separator = new JTextField();

        useDate = new JCheckBox("Tarih Kullanılsın mı?");
        dateFormatter = new JTextField();

        barcodeUpdate = new JButton("Güncelle");

        barcode_panel.add(prefix, "grow, push");
        barcode_panel.add(length, "push");
        barcode_panel.add(separator, "wrap, push");
        barcode_panel.add(dateFormatter, "span 3, split 2");
        barcode_panel.add(useDate, "wrap");
        barcode_panel.add(barcodeUpdate,"span 3, grow");

        add(barcode_panel, "growx, wrap");

        JPanel pin_panel = new JPanel(new MigLayout("fill,insets 5", "[fill][fill][fill]", "[][][]"));
        pin_panel.setBorder(BorderFactory.createTitledBorder("Pin Ayarları"));

        pinField = new JFormattedTextField();

        timeoutSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 60, 1));

        pinUpdate = new JButton("Pin Güncelle");

        pin_panel.add(new JLabel("Pin: "));
        pin_panel.add(pinField, "grow, push");
        pin_panel.add(new JLabel("Süre: "));
        pin_panel.add(timeoutSpinner, "grow, push, wrap");
        pin_panel.add(pinUpdate, "growx, pushx, span 4");

        add(pin_panel, "growx, wrap");

        JPanel extra_panel = new JPanel(new MigLayout("fill,insets 5"));
        add(extra_panel, "growx, wrap");

        add(new JLabel(), "pushy, growy");
    }

    private JTextField prefix;
    private JSpinner length;
    private JTextField separator;
    private JCheckBox useDate;
    private JTextField dateFormatter;
    private JButton barcodeUpdate;
    private JButton pinUpdate;
    private JSpinner timeoutSpinner;
    private JFormattedTextField pinField;
    private JComboBox<String> theme_combo;
}
