package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemFileChooser;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.UserService;
import tr.cabro.servicio.util.PasswordUtil;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ProfileSettingsPanel extends JPanel {

    public static final String MODAL_ID = "profile-settings";

    private JTextField txtName;
    private JTextField txtSurname;
    private JTextField txtEmail;
    private JTextField txtBusinessName;
    private JTextField txtPhone;
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
        txtBusinessName = new JTextField();
        txtPhone = new JTextField();

        styleField(txtName, "Adınız");
        styleField(txtSurname, "Soyadınız");
        styleField(txtEmail, "e-posta@example.com");
        styleField(txtBusinessName, "İşletme adı");
        styleField(txtPhone, "05xx xxx xx xx");

        JPanel twoCol = new JPanel(new MigLayout("fillx,wrap 2,insets 0", "[fill,grow][fill,grow]", ""));
        twoCol.setOpaque(false);
        twoCol.add(new JLabel("Ad:"), "gapy 8");
        twoCol.add(new JLabel("Soyad:"), "gapy 8");
        twoCol.add(txtName);
        twoCol.add(txtSurname);
        add(twoCol);

        add(new JLabel("E-posta:"), "gapy 8");
        add(txtEmail);
        add(new JLabel("Şirket / İşletme Adı:"), "gapy 8");
        add(txtBusinessName);
        add(new JLabel("Telefon:"), "gapy 8");
        add(txtPhone);

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
            if (!opt.isPresent()) return;
            currentUser = opt.get();
            txtName.setText(nvl(currentUser.getName()));
            txtSurname.setText(nvl(currentUser.getSurname()));
            txtEmail.setText(nvl(currentUser.getEmail()));
            txtBusinessName.setText(nvl(currentUser.getBusinessName()));
            txtPhone.setText(nvl(currentUser.getPhoneNumber()));
            selectedPhotoName = currentUser.getProfilePicture();
            if (selectedPhotoName != null && !selectedPhotoName.trim().isEmpty()) {
                lblPhotoName.setText(selectedPhotoName);
            }
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    Toast.show(this, Toast.Type.ERROR, "Kullanıcı bilgileri yüklenemedi!"));
            return null;
        });
    }

    private void save(JButton btnSave) {
        if (currentUser == null) return;

        String curPin  = new String(txtCurrentPin.getPassword());
        String newPin  = new String(txtNewPin.getPassword());
        String newPin2 = new String(txtNewPinConfirm.getPassword());
        boolean changingPin = !curPin.isEmpty() || !newPin.isEmpty() || !newPin2.isEmpty();

        if (changingPin) {
            if (!PasswordUtil.verify(curPin, currentUser.getPassword())) {
                Toast.show(this, Toast.Type.ERROR, "Mevcut PIN yanlış!");
                return;
            }
            if (newPin.length() != 6 || !newPin.matches("\\d+")) {
                Toast.show(this, Toast.Type.WARNING, "Yeni PIN 6 haneli rakam olmalıdır!");
                return;
            }
            if (!newPin.equals(newPin2)) {
                Toast.show(this, Toast.Type.ERROR, "Yeni PIN'ler uyuşmuyor!");
                return;
            }
        }

        if (txtEmail.getText().trim().isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, "E-posta adresi boş olamaz!");
            return;
        }

        currentUser.setName(txtName.getText().trim());
        currentUser.setSurname(txtSurname.getText().trim());
        currentUser.setEmail(txtEmail.getText().trim());
        currentUser.setBusinessName(txtBusinessName.getText().trim());
        currentUser.setPhoneNumber(txtPhone.getText().trim());
        if (selectedPhotoName != null) currentUser.setProfilePicture(selectedPhotoName);
        if (changingPin) currentUser.setPassword(newPin);

        btnSave.setEnabled(false);
        UserService us = ServiceManager.getUserService();
        us.save(currentUser, true).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
            MyDrawerBuilder.getInstance().setUser(saved);
            Toast.show(this, Toast.Type.SUCCESS, "Profil güncellendi!");
            ModalDialog.closeModal(MODAL_ID);
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                Toast.show(this, Toast.Type.ERROR, "Hata: " + cause.getMessage());
                btnSave.setEnabled(true);
            });
            return null;
        });
    }

    private void selectPhoto() {
        SystemFileChooser fc = new SystemFileChooser();
        fc.setDialogTitle("Profil Fotoğrafı Seç");
        fc.setFileFilter(new SystemFileChooser.FileNameExtensionFilter(
                "Resim Dosyaları (*.jpg, *.png)", "jpg", "png", "jpeg"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File src = fc.getSelectedFile();
            if (src != null) {
                try {
                    File dir = new File(Servicio.getInstance().getDataFolder(), "profiles");
                    if (!dir.exists()) dir.mkdirs();
                    Files.copy(src.toPath(), new File(dir, src.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    selectedPhotoName = src.getName();
                    lblPhotoName.setText(selectedPhotoName);
                } catch (Exception ex) {
                    Toast.show(this, Toast.Type.ERROR, "Fotoğraf kopyalanamadı: " + ex.getMessage());
                }
            }
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