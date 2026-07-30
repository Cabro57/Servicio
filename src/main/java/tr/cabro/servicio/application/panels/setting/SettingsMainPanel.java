package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.AppSettings;

import javax.swing.*;

/**
 * Ayarlar &gt; Uygulama &gt; Genel.
 * <p>
 * Barkod öneki bir işletme ayarı olduğu için veritabanında ({@code app_settings}),
 * çıkış onayı tercihi makineye özel olduğu için {@code config.json} içinde tutulur.
 */
public class SettingsMainPanel extends JPanel {

    private JTextField barcodePrefix;
    private JCheckBox skipExitConfirmation;

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

        skipExitConfirmation.addActionListener(e -> {
            AppSettings.get().getUi().setSkipExitConfirmation(skipExitConfirmation.isSelected());
            AppSettings.save();
        });
    }

    private void loadSettings() {
        barcodePrefix.setText(ServiceManager.getAppSettingService().getBarcodePrefix());
        skipExitConfirmation.setSelected(AppSettings.get().getUi().isSkipExitConfirmation());
    }

    private void saveBarcodePrefix() {
        ServiceManager.getAppSettingService().setBarcodePrefix(barcodePrefix.getText().trim());
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 10, gapy 15", "[grow]", "[][][grow]"));

        // --- Barkod Ayarları ---
        JPanel barcodePanel = new JPanel(new MigLayout("fill, insets 10", "[][grow]", "[]"));
        barcodePanel.setBorder(BorderFactory.createTitledBorder("Barkod Ayarları"));

        barcodePanel.add(new JLabel("Barkod Öneki:"));
        barcodePrefix = new JTextField();
        barcodePrefix.setToolTipText("Üretilen barkod numaralarının başına eklenir.");
        barcodePanel.add(barcodePrefix, "growx");

        add(barcodePanel, "growx, wrap");

        // --- Uygulama Davranışı ---
        JPanel behaviourPanel = new JPanel(new MigLayout("fill, insets 10", "[grow]", "[]"));
        behaviourPanel.setBorder(BorderFactory.createTitledBorder("Uygulama Davranışı"));

        skipExitConfirmation = new JCheckBox("Çıkarken onay sorma");
        skipExitConfirmation.setToolTipText("İşaretliyse uygulama kapatılırken \"emin misiniz?\" sorusu gösterilmez.");
        behaviourPanel.add(skipExitConfirmation, "growx");

        add(behaviourPanel, "growx, wrap");

        // Bileşenleri yukarı itmek için boşluk
        add(new JLabel(), "pushy, growy, wrap");
    }
}
