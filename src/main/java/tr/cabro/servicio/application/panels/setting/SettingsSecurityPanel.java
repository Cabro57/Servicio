package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;

/**
 * Ayarlar &gt; Sistem &gt; Güvenlik.
 * <p>
 * İkisi de işletme politikası olduğu için veritabanında ({@code app_settings}) tutulur.
 * Otomatik kilit süresi değiştiğinde {@code InactivityMonitor}'a anında uygulanır —
 * eskiden bu değer yalnızca uygulama açılışında okunuyordu ve arayüzde hiç görünmüyordu.
 */
public class SettingsSecurityPanel extends JPanel {

    private JSpinner autoLockSpinner;
    private JSpinner deviceAccessPurgeSpinner;

    public SettingsSecurityPanel() {
        initComponent();
        loadSettings();

        autoLockSpinner.addChangeListener(e -> saveAutoLockMinutes());
        deviceAccessPurgeSpinner.addChangeListener(e -> saveDeviceAccessPurgeHours());
    }

    private void loadSettings() {
        autoLockSpinner.setValue(ServiceManager.getAppSettingService().getAutoLockMinutes());
        deviceAccessPurgeSpinner.setValue(ServiceManager.getAppSettingService().getDeviceAccessPurgeHours());
    }

    private void saveAutoLockMinutes() {
        int minutes = (Integer) autoLockSpinner.getValue();
        ServiceManager.getAppSettingService().setAutoLockMinutes(minutes);

        // Çalışan izleyiciye anında uygula — yeniden başlatma gerekmesin
        if (Servicio.getInactivityMonitor() != null) {
            Servicio.getInactivityMonitor().setTimeout(minutes);
        }
    }

    private void saveDeviceAccessPurgeHours() {
        ServiceManager.getAppSettingService()
                .setDeviceAccessPurgeHours((Integer) deviceAccessPurgeSpinner.getValue());
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 10, gapy 15", "[grow]", "[][][grow]"));

        // --- Ekran Kilidi ---
        JPanel lockPanel = new JPanel(new MigLayout("fill, insets 10", "[][100!][grow]", "[]"));
        lockPanel.setBorder(BorderFactory.createTitledBorder("Ekran Kilidi"));

        lockPanel.add(new JLabel("Otomatik Kilitleme Süresi (Dakika):"));
        autoLockSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 120, 1));
        autoLockSpinner.setToolTipText("0 yaparsanız otomatik kilitleme devre dışı kalır.");
        lockPanel.add(autoLockSpinner);

        JLabel info = new JLabel("(0 = Devre Dışı)");
        info.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground;");
        lockPanel.add(info, "wrap");

        add(lockPanel, "growx, wrap");

        // --- Cihaz Erişim Güvenliği ---
        JPanel deviceAccessPanel = new JPanel(new MigLayout("fill, insets 10", "[][100!][grow]", "[]"));
        deviceAccessPanel.setBorder(BorderFactory.createTitledBorder("Cihaz Erişim Güvenliği"));

        deviceAccessPanel.add(new JLabel("Teslimattan Sonra Erişim Kodunu Sil (Saat):"));
        deviceAccessPurgeSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 720, 1));
        deviceAccessPurgeSpinner.setToolTipText(
                "Servis teslim edildikten bu kadar saat sonra, cihazın PIN/şifre/desen bilgisi kalıcı olarak silinir.");
        deviceAccessPanel.add(deviceAccessPurgeSpinner);

        add(deviceAccessPanel, "growx, wrap");

        add(new JLabel(), "pushy, growy, wrap");
    }
}
