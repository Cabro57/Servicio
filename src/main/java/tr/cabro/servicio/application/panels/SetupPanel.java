package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.MainUI;
import tr.cabro.servicio.application.component.AmbientLights;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.ProfileImageStore;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * İlk kurulum ekranı: kilit ekranıyla aynı dil. Zeminde vurgu renginden türeyen sabit ışıklar,
 * ekranın tam ortasında standart kart (listCard), en altta sürüm ve Çıkış.
 * <p>
 * Kartta iki bölüm: işletme (ad, e-posta, profil fotoğrafı) ve kilit PIN'i (6 hane, tekrar).
 * Hatalar toast yerine ilgili alanın altında gösterilir; Enter kurulumu tamamlar.
 */
public class SetupPanel extends Form {

    private static final int PIN_LENGTH = 6;

    private final AmbientLights lights = new AmbientLights();

    private final JTextField txtCompanyName = new JTextField();
    private final JTextField txtEmail = new JTextField();
    private final JPasswordField txtPin = new JPasswordField();
    private final JPasswordField txtPinConfirm = new JPasswordField();
    private final JLabel companyError = FormKit.errorLabel();
    private final JLabel emailError = FormKit.errorLabel();
    private final JLabel pinError = FormKit.errorLabel();
    private final JLabel pinConfirmError = FormKit.errorLabel();
    private final JLabel photoName = DetailKit.small("Varsayılan avatar");
    private final JButton btnSave = DetailKit.primaryButton("Kurulumu tamamla", "icons/circle-check.svg", null);

    private String selectedPhotoName = "avatar_male.svg"; // Varsayılan

    public SetupPanel() {
        setLayout(new MigLayout("fill, insets 22 28 16 28, hidemode 3", "[grow, fill]", "[grow]"));
        add(footer(), "dock south");
        // Kart üst ve alt satırdan bağımsız olarak ekranın tam ortasında durur.
        add(card(), "pos 0.5al 0.5al");
    }

    // ------------------------------------------------------------------ yerleşim

    private JComponent card() {
        JPanel p = new JPanel(new MigLayout("insets 26 34 28 34, wrap, fillx, gap 0, hidemode 3, width 420!", "[grow, fill]", ""));
        p.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        JLabel title = new JLabel("Servicio'ya hoş geldiniz");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +6");
        p.add(title);
        JTextArea lead = FormKit.hint("Kurulum bir dakika sürer. Bilgileri daha sonra Ayarlar > İşletme Bilgileri'nden değiştirebilirsiniz.");
        lead.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; background: null; margin: 0,0,0,0");
        p.add(lead, "gaptop 4, wmin 0");

        p.add(new JSeparator(), "gaptop 18, gapbottom 16");

        p.add(DetailKit.title("İşletme"));
        txtCompanyName.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Örn. Sıla Elektronik");
        p.add(FormKit.cell("İşletme adı", txtCompanyName, companyError), "gaptop 10");
        p.add(FormKit.note("Belgelerde, fişlerde ve kilit ekranında görünür."), "gaptop 3");
        txtEmail.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "isletme@ornek.com");
        p.add(FormKit.cell("E-posta (isteğe bağlı)", txtEmail, emailError), "gaptop 12");
        p.add(photoRow(), "gaptop 12");

        p.add(new JSeparator(), "gaptop 18, gapbottom 16");

        p.add(DetailKit.title("Kilit PIN'i"));
        JPanel pins = FormKit.grid(2);
        styleCode(txtPin, "6 hane");
        styleCode(txtPinConfirm, "Tekrar");
        pins.add(FormKit.cell("PIN", txtPin, pinError));
        pins.add(FormKit.cell("PIN tekrar", txtPinConfirm, pinConfirmError));
        p.add(pins, "gaptop 10");
        p.add(FormKit.note("Uygulamayı açarken ve ekran kilitlenince bu PIN sorulur."), "gaptop 6");

        btnSave.addActionListener((ActionEvent e) -> save());
        p.add(btnSave, "gaptop 22");

