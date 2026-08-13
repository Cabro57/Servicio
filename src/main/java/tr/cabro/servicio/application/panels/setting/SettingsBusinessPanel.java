package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.UserService;
import tr.cabro.servicio.util.ProfileImageStore;

import javax.swing.*;

/**
 * Ayarlar &gt; İşletme &gt; İşletme Bilgileri.
 * <p>
 * İşletme adı, telefon, adres ve logo PDF belgelerin antetinde kullanılıyor — bunlar kişisel
 * bilgi olmadığı için Profil Ayarları modalından buraya taşındı. Veriler {@code users} tablosunda
 * duruyor (mevcut şema korundu); logo dosyası veri klasöründeki {@code logos/} altında.
 */
public class SettingsBusinessPanel extends JPanel {

    private JTextField txtBusinessName;
    private JTextField txtPhone;
    private JTextField txtAddress;
    private JLabel lblLogoName;
    private String selectedLogoName;

    private User currentUser;
    private JButton btnSave;

    public SettingsBusinessPanel() {
        initComponent();
        loadUser();
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 10, gapy 15", "[grow]", "[][][grow][]"));

        // --- İşletme Bilgileri ---
        JPanel infoPanel = new JPanel(new MigLayout("fillx, wrap, insets 10", "[fill,grow]", ""));
        infoPanel.setBorder(BorderFactory.createTitledBorder("İşletme Bilgileri"));

        txtBusinessName = new JTextField();
        txtPhone = new JTextField();
        txtAddress = new JTextField();

        styleField(txtBusinessName, "İşletme adı");
        styleField(txtPhone, "05xx xxx xx xx");
        styleField(txtAddress, "İşletme adresi (İsteğe bağlı)");

        infoPanel.add(new JLabel("Şirket / İşletme Adı:"));
        infoPanel.add(txtBusinessName);
        infoPanel.add(new JLabel("Telefon:"), "gapy 8");
        infoPanel.add(txtPhone);
        infoPanel.add(new JLabel("Adres:"), "gapy 8");
        infoPanel.add(txtAddress);

        add(infoPanel, "growx, wrap");

        // --- Logo ---
        JPanel logoPanel = new JPanel(new MigLayout("fillx, insets 10", "[][grow]", "[]"));
        logoPanel.setBorder(BorderFactory.createTitledBorder("İşletme Logosu"));

        JButton btnLogo = new JButton("Logo Seç...");
        btnLogo.putClientProperty(FlatClientProperties.STYLE, "arc:10;");
        btnLogo.addActionListener(e -> selectLogo());
        lblLogoName = new JLabel("Seçilmedi");

        logoPanel.add(btnLogo);
        logoPanel.add(lblLogoName, "growx");

        JLabel logoHint = new JLabel("Seçilen logo, oluşturulan PDF belgelerin antetinde kullanılır.");
        logoHint.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground;");
        logoPanel.add(logoHint, "newline, span 2");

        add(logoPanel, "growx, wrap");

        add(new JLabel(), "pushy, growy, wrap");

        btnSave = new JButton("Kaydet");
        btnSave.putClientProperty(FlatClientProperties.STYLE, "arc:12; margin:4,10,4,10;");
        btnSave.addActionListener(e -> save());
        add(btnSave, "align right");
    }

    private void loadUser() {
        User memUser = MyDrawerBuilder.getInstance().getUser();
        if (memUser == null) return;
        Long userId = memUser.getId() != null ? memUser.getId() : 1L;

        UserService us = ServiceManager.getUserService();
        us.get(userId).thenAccept(opt -> SwingUtilities.invokeLater(() -> {
            if (opt.isEmpty()) return;
            currentUser = opt.get();
            txtBusinessName.setText(nvl(currentUser.getBusinessName()));
            txtPhone.setText(nvl(currentUser.getPhoneNumber()));
            txtAddress.setText(nvl(currentUser.getAddress()));
            selectedLogoName = currentUser.getLogoPath();
            if (selectedLogoName != null && !selectedLogoName.trim().isEmpty()) {
                lblLogoName.setText(selectedLogoName);
            }
        })).exceptionally(ex -> ErrorHandler.handle(this, "İşletme bilgileri yüklenemedi", ex));
    }

    private void selectLogo() {
        try {
            String stored = ProfileImageStore.chooseAndStore(
                    this, "İşletme Logosu Seç", ProfileImageStore.LOGOS_DIR);
            if (stored != null) {
                selectedLogoName = stored;
                lblLogoName.setText(stored);
            }
        } catch (Exception ex) {
            Toast.show(this, Toast.Type.ERROR, Messages.get("toast.logo.copyFailed", ex.getMessage()));
        }
    }

    private void save() {
        if (currentUser == null) return;

        currentUser.setBusinessName(txtBusinessName.getText().trim());
        currentUser.setPhoneNumber(txtPhone.getText().trim());
        currentUser.setAddress(txtAddress.getText().trim());
        if (selectedLogoName != null) currentUser.setLogoPath(selectedLogoName);

        btnSave.setEnabled(false);
        ServiceManager.getUserService().save(currentUser, true)
                .thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                    MyDrawerBuilder.getInstance().setUser(saved);
                    currentUser = saved;
                    btnSave.setEnabled(true);
                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.business.updated"));
                })).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> btnSave.setEnabled(true));
                    return ErrorHandler.handle(this, "İşletme bilgileri kaydedilemedi", ex);
                });
    }

    private void styleField(JTextField field, String placeholder) {
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.STYLE, "margin:4,10,4,10; arc:12;");
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
