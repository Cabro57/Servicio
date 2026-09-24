package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.UserService;
import tr.cabro.servicio.util.ProfileImageStore;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Ayarlar &gt; İşletme &gt; İşletme Bilgileri.
 * <p>
 * İşletme adı, telefon, adres ve logo PDF belgelerin antetinde kullanılıyor — bunlar kişisel
 * bilgi olmadığı için Profil Ayarları modalından buraya taşındı. Veriler {@code users} tablosunda
 * duruyor (mevcut şema korundu); logo dosyası veri klasöründeki {@code logos/} altında.
 * <p>
 * Birbirine bağlı birkaç alan olduğu için anında değil, başlıktaki Kaydet ile kaydedilir;
 * düğme yalnızca kaydedilmemiş değişiklik varken etkindir.
 */
public class SettingsBusinessPanel extends JPanel implements SettingsModal.HeaderActions {

    private static final int LOGO_W = 150;
    private static final int LOGO_H = 84;

    private JTextField txtBusinessName;
    private JTextField txtPhone;
    private JTextField txtAddress;
    private JLabel logoPreview;
    private JLabel logoName;
    private JButton btnRemoveLogo;
    private final JButton btnSave = new JButton("Kaydet");

    private String selectedLogoName;
    private User currentUser;
    private boolean loading;