        // Enter: kurulumu tamamla.
        p.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "save");
        p.getActionMap().put("save", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (btnSave.isEnabled()) save(); }
        });
        return p;
    }

    private JComponent photoRow() {
        JPanel row = new JPanel(new MigLayout("insets 0, fillx, gap 10", "[][grow]", "[center]"));
        row.setOpaque(false);
        JButton choose = DetailKit.secondaryButton("Profil fotoğrafı seç…", "icons/user.svg", null);
        choose.addActionListener(e -> choosePhoto());
        row.add(choose);
        row.add(photoName, "wmin 0");
        return row;
    }

    private JComponent footer() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[grow][][]", "[center]"));
        p.setOpaque(false);
        p.add(new JLabel());
        JLabel version = new JLabel("v" + Servicio.getInstance().getAppVersion());
        version.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        p.add(version);
        JButton exit = new JButton("Çıkış", new Ikon("icons/x.svg", 14, "Label.disabledForeground"));
        exit.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        exit.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground; arc: 10; margin: 4,8,4,8");
        exit.setToolTipText("Uygulamayı kapat");
        exit.setFocusable(false);
        exit.addActionListener(e -> {
            if (SwingUtilities.getWindowAncestor(this) instanceof MainUI ui) ui.attemptExit();
        });
        p.add(exit);
        return p;
    }

    private void styleCode(JPasswordField field, String placeholder) {
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true; showCapsLock: false");
        applyPinFilter(field);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(txtCompanyName::requestFocusInWindow);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        lights.paint((Graphics2D) g, getWidth(), getHeight());
    }

    // ------------------------------------------------------------------ eylemler

    private void choosePhoto() {
        try {
            String stored = ProfileImageStore.chooseAndStore(this, "Profil Fotoğrafı Seç", ProfileImageStore.PROFILES_DIR);
            if (stored != null) {
                selectedPhotoName = stored;
                photoName.setText(stored);
                photoName.setToolTipText(stored);
            }
        } catch (Exception ex) {
            Toasts.show(this, Toast.Type.ERROR, Messages.get("toast.photo.copyFailed", ex.getMessage()));
        }
    }

    private void save() {
        FormKit.clear(txtCompanyName, companyError);
        FormKit.clear(txtEmail, emailError);
        FormKit.clear(txtPin, pinError);
        FormKit.clear(txtPinConfirm, pinConfirmError);

        String company = txtCompanyName.getText().trim();
        String email = txtEmail.getText().trim();
        String pin = new String(txtPin.getPassword());
        String pinConfirm = new String(txtPinConfirm.getPassword());

        JComponent first = null;
        if (company.isEmpty()) {
            first = FormKit.fail(txtCompanyName, companyError, "İşletme adını yazın.");
        }
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            JComponent f = FormKit.fail(txtEmail, emailError, "Geçerli bir e-posta yazın ya da boş bırakın.");
            if (first == null) first = f;
        }
        if (pin.length() != PIN_LENGTH) {
            JComponent f = FormKit.fail(txtPin, pinError, PIN_LENGTH + " rakam olmalı.");
            if (first == null) first = f;
        } else if (!pin.equals(pinConfirm)) {
            JComponent f = FormKit.fail(txtPinConfirm, pinConfirmError, "PIN'ler eşleşmiyor.");
            if (first == null) first = f;
        }
        if (first != null) {
            first.requestFocusInWindow();
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("Kaydediliyor…");
        User newUser = new User("", "", email, pin, company, "", selectedPhotoName);
        ServiceManager.getUserService().save(newUser, false).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
            Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.setup.completed"));
            // Doğrudan sisteme al ve inaktif monitörü başlat
            FormManager.login();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                btnSave.setEnabled(true);
                btnSave.setText("Kurulumu tamamla");
            });
            return ErrorHandler.handle(this, "Kurulum kaydı oluşturulamadı", ex);
        });
    }

    /** Yalnızca rakam ve en çok 6 hane. */
    private void applyPinFilter(JPasswordField field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string == null) return;
                if (string.matches("\\d+") && (fb.getDocument().getLength() + string.length() <= PIN_LENGTH)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text == null) return;
                if (text.isEmpty() || text.matches("\\d+") && (fb.getDocument().getLength() - length + text.length() <= PIN_LENGTH)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
    }
}
