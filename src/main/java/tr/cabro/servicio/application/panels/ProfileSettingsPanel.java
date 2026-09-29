package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.ColorFunctions;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.application.panels.setting.SettingsDialog;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.ProfileImageStore;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.Objects;

/**
 * Profil penceresi: uygulamayı kullanan kişinin adı, e-postası ve profil resmi.
 * <p>
 * Kişi ile işletme ayrıdır: işletme adı, telefonu, adresi ve belgelerdeki logo Ayarlar &gt;
 * İşletme Bilgileri'nde; kilit PIN'i Ayarlar &gt; Güvenlik'te. Uygulama bugün tek kullanıcılı,
 * ama çoklu kullanıcıya geçildiğinde bu pencere kişiye ait kalır.
 * <p>
 * Anatomi {@link SettingsDialog}: üstte kişinin kartı (yuvarlak profil resmi ya da baş harfler,
 * yazıldıkça güncellenen ad ve e-posta), altında alanlar, en altta işletme bilgisi ve PIN'e giden
 * bağlantılar. Kaydet yalnızca kaydedilmemiş değişiklik varken etkindir.
 */
public final class ProfileSettingsPanel {

    private static final int AVATAR = 76;

    private final SettingsDialog dialog = new SettingsDialog("Profil", 500);
    private final JTextField txtName = field("Adınız");
    private final JTextField txtSurname = field("Soyadınız");
    private final JTextField txtEmail = field("ornek@eposta.com");
    private final JLabel emailError = FormKit.errorLabel();
    private final Avatar avatar = new Avatar();
    private final JLabel lblName = new JLabel();
    private final JLabel lblEmail = new JLabel();
    private final JButton btnRemovePhoto = new JButton("Kaldır");

    private User user;
    private String photo;
    private boolean loading;

    private ProfileSettingsPanel() {
        dialog.lead("Uygulamayı kullanan kişi. Adınız ve resminiz menünün üstünde görünür.");

        JPanel body = dialog.body();
        body.add(identityCard(), "span 2");
        body.add(FormKit.cell("Ad", txtName, null));
        body.add(FormKit.cell("Soyad", txtSurname, null));
        body.add(FormKit.cell("E-posta", txtEmail, emailError), "span 2");
        body.add(elsewhere(), "span 2, gaptop 6");

        dialog.primary("Kaydet", false, this::save);
        dialog.setPrimaryEnabled(false);

        DocumentListener live = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        };
        txtName.getDocument().addDocumentListener(live);
        txtSurname.getDocument().addDocumentListener(live);
        txtEmail.getDocument().addDocumentListener(live);

