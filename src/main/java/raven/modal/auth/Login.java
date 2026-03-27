package raven.modal.auth;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.DropShadowBorder;
import raven.modal.component.LabelButton;
import raven.modal.menu.MyDrawerBuilder;
import raven.modal.model.ModelUser;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.application.manager.AuthManager;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

public class Login extends Form {

    public Login() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("al center center"));
        createLogin();
    }

    private void createLogin() {
        JPanel panelLogin = new JPanel(new BorderLayout()) {
            @Override
            public void updateUI() {
                super.updateUI();
                applyShadowBorder(this);
            }
        };
        panelLogin.setOpaque(false);
        applyShadowBorder(panelLogin);

        JPanel loginContent = new JPanel(new MigLayout("fillx,wrap,insets 35 35 25 35", "[fill,300]"));

        JLabel lbTitle = new JLabel("Tekrar hoşgeldiniz!");
        JLabel lbDescription = new JLabel("Hesabınıza erişmek için lütfen oturum açın");
        lbTitle.putClientProperty(FlatClientProperties.STYLE, "font:bold +12;");

        loginContent.add(lbTitle);
        loginContent.add(lbDescription);

        // Değişken adı e-postaya uygun olarak güncellendi
        JTextField txtEmail = new JTextField();
        JPasswordField txtPassword = new JPasswordField();
        JCheckBox chRememberMe = new JCheckBox("Beni Hatırla");
        JButton cmdLogin = new JButton("Giriş Yap") {
            @Override
            public boolean isDefaultButton() {
                return true;
            }
        };

        // UI Stilleri
        txtEmail.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "E-posta adresinizi girin");
        txtPassword.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Şifrenizi girin");

        panelLogin.putClientProperty(FlatClientProperties.STYLE, "[dark]background:tint($Panel.background,1%);");
        loginContent.putClientProperty(FlatClientProperties.STYLE, "background:null;");

        txtEmail.putClientProperty(FlatClientProperties.STYLE, "margin:4,10,4,10; arc:12;");
        txtPassword.putClientProperty(FlatClientProperties.STYLE, "margin:4,10,4,10; arc:12; showRevealButton:true;");
        cmdLogin.putClientProperty(FlatClientProperties.STYLE, "margin:4,10,4,10; arc:12;");

        loginContent.add(new JLabel("E-posta"), "gapy 25");
        loginContent.add(txtEmail);
        loginContent.add(new JLabel("Şifre"), "gapy 10");
        loginContent.add(txtPassword);
        loginContent.add(chRememberMe);
        loginContent.add(cmdLogin, "gapy 20");

        // Alt kısımdaki Kayıt Ol/İletişim linkleri
        loginContent.add(createInfo());

        panelLogin.add(loginContent);
        add(panelLogin);

        // --- GERÇEK API ENTEGRASYONU (EVENT) ---
        cmdLogin.addActionListener(e -> {
            String email = txtEmail.getText().trim();
            String password = String.valueOf(txtPassword.getPassword()).trim();

            if (email.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Lütfen e-posta adresinizi ve şifrenizi girin.", "Uyarı", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Arayüz donmasın diye butonu kilitliyoruz
            cmdLogin.setEnabled(false);
            cmdLogin.setText("Buluta Bağlanıyor...");

            // Arka planda (Thread) ağ isteği atıyoruz
            new Thread(() -> {
                try {
                    // 1. Coolify sunucusundaki API'ye bağlan ve doğrula (E-posta adresini gönderiyoruz)
                    String dbKey = AuthManager.login(email, password);

                    // 2. API'den gelen güvenlik anahtarı ile yerel SQLite veritabanını çöz ve başlat
                    boolean isDbReady = DatabaseManager.initialize(dbKey);

                    if (isDbReady) {

                        // 3. Veritabanı şifresi çözüldüğüne göre artık servisleri başlatabiliriz!
                        try {
                            ServiceManager.initialize();
                        } catch (Exception ex) {
                            tr.cabro.servicio.Servicio.getLogger().error("Servisler başlatılamadı!", ex);
                        }

                        SwingUtilities.invokeLater(() -> {
                            // Uygulamanın menüsü (Drawer) için oturum bilgilerini ayarla
                            ModelUser user = new ModelUser(AuthManager.currentUsername, email, ModelUser.Role.ADMIN);
                            MyDrawerBuilder.getInstance().setUser(user);

                            // Raven Framework'ün ana uygulamaya geçiş metodu
                            FormManager.login();
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "Güvenlik İhlali: Veritabanı kilidi açılamadı!", "Kritik Hata", JOptionPane.ERROR_MESSAGE);
                        });
                    }

                } catch (Exception ex) {
                    // API'den "Yanlış Şifre" veya "Lisans İptal" gibi bir yanıt gelirse ekrana basar
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, ex.getMessage(), "Giriş Başarısız", JOptionPane.ERROR_MESSAGE);
                    });
                } finally {
                    // İşlem bittikten sonra butonu eski haline getir
                    SwingUtilities.invokeLater(() -> {
                        cmdLogin.setEnabled(true);
                        cmdLogin.setText("Giriş Yap");
                    });
                }
            }).start();
        });
    }

    private JPanel createInfo() {
        JPanel panelInfo = new JPanel(new MigLayout("wrap,al center", "[center]"));
        panelInfo.putClientProperty(FlatClientProperties.STYLE, "background:null;");

        panelInfo.add(new JLabel("Henüz bir hesabınız yok mu?"), "split 2");
        LabelButton lbLink = new LabelButton("Web'den Kayıt Ol");

        panelInfo.add(lbLink);

        // Tıklandığında bilgisayarın varsayılan tarayıcısında kayıt sitesini açar
        lbLink.addOnClick(e -> {
            try {
                // TODO: İleride buraya kendi gerçek domain adresini yazacaksın (Örn: https://servicio.com/register)
                Desktop.getDesktop().browse(new URI("https://servicio.sametozen.me/register.html"));
            } catch (Exception ex) {
                tr.cabro.servicio.Servicio.getLogger().error("Tarayıcı açılamadı", ex);
                JOptionPane.showMessageDialog(this, "Lütfen sitemizi ziyaret edin: www.servicio.com", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        return panelInfo;
    }

    private void applyShadowBorder(JPanel panel) {
        if (panel != null) {
            panel.setBorder(new DropShadowBorder(new Insets(5, 8, 12, 8), 1, 25));
        }
    }
}