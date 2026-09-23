package tr.cabro.servicio.application.panels.setting;

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
 * <p>
 * Veritabanında "0 dakika = kilit kapalı" olarak saklanır; arayüzde bu ayrı bir onay kutusudur,
 * kapalıyken son seçilen süre korunur ki tekrar açıldığında aynı değere dönülsün.
 */
public class SettingsSecurityPanel extends JPanel {

    private static final int DEFAULT_LOCK_MINUTES = 5;

    private JCheckBox autoLockEnabled;
    private JSpinner autoLockSpinner;
    private JSpinner deviceAccessPurgeSpinner;

    public SettingsSecurityPanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());
        loadSettings();

        autoLockEnabled.addActionListener(e -> {
            autoLockSpinner.setEnabled(autoLockEnabled.isSelected());
            saveAutoLockMinutes();
        });
        autoLockSpinner.addChangeListener(e -> saveAutoLockMinutes());
        deviceAccessPurgeSpinner.addChangeListener(e -> {
            ServiceManager.getAppSettingService()
                    .setDeviceAccessPurgeHours((Integer) deviceAccessPurgeSpinner.getValue());
            SettingsKit.saved(this);
        });
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        // --- Ekran kilidi ---
        autoLockEnabled = new JCheckBox("Boşta kalınca ekranı kilitle");
        autoLockSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_LOCK_MINUTES, 1, 120, 1));

        JPanel lock = SettingsKit.stack();
        lock.add(SettingsKit.check(autoLockEnabled,
                "Tezgâhtan uzaklaştığınızda müşteri ekrandaki bilgileri göremesin. Açık pencere, PIN girilince kaldığı yerden devam eder."));
        JPanel after = new JPanel(new MigLayout("insets 0, gap 8", "[][70!][]", "[center]"));
        after.setOpaque(false);
        after.add(SettingsKit.label("Bekleme süresi"));
        after.add(autoLockSpinner, "growx");
        after.add(SettingsKit.note("dakika"));
        lock.add(after, "gaptop 4");
        SettingsKit.section(page, "Ekran kilidi", "Uygulama bir süre kullanılmayınca PIN istenir.", lock);

        // --- Cihaz erişim bilgisi ---
        deviceAccessPurgeSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 720, 1));
        JPanel purge = SettingsKit.stack();
        JPanel purgeRow = new JPanel(new MigLayout("insets 0, gap 8", "[][70!][]", "[center]"));
        purgeRow.setOpaque(false);
        purgeRow.add(SettingsKit.label("Teslimden"));
        purgeRow.add(deviceAccessPurgeSpinner, "growx");
        purgeRow.add(SettingsKit.label("saat sonra sil"));
        purge.add(purgeRow);
        purge.add(SettingsKit.wrappingNote("Servis teslim edildikten sonra cihazın PIN, şifre ve desen bilgisi bu süre "
                + "dolunca kalıcı olarak silinir. Garanti dönüşü için kısa bir süre tutmak işinize yarayabilir."), "wmin 0, wmax 420");
        SettingsKit.section(page, "Cihaz erişim bilgisi", "Müşterinin cihaz şifresi gereğinden uzun saklanmasın.", purge);

        return page;
    }

    private void loadSettings() {
        int minutes = ServiceManager.getAppSettingService().getAutoLockMinutes();
        autoLockEnabled.setSelected(minutes > 0);
        autoLockSpinner.setValue(minutes > 0 ? Math.min(minutes, 120) : DEFAULT_LOCK_MINUTES);
        autoLockSpinner.setEnabled(minutes > 0);
        deviceAccessPurgeSpinner.setValue(ServiceManager.getAppSettingService().getDeviceAccessPurgeHours());
    }

    private void saveAutoLockMinutes() {
        int minutes = autoLockEnabled.isSelected() ? (Integer) autoLockSpinner.getValue() : 0;
        ServiceManager.getAppSettingService().setAutoLockMinutes(minutes);

        // Çalışan izleyiciye anında uygula — yeniden başlatma gerekmesin
        if (Servicio.getInactivityMonitor() != null) {
            Servicio.getInactivityMonitor().setTimeout(minutes);
        }
        SettingsKit.saved(this);
    }
}
