package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemFileChooser;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.UserService;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SetupPanel extends Form {

    private JTextField txtEmail;
    private JTextField txtCompanyName;
    private JPasswordField txtPinCode;
    private JPasswordField txtPinCodeConfirm;
    private JButton btnSelectPhoto;
    private JLabel lblPhotoPath;
    private JButton btnSave;

    private String selectedPhotoName = "avatar_male.svg"; // Varsayılan

    public SetupPanel() {
        setLayout(new MigLayout("al center center"));
        init();
    }

    private void init() {
        initComponents();
    }

    private void initComponents() {
        JPanel panelLogin = new JPanel(new MigLayout());

        JPanel loginContent = new JPanel(new MigLayout("fillx,wrap,insets 35 35 25 35", "[fill,300]"));

        JLabel lbTitle = new JLabel("Servicio'ya Hoşgeldin");
        JLabel lbDescription = new JLabel("Lütfen giriş yapmak için kayıt olun");
        lbTitle.putClientProperty(FlatClientProperties.STYLE, "" +
                "font:bold +12;");

        loginContent.add(lbTitle);
        loginContent.add(lbDescription);

        // Form Elemanları
        txtEmail = new JTextField();
        txtCompanyName = new JTextField();
        txtPinCode = new JPasswordField();
        txtPinCodeConfirm = new JPasswordField();
        btnSelectPhoto = new JButton("Fotoğraf Seç...");
        lblPhotoPath = new JLabel("Seçilmedi (Varsayılan)");
        btnSave = new JButton("Kurulumu Tamamla") {
            @Override
            public boolean isDefaultButton() { return true; }
        };

        applyPinFilter(txtPinCode);
        applyPinFilter(txtPinCodeConfirm);

        // style
        txtEmail.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "işletme@example.com");
        txtCompanyName.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Sıla Elektronik");
        txtPinCode.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "6 Haneli Pin");
        txtPinCodeConfirm.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Pin tekrar");

        panelLogin.putClientProperty(FlatClientProperties.STYLE, "" +
                "[light]border:5,5,5,5,shade($Panel.background,10%),,20;" +
                "[dark]border:5,5,5,5,tint($Panel.background,5%),,20;" +
                "[light]background:shade($Panel.background,3%);" +
                "[dark]background:tint($Panel.background,2%);");

        loginContent.putClientProperty(FlatClientProperties.STYLE, "" +
                "background:null;");

        txtEmail.putClientProperty(FlatClientProperties.STYLE, "" +
                "margin:4,10,4,10;" +
                "arc:12;");

        txtCompanyName.putClientProperty(FlatClientProperties.STYLE, "" +
                "margin:4,10,4,10;" +
                "arc:12;");
        txtPinCode.putClientProperty(FlatClientProperties.STYLE, "" +
                "margin:4,10,4,10;" +
                "arc:12;" +
                "showRevealButton:true;");

        txtPinCodeConfirm.putClientProperty(FlatClientProperties.STYLE, "" +
                "margin:4,10,4,10;" +
                "arc:12;" +
                "showRevealButton:true;");

        btnSave.putClientProperty(FlatClientProperties.STYLE, "" +
                "margin:4,10,4,10;" +
                "arc:12;");

        loginContent.add(new JLabel("E-posta Adresi:"), "gapy 10");
        loginContent.add(txtEmail);

        loginContent.add(new JLabel("Şirket/İşletme Adı:"), "gapy 10");
        loginContent.add(txtCompanyName);

        loginContent.add(new JLabel("PIN Kodu (6 Haneli Rakam):"), "gapy 10");
        loginContent.add(txtPinCode);

        loginContent.add(new JLabel("PIN Kodu Tekrar:"), "gapy 10");
        loginContent.add(txtPinCodeConfirm);

        loginContent.add(new JLabel("Profil Fotoğrafı:"), "gapy 10");
        JPanel photoPanel = new JPanel(new MigLayout("insets 0", "[][grow, fill]", ""));
        photoPanel.setBackground(null);

        photoPanel.add(btnSelectPhoto);
        photoPanel.add(lblPhotoPath);
        loginContent.add(photoPanel);

        loginContent.add(btnSave, "gapy 20");

        panelLogin.add(loginContent);
        add(panelLogin); // Ana panele ortalanmış şekilde ekle

        // Olay Dinleyicileri (Event Listeners)
        btnSelectPhoto.addActionListener((ActionEvent e) -> {
            SystemFileChooser fileChooser = new SystemFileChooser();
            fileChooser.setDialogTitle("Profil Fotoğrafı Seç");

            // Sadece resim dosyalarının seçilmesine izin ver
            fileChooser.setFileFilter(
                    new SystemFileChooser.FileNameExtensionFilter("Resim Dosyaları (*.jpg, *.png)", "jpg", "png", "jpeg")
            );

            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();

                // Güvenlik Zırhı 1: Dosya gerçekten seçilmiş mi?
                if (file != null) {
                    try {
                        // Güvenlik Zırhı 2: Servicio instance'ı null ise varsayılan klasörü kullan
                        File baseFolder = (Servicio.getInstance() != null && Servicio.getInstance().getDataFolder() != null)
                                ? Servicio.getInstance().getDataFolder()
                                : new File(".servicio");

                        File targetDir = new File(baseFolder, "profiles");
                        if (!targetDir.exists()) {
                            targetDir.mkdirs();
                        }

                        File targetFile = new File(targetDir, file.getName());

                        // Dosyayı güvenli bir şekilde kopyala
                        Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                        selectedPhotoName = file.getName();
                        lblPhotoPath.setText(selectedPhotoName);

                    } catch (Exception ex) {
                        // Konsola hatayı yazdır ki arkada ne patlıyor görebilelim
                        ex.printStackTrace();
                        Toast.show(this, Toast.Type.ERROR, "Fotoğraf kopyalanamadı: " + ex.getMessage());
                    }
                }
            }
        });

        btnSave.addActionListener((ActionEvent e) -> {
            String pin = new String(txtPinCode.getPassword());
            String pinConfirm = new String(txtPinCodeConfirm.getPassword());

            if (pin.length() != 6 || !pin.matches("\\d+")) {
                Toast.show(this, Toast.Type.WARNING, "PIN 6 haneli rakam olmalıdır!");
                return;
            }
            if (!pin.equals(pinConfirm)) {
                Toast.show(this, Toast.Type.ERROR, "PIN kodları uyuşmuyor!");
                return;
            }

            btnSave.setEnabled(false);

            // Asenkron Kayıt
            User newUser = new User("", "", txtEmail.getText(), pin, txtCompanyName.getText(), "", selectedPhotoName);
            UserService userService = ServiceManager.getUserService();

            userService.save(newUser, false).thenAccept(savedUser -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, "Kurulum Tamamlandı!");
                    // Doğrudan sisteme al ve inaktif monitörü başlat
                    FormManager.login();
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.ERROR, "Kayıt Hatası: " + ex.getCause().getMessage());
                    btnSave.setEnabled(true);
                });
                return null;
            });
        });
    }

    private void applyPinFilter(JPasswordField field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string == null) return;
                // Sadece sayıysa ve eklendiğinde 6 haneyi geçmiyorsa izin ver
                if (string.matches("\\d+") && (fb.getDocument().getLength() + string.length() <= 6)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text == null) return;
                // Sadece sayıysa ve değiştirildiğinde 6 haneyi geçmiyorsa izin ver
                if (text.matches("\\d+") && (fb.getDocument().getLength() - length + text.length() <= 6)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
    }
}