package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.Settings;

import javax.swing.*;

public class SettingsMainPanel extends JPanel {

    private JSpinner timeoutSpinner;
    private JTextField barcodePrefix;

    public SettingsMainPanel() {
        init();
    }

    private void init() {
        initComponent();
        loadSettings();

        barcodePrefix.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveBarcodePrefix();
            }
        });

    }

    private void loadSettings() {
        // Ayarlar nesnesini alıyoruz
        Settings settings = Servicio.getSettings();

        // Eğer null değilse, mevcut prefix'i TextField içine set ediyoruz
        if (settings != null) {
            barcodePrefix.setText(settings.getBarcodePrefix());
        }
    }

    private void saveBarcodePrefix() {
        Settings settings = Servicio.getSettings();
        String newPrefix = barcodePrefix.getText().trim();

        // Yeni prefix'i set et ve ayarları kaydet
        settings.setBarcodePrefix(newPrefix);

        // Not: Ayarların kalıcı olması için diske yazan metodunuzu çağırmayı unutmayın.
        // Örn: settings.save() veya Servicio.saveSettings() gibi.
        settings.save();
    }


    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 10, gapy 15", "[grow]", "[][][][grow][]"));

        // --- Barkod Ayarları Paneli ---
        JPanel barcodePanel = new JPanel(new MigLayout("fill, insets 10", "[][grow]", "[]"));
        barcodePanel.setBorder(BorderFactory.createTitledBorder("Barkod Ayarları"));

        barcodePanel.add(new JLabel("Barkod Öneki:"));
        barcodePrefix = new JTextField();
        barcodePanel.add(barcodePrefix, "growx");

        add(barcodePanel, "growx, wrap");

//        // --- Güvenlik Ayarları Paneli (YENİ EKLENDİ) ---
//        JPanel security_panel = new JPanel(new MigLayout("fill, insets 10", "[][100!][grow]", "[]"));
//        security_panel.setBorder(BorderFactory.createTitledBorder("Güvenlik Ayarları"));
//
//        security_panel.add(new JLabel("Otomatik Kilitleme Süresi (Dakika):"));
//
//        // Varsayılan 5, Min 0 (Kapalı), Max 120, 1'er 1'er artan spinner
//        timeoutSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 120, 1));
//        timeoutSpinner.setToolTipText("0 yaparsanız otomatik kilitleme devre dışı kalır.");
//        security_panel.add(timeoutSpinner, "");
//
//        JLabel infoLabel = new JLabel("(0 = Devre Dışı)");
//        infoLabel.putClientProperty(FlatClientProperties.STYLE, "foreground:$text.disabled;");
//        security_panel.add(infoLabel, "wrap");
//
//        add(security_panel, "growx, wrap");

//        // --- Ekstra Ayarlar Paneli ---
//        JPanel extra_panel = new JPanel(new MigLayout("fill, insets 10"));
//        extra_panel.setBorder(BorderFactory.createTitledBorder("Diğer Ayarlar"));
//        add(extra_panel, "growx, wrap");

        // Bileşenleri yukarı itmek için boşluk
        add(new JLabel(), "pushy, growy, wrap");
    }
}