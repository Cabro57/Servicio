package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.UserService;
import tr.cabro.servicio.util.PasswordUtil;
import tr.cabro.servicio.util.ProfileImageStore;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Kullanıcının kişisel bilgileri ve PIN'i.
 * <p>
 * İşletme bilgileri (işletme adı, telefon, adres, logo) buradan Ayarlar &gt; İşletme Bilgileri
 * ekranına taşındı — bunlar kişiye değil işletmeye ait ve belgelerde kullanılıyor.
 */
public class ProfileSettingsPanel extends JPanel {

    public static final String MODAL_ID = "profile-settings";

    private JTextField txtName;
    private JTextField txtSurname;
    private JTextField txtEmail;
    private JLabel lblPhotoName;
    private String selectedPhotoName;

    private JPasswordField txtCurrentPin;
    private JPasswordField txtNewPin;
    private JPasswordField txtNewPinConfirm;

    private User currentUser;

    public ProfileSettingsPanel() {
        setLayout(new MigLayout("fillx,wrap,insets 5 5 5 5", "[fill,430::]", ""));
        initComponents();
        loadUser();
    }

    private void initComponents() {
        // --- Profil Bilgileri ---
        JLabel sectionInfo = new JLabel("Profil Bilgileri");
        sectionInfo.putClientProperty(FlatClientProperties.STYLE, "font:bold +2;");
        add(sectionInfo, "gapy 5");

        txtName = new JTextField();
        txtSurname = new JTextField();
        txtEmail = new JTextField();

        styleField(txtName, "Adınız");
        styleField(txtSurname, "Soyadınız");
        styleField(txtEmail, "e-posta@example.com");

        JPanel twoCol = new JPanel(new MigLayout("fillx,wrap 2,insets 0", "[fill,grow][fill,grow]", ""));
        twoCol.setOpaque(false);
        twoCol.add(new JLabel("Ad:"), "gapy 8");
        twoCol.add(new JLabel("Soyad:"), "gapy 8");
        twoCol.add(txtName);
        twoCol.add(txtSurname);
        add(twoCol);

        add(new JLabel("E-posta:"), "gapy 8");
        add(txtEmail);

        // Profil fotoğrafı
        add(new JLabel("Profil Fotoğrafı:"), "gapy 8");
        lblPhotoName = new JLabel("Değiştirilmedi");
        JButton btnPhoto = new JButton("Fotoğraf Seç...");
        btnPhoto.putClientProperty(FlatClientProperties.STYLE, "arc:10;");
        JPanel photoRow = new JPanel(new MigLayout("insets 0", "[][grow,fill]"));
        photoRow.setOpaque(false);
        photoRow.add(btnPhoto);
        photoRow.add(lblPhotoName);
        add(photoRow);
        btnPhoto.addActionListener(e -> selectPhoto());

        // --- Güvenlik ---
        add(new JSeparator(), "gapy 15");

        JLabel sectionSecurity = new JLabel("Güvenlik");
        sectionSecurity.putClientProperty(FlatClientProperties.STYLE, "font:bold +2;");
        add(sectionSecurity, "gapy 5");

        JLabel hint = new JLabel("PIN alanlarını boş bırakırsanız şifreniz değişmez.");
        hint.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground;");
        add(hint);

        txtCurrentPin = new JPasswordField();
        txtNewPin = new JPasswordField();
        txtNewPinConfirm = new JPasswordField();

        stylePin(txtCurrentPin, "Mevcut PIN (6 haneli)");
        stylePin(txtNewPin, "Yeni PIN (6 haneli)");
        stylePin(txtNewPinConfirm, "Yeni PIN tekrar");

        applyPinFilter(txtCurrentPin);
        applyPinFilter(txtNewPin);
        applyPinFilter(txtNewPinConfirm);

        add(new JLabel("Mevcut PIN:"), "gapy 8");
        add(txtCurrentPin);
        add(new JLabel("Yeni PIN:"), "gapy 8");
        add(txtNewPin);
        add(new JLabel("Yeni PIN Tekrar:"), "gapy 8");
        add(txtNewPinConfirm);

        // --- Kaydet ---
        JButton btnSave = new JButton("Kaydet") {
            @Override public boolean isDefaultButton() { return true; }
        };
        btnSave.putClientProperty(FlatClientProperties.STYLE, "arc:12; margin:4,10,4,10;");
        add(btnSave, "gapy 20, align right");
        btnSave.addActionListener(e -> save(btnSave));
    }

