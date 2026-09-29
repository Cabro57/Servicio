package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Ayarlar &gt; Sistem &gt; Güvenlik: ekran kilidi, cihaz erişim bilgisi ve kilit PIN'inin
 * değiştirilmesi (eskiden Profil penceresindeydi; PIN kişiye değil kilide ait).
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

    private final JPasswordField txtCurrentPin = new JPasswordField();
    private final JPasswordField txtNewPin = new JPasswordField();
    private final JPasswordField txtNewPinRepeat = new JPasswordField();
    private final JLabel currentPinError = FormKit.errorLabel();
    private final JLabel newPinError = FormKit.errorLabel();
    private final JLabel repeatPinError = FormKit.errorLabel();
    private final JButton btnChangePin = new JButton("PIN'i değiştir", new Ikon("icons/lock.svg", 16, "Label.foreground"));

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

        // --- Kilit PIN'i ---
        SettingsKit.section(page, "Kilit PIN'i", "Açılışta ve kilit ekranında istenen 6 haneli şifre.", createPinForm());

        return page;
    }

    /**
     * PIN değiştirme: mevcut PIN, yeni PIN ve tekrarı. Diğer ayarlar anında kaydedilir ama bu üç
     * alan birlikte anlam taşıdığı için kendi düğmesiyle kaydedilir. Hatalar ilgili alanın altında
     * yazılır; başarılıysa alanlar temizlenir ve başlıkta "Kaydedildi" yanar.
     */
    private JPanel createPinForm() {
        JPanel form = FormKit.grid(1);
        form.add(FormKit.cell("Mevcut PIN", txtCurrentPin, currentPinError), SettingsKit.FIELD);
        JPanel pair = FormKit.grid(2);
        pair.add(FormKit.cell("Yeni PIN", txtNewPin, newPinError));
        pair.add(FormKit.cell("Yeni PIN tekrar", txtNewPinRepeat, repeatPinError));
        form.add(pair, SettingsKit.FIELD);

        btnChangePin.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12; iconTextGap: 6");
        btnChangePin.setEnabled(false);
        btnChangePin.addActionListener(e -> changePin());
        form.add(btnChangePin, "gaptop 4, growx 0");

        for (JPasswordField f : new JPasswordField[]{txtCurrentPin, txtNewPin, txtNewPinRepeat}) {
            f.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
            f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "6 hane");
            ((AbstractDocument) f.getDocument()).setDocumentFilter(new DigitsFilter(6));
            f.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updatePinButton(); }
                @Override public void removeUpdate(DocumentEvent e) { updatePinButton(); }
                @Override public void changedUpdate(DocumentEvent e) { }
            });
            f.addActionListener(e -> {
                if (btnChangePin.isEnabled()) changePin();
            });
        }
        return form;
    }

    private void updatePinButton() {
        btnChangePin.setEnabled(txtCurrentPin.getPassword().length > 0
                && txtNewPin.getPassword().length > 0 && txtNewPinRepeat.getPassword().length > 0);
    }

    private void changePin() {
        String current = new String(txtCurrentPin.getPassword());
        String next = new String(txtNewPin.getPassword());
        String repeat = new String(txtNewPinRepeat.getPassword());
        FormKit.clear(txtCurrentPin, currentPinError);
        FormKit.clear(txtNewPin, newPinError);
        FormKit.clear(txtNewPinRepeat, repeatPinError);

        if (!next.matches("\\d{6}")) {
            FormKit.fail(txtNewPin, newPinError, "PIN 6 haneli olmalı.").requestFocusInWindow();
            return;
        }
        if (!next.equals(repeat)) {
            FormKit.fail(txtNewPinRepeat, repeatPinError, "İki PIN aynı değil.").requestFocusInWindow();
            return;
        }
        if (next.equals(current)) {
            FormKit.fail(txtNewPin, newPinError, "Yeni PIN eskisiyle aynı.").requestFocusInWindow();
            return;
        }

        btnChangePin.setEnabled(false);
        User mem = MyDrawerBuilder.getInstance().getUser();
        Long id = mem != null && mem.getId() != null ? mem.getId() : 1L;
        ServiceManager.getUserService().changePin(id, current, next).thenAccept(ok -> SwingUtilities.invokeLater(() -> {
            if (!ok) {
                FormKit.fail(txtCurrentPin, currentPinError, "Mevcut PIN yanlış.").requestFocusInWindow();
                updatePinButton();
                return;
            }
            txtCurrentPin.setText("");
            txtNewPin.setText("");
            txtNewPinRepeat.setText("");
            SettingsKit.saved(this);
            Toasts.show(this, Toast.Type.SUCCESS, "PIN değiştirildi. Kilit ekranında yeni PIN'i girin.");
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(this::updatePinButton);
            return ErrorHandler.handle(this, "PIN değiştirilemedi", ex);
        });
    }

    /** Yalnızca rakam ve en fazla {@code max} hane kabul eder. */
    private static final class DigitsFilter extends DocumentFilter {
        private final int max;

        DigitsFilter(int max) {
            this.max = max;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
            if (text != null && text.matches("\\d+") && fb.getDocument().getLength() + text.length() <= max) {
                super.insertString(fb, offset, text, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text == null || text.isEmpty()) {
                super.replace(fb, offset, length, text, attrs);
            } else if (text.matches("\\d+") && fb.getDocument().getLength() - length + text.length() <= max) {
                super.replace(fb, offset, length, text, attrs);
            }
        }
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