    public SettingsBusinessPanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());

        btnSave.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,16,5,16; font: bold; "
                + "background: $Component.accentColor; foreground: $Servicio.onAccentForeground");
        btnSave.setEnabled(false);
        btnSave.addActionListener(e -> save());

        loadUser();
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(btnSave);
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        // --- Kimlik ---
        txtBusinessName = field("Örn. Cabro Teknik Servis");
        txtPhone = field("05xx xxx xx xx");
        txtAddress = field("Mahalle, cadde, no · ilçe / il");

        JPanel identity = FormKit.grid(2);
        identity.add(FormKit.cell("İşletme adı", txtBusinessName, null));
        identity.add(FormKit.cell("Telefon", txtPhone, null));
        identity.add(FormKit.cell("Adres", txtAddress, null), "span 2");
        SettingsKit.section(page, "Kimlik", "Belgelerin üstünde ve fişlerin başında basılır.", identity);

        // --- Logo ---
        logoPreview = new JLabel();
        logoPreview.setHorizontalAlignment(SwingConstants.CENTER);
        logoPreview.putClientProperty(FlatClientProperties.STYLE,
                "border: 6,6,6,6,$Component.borderColor,1,10; foreground: $Label.disabledForeground; font: -1");

        logoName = SettingsKit.note("");

        JButton btnChoose = new JButton("Logo seç…", new Ikon("icons/folder-search.svg", 16));
        btnChoose.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12; iconTextGap: 6");
        btnChoose.addActionListener(e -> selectLogo());

        btnRemoveLogo = new JButton("Kaldır");
        btnRemoveLogo.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnRemoveLogo.putClientProperty(FlatClientProperties.STYLE, "margin: 5,10,5,10; foreground: $Servicio.dangerColor");
        btnRemoveLogo.addActionListener(e -> setLogo(""));

        JPanel logo = new JPanel(new MigLayout("insets 0, gapx 16, gapy 6", "[" + LOGO_W + "!][grow]", "[top]"));
        logo.setOpaque(false);
        logo.add(logoPreview, "spany 3, w " + LOGO_W + "!, h " + LOGO_H + "!");
        JPanel buttons = new JPanel(new MigLayout("insets 0, gap 6", "", "[center]"));
        buttons.setOpaque(false);
        buttons.add(btnChoose);
        buttons.add(btnRemoveLogo);
        logo.add(buttons, "wrap");
        logo.add(logoName, "wmin 0, wrap");
        logo.add(SettingsKit.wrappingNote("PNG ya da JPG. Yatay, beyaz ya da saydam zeminli bir logo belgelerde en iyi sonucu verir."),
                "wmin 0, wmax 300");
        SettingsKit.section(page, "Logo", "PDF belgelerin antedinde, işletme adının yanında durur.", logo);

        showLogo(null);
        return page;
    }

    private JTextField field(String placeholder) {
        JTextField field = new JTextField();
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { markDirty(); }
            @Override public void removeUpdate(DocumentEvent e) { markDirty(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        return field;
    }

    private void markDirty() {
        if (loading || currentUser == null) return;
        boolean dirty = !txtBusinessName.getText().trim().equals(nvl(currentUser.getBusinessName()))
                || !txtPhone.getText().trim().equals(nvl(currentUser.getPhoneNumber()))
                || !txtAddress.getText().trim().equals(nvl(currentUser.getAddress()))
                || !Objects.equals(nvl(selectedLogoName), nvl(currentUser.getLogoPath()));
        btnSave.setEnabled(dirty);
    }

    private void loadUser() {
        User memUser = MyDrawerBuilder.getInstance().getUser();
        if (memUser == null) return;
        Long userId = memUser.getId() != null ? memUser.getId() : 1L;

        UserService us = ServiceManager.getUserService();
        us.get(userId).thenAccept(opt -> SwingUtilities.invokeLater(() -> {
            if (opt.isEmpty()) return;
            currentUser = opt.get();
            loading = true;
            txtBusinessName.setText(nvl(currentUser.getBusinessName()));
            txtPhone.setText(nvl(currentUser.getPhoneNumber()));
            txtAddress.setText(nvl(currentUser.getAddress()));
            selectedLogoName = currentUser.getLogoPath();
            showLogo(selectedLogoName);
            loading = false;
            btnSave.setEnabled(false);
        })).exceptionally(ex -> ErrorHandler.handle(this, "İşletme bilgileri yüklenemedi", ex));
    }

    private void selectLogo() {
        try {
            String stored = ProfileImageStore.chooseAndStore(
                    this, "İşletme Logosu Seç", ProfileImageStore.LOGOS_DIR);
            if (stored != null) setLogo(stored);
        } catch (Exception ex) {
            Toast.show(this, Toast.Type.ERROR, Messages.get("toast.logo.copyFailed", ex.getMessage()));
        }
    }

    private void setLogo(String name) {
        selectedLogoName = name;
        showLogo(name);
        markDirty();
    }

    /** Logoyu kutuya sığacak şekilde, oranını bozmadan gösterir; yoksa boş durum metni. */
    private void showLogo(String name) {
        boolean has = name != null && !name.isBlank();
        btnRemoveLogo.setVisible(has);
        logoPreview.setIcon(null);
        logoPreview.setText(has ? "" : "Logo yok");
        logoName.setText(has ? name : "Belgelerde yalnızca işletme adı basılır.");
        if (!has) return;

        File file = ProfileImageStore.resolve(ProfileImageStore.LOGOS_DIR, name);
        if (!file.isFile()) {
            logoPreview.setText("Dosya bulunamadı");
            return;
        }
        Image image = new ImageIcon(file.getAbsolutePath()).getImage();
        int iw = image.getWidth(null), ih = image.getHeight(null);
        if (iw <= 0 || ih <= 0) {
            logoPreview.setText("Görsel okunamadı");
            return;
        }
        int boxW = UIScale.scale(LOGO_W - 14), boxH = UIScale.scale(LOGO_H - 14);
        double scale = Math.min((double) boxW / iw, (double) boxH / ih);
        logoPreview.setIcon(new ImageIcon(image.getScaledInstance(
                Math.max(1, (int) (iw * scale)), Math.max(1, (int) (ih * scale)), Image.SCALE_SMOOTH)));
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
                    markDirty();
                    SettingsKit.saved(this);
                })).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> btnSave.setEnabled(true));
                    return ErrorHandler.handle(this, "İşletme bilgileri kaydedilemedi", ex);
                });
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