    private void loadUser() {
        User memUser = MyDrawerBuilder.getInstance().getUser();
        if (memUser == null) return;
        Long userId = memUser.getId() != null ? memUser.getId() : 1L;

        UserService us = ServiceManager.getUserService();
        us.get(userId).thenAccept(opt -> SwingUtilities.invokeLater(() -> {
            if (opt.isEmpty()) return;
            currentUser = opt.get();
            txtName.setText(nvl(currentUser.getName()));
            txtSurname.setText(nvl(currentUser.getSurname()));
            txtEmail.setText(nvl(currentUser.getEmail()));
            selectedPhotoName = currentUser.getProfilePicture();
            if (selectedPhotoName != null && !selectedPhotoName.trim().isEmpty()) {
                lblPhotoName.setText(selectedPhotoName);
            }
        })).exceptionally(ex -> ErrorHandler.handle(this, "Kullanıcı bilgileri yüklenemedi", ex));
    }

    private void save(JButton btnSave) {
        if (currentUser == null) return;

        String curPin  = new String(txtCurrentPin.getPassword());
        String newPin  = new String(txtNewPin.getPassword());
        String newPin2 = new String(txtNewPinConfirm.getPassword());
        boolean changingPin = !curPin.isEmpty() || !newPin.isEmpty() || !newPin2.isEmpty();

        if (changingPin) {
            if (!PasswordUtil.verify(curPin, currentUser.getPassword())) {
                Toast.show(this, Toast.Type.ERROR, Messages.get("toast.pin.currentInvalid"));
                return;
            }
            if (newPin.length() != 6 || !newPin.matches("\\d+")) {
                Toast.show(this, Toast.Type.WARNING, Messages.get("toast.pin.newMustBe6Digits"));
                return;
            }
            if (!newPin.equals(newPin2)) {
                Toast.show(this, Toast.Type.ERROR, Messages.get("toast.pin.newMismatch"));
                return;
            }
        }

        if (txtEmail.getText().trim().isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.email.required"));
            return;
        }

        currentUser.setName(txtName.getText().trim());
        currentUser.setSurname(txtSurname.getText().trim());
        currentUser.setEmail(txtEmail.getText().trim());
        if (selectedPhotoName != null) currentUser.setProfilePicture(selectedPhotoName);
        if (changingPin) currentUser.setPassword(newPin);

        btnSave.setEnabled(false);
        UserService us = ServiceManager.getUserService();
        us.save(currentUser, true).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
            MyDrawerBuilder.getInstance().setUser(saved);
            Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.profile.updated"));
            AppModal.closeModal(MODAL_ID);
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> btnSave.setEnabled(true));
            return ErrorHandler.handle(this, "Profil güncellenemedi", ex);
        });
    }

    private void selectPhoto() {
        try {
            String stored = ProfileImageStore.chooseAndStore(
                    this, "Profil Fotoğrafı Seç", ProfileImageStore.PROFILES_DIR);
            if (stored != null) {
                selectedPhotoName = stored;
                lblPhotoName.setText(stored);
            }
        } catch (Exception ex) {
            Toast.show(this, Toast.Type.ERROR, Messages.get("toast.photo.copyFailed", ex.getMessage()));
        }
    }

    private void styleField(JTextField f, String placeholder) {
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        f.putClientProperty(FlatClientProperties.STYLE, "margin:4,10,4,10; arc:12;");
    }

    private void stylePin(JPasswordField f, String placeholder) {
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        f.putClientProperty(FlatClientProperties.STYLE, "margin:4,10,4,10; arc:12; showRevealButton:true;");
    }

    private void applyPinFilter(JPasswordField field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string != null && string.matches("\\d+") && fb.getDocument().getLength() + string.length() <= 6)
                    super.insertString(fb, offset, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text != null && text.matches("\\d+") && fb.getDocument().getLength() - length + text.length() <= 6)
                    super.replace(fb, offset, length, text, attrs);
            }
        });
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}