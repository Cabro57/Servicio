package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.extras.AvatarIcon;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.OtpField;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.UserService;
import tr.cabro.servicio.util.DialogHelper;

import javax.swing.*;
import java.net.URL;

public class PinPanel extends Form {

    private static final int MAX_PIN_LENGTH = 6;

    private OtpField otpPin;

    public PinPanel() {
        setLayout(new MigLayout("al center center"));
        initComponents();
    }

    private void initComponents() {
        // UI oluşturma, stillendirme ve dinleyici ekleme işlemlerini kategorize ettik
        JPanel panelLogin = createLoginPanel();
        add(panelLogin);

        setupListeners();
    }

    private JPanel createLoginPanel() {
        JPanel panelLogin = new JPanel(new MigLayout());
        applyPanelStyles(panelLogin);

        JPanel loginContent = new JPanel(new MigLayout("fillx,wrap,insets 35 35 25 35", "[fill,300]"));
        loginContent.putClientProperty(FlatClientProperties.STYLE, "background:null;");

        // Bileşenleri başlat
        URL path = Servicio.class.getClassLoader().getResource("background_login.png");
        JLabel lblIcon = new JLabel(new AvatarIcon(path, 750, 410, 35));

        JLabel lblTitle = new JLabel("Hoş Geldiniz");
        lblTitle.putClientProperty(FlatClientProperties.STYLE, "font:bold +3");

        JLabel lblSub = new JLabel("Devam etmek için PIN kodunuzu girin");

        otpPin = new OtpField(MAX_PIN_LENGTH);

        // Panele ekle
        loginContent.add(lblIcon);
        loginContent.add(lblTitle);
        loginContent.add(lblSub, "grow 0");
        loginContent.add(otpPin, "gapy 10, al center");

        panelLogin.add(loginContent);
        return panelLogin;
    }

    private void applyPanelStyles(JPanel panelLogin) {
        panelLogin.putClientProperty(FlatClientProperties.STYLE, "" +
                "[light]border:5,5,5,5,shade($Panel.background,10%),,20;" +
                "[dark]border:5,5,5,5,tint($Panel.background,5%),,20;" +
                "[light]background:shade($Panel.background,3%);" +
                "[dark]background:tint($Panel.background,2%);");
    }

    // --- EVENT LİSTENER'LAR ---

    private void setupListeners() {
        // Son hane girilince otomatik doğrula (hata gösterme yok, kullanıcı hâlâ yazıyor olabilir)
        otpPin.setOnComplete(() -> verifyPin(false));
        // Enter'a basılırsa eksik hane varsa uyar
        otpPin.setOnSubmit(() -> verifyPin(true));
    }

    /**
     * Kilit ekranı her gösterildiğinde (panel yeniden eklendiğinde)
     * alanı temizleyip ilk haneye odaklanır.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> otpPin.clear());
    }

    /**
     * PIN doğrulama işlemini yapan metod.
     */
    private void verifyPin(boolean forceError) {
        String enteredPin = otpPin.getValue();

        if (enteredPin.length() == MAX_PIN_LENGTH) {
            UserService userService = ServiceManager.getUserService();

            // Veritabanındaki tek kullanıcı ile şifreyi karşılaştır
            userService.authenticate(enteredPin).thenAccept(isValid -> {
                SwingUtilities.invokeLater(() -> {
                    if (isValid) {
                        otpPin.clear();
                        FormManager.unlock();
                    } else {
                        otpPin.clear();
                        DialogHelper.error(this, "pin.invalid");
                    }
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> otpPin.clear());
                return ErrorHandler.handle(this, "PIN doğrulanamadı", ex);
            });
        } else if (forceError) {
            DialogHelper.error(this, "pin.length.required", MAX_PIN_LENGTH);
            otpPin.clear();
        }
    }
}
