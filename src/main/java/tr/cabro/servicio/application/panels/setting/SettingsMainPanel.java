package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.Settings;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class SettingsMainPanel extends JPanel {

    private JSpinner timeoutSpinner;
    private JButton saveButton;

    public SettingsMainPanel() {
        init();
    }

    private void init() {
        initComponent();
        loadSettings();
        addListeners();
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 10, gapy 15", "[grow]", "[][][][grow][]"));

        // --- Barkod Ayarları Paneli ---
        JPanel barcode_panel = new JPanel(new MigLayout("fill, insets 10", "[][grow]", "[]"));
        barcode_panel.setBorder(BorderFactory.createTitledBorder("Barkod Ayarları"));

        barcode_panel.add(new JLabel("Barkod Öneki:"));
        JTextField prefix = new JTextField();
        barcode_panel.add(prefix, "growx");

        add(barcode_panel, "growx, wrap");

        // --- Güvenlik Ayarları Paneli (YENİ EKLENDİ) ---
        JPanel security_panel = new JPanel(new MigLayout("fill, insets 10", "[][100!][grow]", "[]"));
        security_panel.setBorder(BorderFactory.createTitledBorder("Güvenlik Ayarları"));

        security_panel.add(new JLabel("Otomatik Kilitleme Süresi (Dakika):"));

        // Varsayılan 5, Min 0 (Kapalı), Max 120, 1'er 1'er artan spinner
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 120, 1));
        timeoutSpinner.setToolTipText("0 yaparsanız otomatik kilitleme devre dışı kalır.");
        security_panel.add(timeoutSpinner, "");

        JLabel infoLabel = new JLabel("(0 = Devre Dışı)");
        infoLabel.putClientProperty(FlatClientProperties.STYLE, "foreground:$text.disabled;");
        security_panel.add(infoLabel, "wrap");

        add(security_panel, "growx, wrap");

        // --- Ekstra Ayarlar Paneli ---
        JPanel extra_panel = new JPanel(new MigLayout("fill, insets 10"));
        extra_panel.setBorder(BorderFactory.createTitledBorder("Diğer Ayarlar"));
        add(extra_panel, "growx, wrap");

        // Bileşenleri yukarı itmek için boşluk
        add(new JLabel(), "pushy, growy, wrap");

        // --- Genel Kaydet Butonu ---
        saveButton = new JButton("Ayarları Kaydet");
        saveButton.putClientProperty(FlatClientProperties.STYLE, "font:bold");
        add(saveButton, "align right");
    }

    private void loadSettings() {
        // Ayarlar dosyasından mevcut süreyi çekip arayüze basıyoruz
        Settings settings = Servicio.getSettings();
        timeoutSpinner.setValue(settings.getAutoLockTimeoutMinutes());
    }

    private void addListeners() {
        saveButton.addActionListener((ActionEvent e) -> {
            try {
                saveButton.setEnabled(false);

                int newTimeout = (Integer) timeoutSpinner.getValue();

                // 1. Okaeri Configs nesnesini güncelle ve diske kaydet
                Settings settings = Servicio.getSettings();
                settings.setAutoLockTimeoutMinutes(newTimeout);
                settings.save();

                // 2. Yeniden başlatmaya gerek kalmadan canlı süreyi sisteme uygula
                Servicio.getInactivityMonitor().setTimeout(newTimeout);

                Toast.show(this, Toast.Type.SUCCESS, "Ayarlar başarıyla kaydedildi.");
            } catch (Exception ex) {
                Toast.show(this, Toast.Type.ERROR, "Ayarlar kaydedilirken hata oluştu!");
                Servicio.getLogger().error("Ayarlar kayıt hatası:", ex);
            } finally {
                saveButton.setEnabled(true);
            }
        });
    }
}