        refreshCard();
        load();
    }

    /** Profil penceresini açar (menü başlığındaki kişi kartından). */
    public static void show(Component parent) {
        ProfileSettingsPanel p = new ProfileSettingsPanel();
        p.dialog.show(parent, p.txtName);
    }

    // ------------------------------------------------------------------ yapı

    /**
     * Kişinin kartı: solda profil resmi (tıklanınca resim seçilir), sağda ad ve e-posta, altında
     * resim düğmeleri. Alanlara yazıldıkça kart güncellenir; menüde nasıl görüneceği burada okunur.
     */
    private JPanel identityCard() {
        JPanel card = new JPanel(new MigLayout("insets 14 16 14 16, fillx, gapx 16, gapy 0",
                "[" + AVATAR + "!][grow, fill]", "[center]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        avatar.setToolTipText("Profil resmi seç");
        avatar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        avatar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                choosePhoto();
            }
        });
        card.add(avatar, "w " + AVATAR + "!, h " + AVATAR + "!");

        lblName.putClientProperty(FlatClientProperties.STYLE, "font: bold +4");
        JButton btnChoose = new JButton("Resim seç…", new Ikon("icons/folder-search.svg", 16, "Label.foreground"));
        btnChoose.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10; iconTextGap: 6");
        btnChoose.addActionListener(e -> choosePhoto());
        btnRemovePhoto.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnRemovePhoto.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10; foreground: $Servicio.dangerColor");
        btnRemovePhoto.addActionListener(e -> setPhoto(""));

        JPanel text = new JPanel(new MigLayout("insets 0, wrap, fillx, gap 0, hidemode 3", "[grow, fill]", "[]2[]10[]"));
        text.setOpaque(false);
        text.add(lblName, "wmin 0");
        text.add(lblEmail, "wmin 0");
        JPanel buttons = new JPanel(new MigLayout("insets 0, gap 6, hidemode 3", "", "[center]"));
        buttons.setOpaque(false);
        buttons.add(btnChoose);
        buttons.add(btnRemovePhoto);
        text.add(buttons);
        card.add(text, "wmin 0");
        return card;
    }

    /** Bu pencerede olmayanlar: nereye taşındıkları bağlantıyla söylenir, tıklanınca oraya gidilir. */
    private JPanel elsewhere() {
        JPanel row = new JPanel(new MigLayout("insets 0, gap 0, hidemode 3", "[][][][]", "[center]"));
        row.setOpaque(false);
        JLabel caption = new JLabel("İşletme adı ve logo:");
        caption.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        row.add(caption);
        row.add(DetailKit.link("İşletme Bilgileri", () -> openSettings("İşletme/İşletme Bilgileri")), "gapright 10");
        JLabel pin = new JLabel("Kilit PIN'i:");
        pin.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        row.add(pin);
        row.add(DetailKit.link("Güvenlik", () -> openSettings("Sistem/Güvenlik")));
        return row;
    }

    private void openSettings(String pageId) {
        dialog.close();
        SwingUtilities.invokeLater(() -> FormManager.showSettings(pageId));
    }

    private static JTextField field(String placeholder) {
        JTextField f = new JTextField();
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        return f;
    }

    // ------------------------------------------------------------------ durum

    private void load() {
        User mem = MyDrawerBuilder.getInstance().getUser();
        Long id = mem != null && mem.getId() != null ? mem.getId() : 1L;
        ServiceManager.getUserService().get(id).thenAccept(opt -> SwingUtilities.invokeLater(() -> {
            if (opt.isEmpty()) return;
            user = opt.get();
            loading = true;
            txtName.setText(nvl(user.getName()));
            txtSurname.setText(nvl(user.getSurname()));
            txtEmail.setText(nvl(user.getEmail()));
            photo = nvl(user.getProfilePicture());
            loading = false;
            changed();
        })).exceptionally(ex -> ErrorHandler.handle(dialog, "Profil bilgileri yüklenemedi", ex));
    }

    private void changed() {
        refreshCard();
        if (loading || user == null) return;
        boolean dirty = !txtName.getText().trim().equals(nvl(user.getName()))
                || !txtSurname.getText().trim().equals(nvl(user.getSurname()))
                || !txtEmail.getText().trim().equals(nvl(user.getEmail()))
                || !Objects.equals(photo, nvl(user.getProfilePicture()));
        dialog.setPrimaryEnabled(dirty);
        dialog.status(dirty ? "Kaydedilmemiş değişiklik var" : null, null, null);
    }

    /** Kartı alanlardan yeniden yazar: boş ad soluk bir yer tutucuyla, boş e-posta hiç yazılmaz. */
    private void refreshCard() {
        String full = fullName();
        lblName.setText(full.isEmpty() ? "Adınızı yazın" : full);
        lblName.putClientProperty(FlatClientProperties.STYLE, full.isEmpty()
                ? "font: bold +4; foreground: $Label.disabledForeground" : "font: bold +4");
        String email = txtEmail.getText().trim();
        lblEmail.setText(email.isEmpty() ? "E-posta yok" : email);
        lblEmail.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        btnRemovePhoto.setVisible(hasPhoto());
        avatar.set(hasPhoto() ? loadImage(photo) : null, initials());
    }

    private void choosePhoto() {
        try {
            String stored = ProfileImageStore.chooseAndStore(dialog, "Profil Resmi Seç", ProfileImageStore.PROFILES_DIR);
            if (stored != null) setPhoto(stored);
        } catch (Exception ex) {
            Toasts.show(dialog, Toast.Type.ERROR, Messages.get("toast.photo.copyFailed", ex.getMessage()));
        }
    }

    private void setPhoto(String name) {
        photo = nvl(name);
        changed();
    }

    private void save() {
        if (user == null) return;
        String email = txtEmail.getText().trim();
        // E-posta isteğe bağlı (ilk kurulumda da öyle); yazıldıysa biçimi denetlenir.
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            FormKit.fail(txtEmail, emailError, "Geçerli bir e-posta adresi yazın ya da boş bırakın.").requestFocusInWindow();
            return;
        }
        dialog.busy();
        ServiceManager.getUserService().updateProfile(user.getId() != null ? user.getId() : 1L,
                        txtName.getText().trim(), txtSurname.getText().trim(), email, photo)
                .thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                    MyDrawerBuilder.getInstance().setUser(saved);
                    Toasts.show(dialog, Toast.Type.SUCCESS, Messages.get("toast.profile.updated"));
                    dialog.close();
                })).exceptionally(ex -> {
                    SwingUtilities.invokeLater(dialog::idle);
                    return ErrorHandler.handle(dialog, "Profil kaydedilemedi", ex);
                });
    }

    // ------------------------------------------------------------------ yardımcılar

    private String fullName() {
        return (txtName.getText().trim() + " " + txtSurname.getText().trim()).trim();
    }

    /** Ad ve soyadın baş harfleri; ad yoksa e-postanın ilk harfi; o da yoksa boş (kişi ikonu çizilir). */
    private String initials() {
        String n = txtName.getText().trim(), s = txtSurname.getText().trim();
        StringBuilder b = new StringBuilder();
        if (!n.isEmpty()) b.append(n.charAt(0));
        if (!s.isEmpty()) b.append(s.charAt(0));
        if (b.length() == 0 && !txtEmail.getText().isBlank()) b.append(txtEmail.getText().trim().charAt(0));
        return b.toString().toUpperCase(java.util.Locale.forLanguageTag("tr"));
    }

    private boolean hasPhoto() {
        return photo != null && !photo.isBlank();
    }

    private static Image loadImage(String name) {
        File file = ProfileImageStore.resolve(ProfileImageStore.PROFILES_DIR, name);
        if (!file.isFile()) return null;
        Image img = new ImageIcon(file.getAbsolutePath()).getImage();
        return img.getWidth(null) > 0 ? img : null;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /**
     * Yuvarlak profil resmi. Resim daireyi kaplayacak şekilde ortadan kırpılır; resim yoksa soluk
     * zemin üstünde baş harfler, onlar da yoksa kişi ikonu. Üzerine gelince kenar çizgisi koyulaşır.
     * Renkler her boyamada temadan okunur.
     */
    private static final class Avatar extends JComponent {
        private Image image;
        private String initials = "";
        private boolean hover;
        private final Ikon fallback = new Ikon("icons/user.svg", 30, "Label.disabledForeground");

        Avatar() {
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void set(Image image, String initials) {
            this.image = image;
            this.initials = initials;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int d = Math.min(getWidth(), getHeight());
                int x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
                Ellipse2D circle = new Ellipse2D.Float(x, y, d, d);

                Color fg = UIManager.getColor("Label.foreground");
                Color ground = UIManager.getColor("Table.background");
                if (image != null) {
                    int iw = image.getWidth(null), ih = image.getHeight(null);
                    double scale = Math.max((double) d / iw, (double) d / ih);
                    int w = (int) Math.round(iw * scale), h = (int) Math.round(ih * scale);
                    Shape old = g2.getClip();
                    g2.clip(circle);
                    g2.drawImage(image, x + (d - w) / 2, y + (d - h) / 2, w, h, null);
                    g2.setClip(old);
                } else {
                    g2.setColor(ColorFunctions.mix(fg, ground, 0.10f));
                    g2.fill(circle);
                    if (!initials.isEmpty()) {
                        g2.setColor(ColorFunctions.mix(fg, ground, 0.70f));
                        Font f = getFont().deriveFont(Font.BOLD, getFont().getSize2D() + UIScale.scale(12f));
                        g2.setFont(f);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(initials, x + (d - fm.stringWidth(initials)) / 2,
                                y + (d - fm.getHeight()) / 2 + fm.getAscent());
                    } else {
                        fallback.paintIcon(this, g2, x + (d - fallback.getIconWidth()) / 2, y + (d - fallback.getIconHeight()) / 2);
                    }
                }
                Color border = hover ? ColorFunctions.mix(fg, ground, 0.45f) : UIManager.getColor("Component.borderColor");
                g2.setColor(border);
                g2.setStroke(new BasicStroke(UIScale.scale(hover ? 1.5f : 1f)));
                float inset = UIScale.scale(0.5f);
                g2.draw(new Ellipse2D.Float(x + inset, y + inset, d - 2 * inset, d - 2 * inset));
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(AVATAR), UIScale.scale(AVATAR));
        }
    }
}
