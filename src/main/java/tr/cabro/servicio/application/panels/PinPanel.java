package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.UserService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;

public class PinPanel extends Form {

    private static final int MAX_PIN_LENGTH = 6;

    private JPasswordField txtPin;
    private JButton btnLogin;

    public PinPanel() {
        setLayout(new MigLayout("al center center"));
        initComponents();
    }

    private void initComponents() {
        // UI oluşturma, stillendirme ve dinleyici ekleme işlemlerini kategorize ettik
        JPanel panelLogin = createLoginPanel();
        add(panelLogin);

        setupListeners();
        setupDocumentFilter();
    }

    private JPanel createLoginPanel() {
        JPanel panelLogin = new JPanel(new MigLayout());
        applyPanelStyles(panelLogin);

        JPanel loginContent = new JPanel(new MigLayout("fillx,wrap,insets 35 35 25 35", "[fill,300]"));
        loginContent.putClientProperty(FlatClientProperties.STYLE, "background:null;");

        // Bileşenleri başlat
        JLabel lblIcon = new JLabel(new Ikon("icons/shield-user.svg", 4f));

        JLabel lblTitle = new JLabel("Hoş Geldiniz");
        lblTitle.putClientProperty(FlatClientProperties.STYLE, "font:bold +3");

        JLabel lblSub = new JLabel("Devam etmek için PIN kodunuzu girin");

        txtPin = new JPasswordField(10);
        btnLogin = new JButton(new Ikon("icons/chevron-right.svg"));

        applyInputStyles();

        // Panele ekle
        loginContent.add(lblIcon);
        loginContent.add(lblTitle);
        loginContent.add(lblSub, "grow 0");
        loginContent.add(txtPin, "gapy 10");

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

    private void applyInputStyles() {
        txtPin.setHorizontalAlignment(SwingConstants.CENTER);
        txtPin.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, btnLogin);
        txtPin.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "• • • •");
        txtPin.putClientProperty(FlatClientProperties.STYLE, "" +
                "margin:4,10,4,10;" +
                "arc:12;" +
                "font:bold +3;");

        btnLogin.setFont(new Font("SansSerif", Font.BOLD, 14));
    }

    // --- EVENT LİSTENER'LAR VE FİLTRELER ---

    private void setupListeners() {
        // Hem butona basıldığında hem de Enter'a basıldığında aynı mantık çalışır (forceError = true)
        btnLogin.addActionListener(e -> verifyPin(true));
        txtPin.addActionListener(e -> verifyPin(true));

        // Otomatik 4 ve 6 hane kontrolü
        txtPin.getDocument().addDocumentListener(new DocumentListener() {
            private void checkLength() {
                SwingUtilities.invokeLater(() -> {
                    int len = txtPin.getPassword().length;
                    if (len == 4 || len == MAX_PIN_LENGTH) {
                        verifyPin(false);
                    }
                });
            }

            @Override
            public void insertUpdate(DocumentEvent e) { checkLength(); }

            @Override
            public void removeUpdate(DocumentEvent e) {}

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });
    }

    private void setupDocumentFilter() {
        ((AbstractDocument) txtPin.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string == null) return;

                if (string.matches("\\d+") && (fb.getDocument().getLength() + string.length() <= MAX_PIN_LENGTH)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                // Eğer text null veya boş ise, bu bir silme/temizleme (setText(null) veya setText("")) işlemidir.
                // Koşulsuz olarak işleme izin ver.
                if (text == null || text.isEmpty()) {
                    super.replace(fb, offset, length, text, attrs);
                    return;
                }

                // Sadece yeni eklenen metinler için rakam ve uzunluk kısıtlamasını uygula.
                if (text.matches("\\d+") && (fb.getDocument().getLength() - length + text.length() <= MAX_PIN_LENGTH)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
    }

    /**
     * PIN doğrulama işlemini yapan metod.
     */
    private void verifyPin(boolean forceError) {
        String enteredPin = new String(txtPin.getPassword());

        if (enteredPin.length() == MAX_PIN_LENGTH) {
            UserService userService = ServiceManager.getUserService();

            // Veritabanındaki tek kullanıcı ile şifreyi karşılaştır
            userService.authenticate(enteredPin).thenAccept(isValid -> {
                SwingUtilities.invokeLater(() -> {
                    if (isValid) {
                        txtPin.setText("");
                        FormManager.login(); // Sisteme gir!
                    } else {
                        txtPin.setText("");
                        JOptionPane.showMessageDialog(this, "Hatalı PIN!", "Hata", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    txtPin.setText("");
                    Toast.show(this, Toast.Type.ERROR, "Doğrulama hatası!");
                });
                return null;
            });
        } else if (forceError) {
            JOptionPane.showMessageDialog(this, "PIN " + MAX_PIN_LENGTH + " haneli olmalıdır!", "Hata", JOptionPane.WARNING_MESSAGE);
            txtPin.setText("");
        }
    }
}