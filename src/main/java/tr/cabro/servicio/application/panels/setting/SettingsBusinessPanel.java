package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.ScaledImageIcon;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Business;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.ProfileImageStore;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Ayarlar &gt; İşletme &gt; İşletme Bilgileri.
 * <p>
 * İşletmenin belgelerde, termal fişte ve WhatsApp mesajlarında basılan bilgileri. V28'den beri
 * kullanıcıdan ayrı, tek satırlık {@code business_profile} tablosunda durur ({@link Business}).
 * <p>
 * Sayfanın başında antet önizlemesi var: PDF belgelerin üstü nasıl basılacaksa öyle, alanlara
 * yazıldıkça güncellenir. Altında Kimlik, Vergi, Ödeme, Çalışma saatleri ve Logo bölümleri; her
 * bölümün notu bilginin nerede basıldığını söyler. Alanlar birbirine bağlı olduğu için anında
 * değil, başlıktaki Kaydet ile kaydedilir; düğme yalnızca kaydedilmemiş değişiklik varken etkindir.
 */
public class SettingsBusinessPanel extends JPanel implements SettingsModal.HeaderActions {

    private static final int LOGO_W = 150;
    private static final int LOGO_H = 84;

    /** Alan ↔ model eşlemesi: yükleme, kirli denetimi ve kayıt aynı listeden yürür. */
    private record Binding(JTextComponent field, Function<Business, String> get, BiConsumer<Business, String> set) {}

    private final List<Binding> bindings = new ArrayList<>();
    private final Map<JTextComponent, JLabel> errors = new LinkedHashMap<>();

    private final JTextField txtName = field("Örn. Cabro Teknik Servis");
    private final JTextField txtPhone = field("0212 000 00 00");
    private final JTextField txtPhone2 = field("05xx xxx xx xx");
    private final JTextField txtEmail = field("info@isletmeniz.com");
    private final JTextField txtWebsite = field("isletmeniz.com");
    private final JTextField txtAddress = field("Mahalle, cadde, no · ilçe / il");
    private final JTextField txtTaxOffice = field("Örn. Kadıköy");
    private final JTextField txtTaxNumber = field("10 haneli VKN ya da 11 haneli TCKN");
    private final JTextField txtIban = field("TR00 0000 0000 0000 0000 0000 00");
    private final JTextField txtBank = field("Örn. Ziraat Bankası");
    private final JTextField txtHolder = field("Hesabın sahibi");
    private final JTextField txtHours = field("Hafta içi 09:00–19:00 · Cumartesi 10:00–17:00");

    private final LetterheadPreview preview = new LetterheadPreview();
    private JLabel logoPreview;
    private JLabel logoName;
    private JButton btnRemoveLogo;
    private final JButton btnSave = new JButton("Kaydet");

    private String selectedLogoName;
    private Business current;
    private boolean loading;

    public SettingsBusinessPanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);

        bind(txtName, Business::getBusinessName, Business::setBusinessName);
        bind(txtPhone, Business::getPhoneNumber, Business::setPhoneNumber);
        bind(txtPhone2, Business::getPhoneNumber2, Business::setPhoneNumber2);
        bind(txtEmail, Business::getEmail, Business::setEmail);
        bind(txtWebsite, Business::getWebsite, Business::setWebsite);
        bind(txtAddress, Business::getAddress, Business::setAddress);
        bind(txtTaxOffice, Business::getTaxOffice, Business::setTaxOffice);
        bind(txtTaxNumber, Business::getTaxNumber, Business::setTaxNumber);
        bind(txtIban, b -> formatIban(b.getIban()), (b, v) -> b.setIban(v.isEmpty() ? null : normalizeIban(v)));
        bind(txtBank, Business::getBankName, Business::setBankName);
        bind(txtHolder, Business::getAccountHolder, Business::setAccountHolder);
        bind(txtHours, Business::getWorkingHours, Business::setWorkingHours);

        add(createPage());

        btnSave.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,16,5,16; font: bold; "
                + "background: $Component.accentColor; foreground: $Servicio.onAccentForeground");
        btnSave.setEnabled(false);
        btnSave.addActionListener(e -> save());

        load();
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(btnSave);
    }

    // ------------------------------------------------------------------ sayfa

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        SettingsKit.section(page, "Antet", "PDF belgelerin üstü böyle basılır. Alanlara yazdıkça güncellenir.", preview);

        JPanel identity = FormKit.grid(2);
        identity.add(FormKit.cell("İşletme adı", txtName, error(txtName)), "span 2");
        identity.add(FormKit.cell("Telefon", txtPhone, null));
        identity.add(FormKit.cell("İkinci telefon", txtPhone2, null));
        identity.add(FormKit.cell("E-posta", txtEmail, error(txtEmail)));
        identity.add(FormKit.cell("Web sitesi", txtWebsite, null));
        identity.add(FormKit.cell("Adres", txtAddress, null), "span 2");
        SettingsKit.section(page, "Kimlik", "Belgelerin antedinde ve termal fişin başında basılır.", identity);

        JPanel tax = FormKit.grid(2);
        tax.add(FormKit.cell("Vergi dairesi", txtTaxOffice, null));
        tax.add(FormKit.cell("Vergi / TC kimlik no", txtTaxNumber, error(txtTaxNumber)));
        SettingsKit.section(page, "Vergi", "Antedin son satırında ve termal fişte basılır.", tax);

        JPanel payment = FormKit.grid(2);
        payment.add(FormKit.cell("IBAN", txtIban, error(txtIban)), "span 2");
        payment.add(FormKit.cell("Banka", txtBank, null));
        payment.add(FormKit.cell("Hesap sahibi", txtHolder, null));
        payment.add(SettingsKit.wrappingNote("Teklif, teslim (kalan bakiye varsa) ve 2.el satış belgelerinde e-faturadaki gibi "
                + "\"Banka Hesap Bilgileri\" bölümü olarak basılır; altında açıklamaya yazılacak belge no ve ödenecek tutar yer alır. "
                + "WhatsApp şablonlarında {iban}, {banka} ve {hesap_sahibi} ile kullanılır."),
                "span 2, wmin 0");
        SettingsKit.section(page, "Ödeme", "Müşteri havale ile ödemek istediğinde.", payment);

        JPanel hours = FormKit.grid(1);
        hours.add(FormKit.cell("Çalışma saatleri", txtHours, null));
        hours.add(SettingsKit.wrappingNote("Belgelerin antedinde saat ikonuyla basılır; WhatsApp şablonlarında "
                + "{calisma_saatleri} ile (\"Cihazınız hazır, … arası teslim alabilirsiniz\")."), "wmin 0");
        SettingsKit.section(page, "Çalışma saatleri", "Müşteri cihazını ne zaman alabilir.", hours);

        SettingsKit.section(page, "Logo", "PDF belgelerin antedinde, işletme adının solunda durur.", createLogo());

        showLogo(null);
        return page;
    }

    private JPanel createLogo() {
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
        return logo;
    }

    private JTextField field(String placeholder) {
        JTextField field = new JTextField();
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        return field;
    }

    private JLabel error(JTextComponent field) {
        JLabel label = FormKit.errorLabel();
        errors.put(field, label);
        return label;
    }

    private void bind(JTextComponent field, Function<Business, String> get, BiConsumer<Business, String> set) {
        bindings.add(new Binding(field, get, set));
    }

    // ------------------------------------------------------------------ durum

    private void load() {
        ServiceManager.getBusinessService().get().thenAccept(opt -> SwingUtilities.invokeLater(() -> {
            current = opt.orElseGet(Business::new);
            loading = true;
            for (Binding b : bindings) b.field().setText(nvl(b.get().apply(current)));
            selectedLogoName = current.getLogoPath();
            showLogo(selectedLogoName);
            loading = false;
            changed();
        })).exceptionally(ex -> ErrorHandler.handle(this, "İşletme bilgileri yüklenemedi", ex));
    }

    /** Her tuşta: önizleme güncellenir, Kaydet kaydedilmemiş değişiklik varsa etkinleşir. */
    private void changed() {
        if (loading) return;
        preview.update(snapshot(), selectedLogoName);
        if (current == null) return;
        boolean dirty = !Objects.equals(nvl(selectedLogoName), nvl(current.getLogoPath()));
        for (Binding b : bindings) {
            if (!b.field().getText().trim().equals(nvl(b.get().apply(current)))) dirty = true;
        }
        btnSave.setEnabled(dirty);
    }

    /** Alanlardaki hâl, kaydedilmemiş olsa da (önizleme ve kayıt için). */
    private Business snapshot() {
        Business b = new Business();
        for (Binding binding : bindings) {
            String v = binding.field().getText().trim();
            binding.set().accept(b, v);
            if (v.isEmpty() && binding.field() != txtIban) binding.set().accept(b, null);
        }
        b.setLogoPath(selectedLogoName != null && !selectedLogoName.isBlank() ? selectedLogoName : null);
        return b;
    }

    /** Biçim denetimi: hatalar alanın altında yazılır, ilk hatalı alana odaklanılır. */
    private boolean validateFields() {
        errors.forEach(FormKit::clear);
        JComponent first = null;
        if (txtName.getText().isBlank()) {
            first = FormKit.fail(txtName, errors.get(txtName), "İşletme adı belgelerin başında basılır; boş olamaz.");
        }
        String email = txtEmail.getText().trim();
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            JComponent f = FormKit.fail(txtEmail, errors.get(txtEmail), "Geçerli bir e-posta yazın ya da boş bırakın.");
            if (first == null) first = f;
        }
        String taxNo = txtTaxNumber.getText().trim();
        if (!taxNo.isEmpty() && !taxNo.matches("\\d{10}|\\d{11}")) {
            JComponent f = FormKit.fail(txtTaxNumber, errors.get(txtTaxNumber), "VKN 10, TC kimlik no 11 hanedir.");
            if (first == null) first = f;
        }
        String iban = txtIban.getText().trim();
        if (!iban.isEmpty() && !isValidIban(normalizeIban(iban))) {
            JComponent f = FormKit.fail(txtIban, errors.get(txtIban), "IBAN geçersiz: TR ve 24 rakam olmalı, kontrol hanesi tutmalı.");
            if (first == null) first = f;
        }
        if (first != null) first.requestFocusInWindow();
        return first == null;
    }

    private void save() {
        if (current == null || !validateFields()) return;
        Business next = snapshot();
        btnSave.setEnabled(false);
        ServiceManager.getBusinessService().save(next)
                .thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                    current = saved;
                    // IBAN kayıtta boşluksuz tutulur; alanda gruplanmış hâli gösterilsin.
                    loading = true;
                    txtIban.setText(formatIban(saved.getIban()));
                    loading = false;
                    MyDrawerBuilder.getInstance().setBusinessName(saved.getBusinessName());
                    changed();
                    SettingsKit.saved(this);
                })).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> btnSave.setEnabled(true));
                    return ErrorHandler.handle(this, "İşletme bilgileri kaydedilemedi", ex);
                });
    }

    // ------------------------------------------------------------------ logo

    private void selectLogo() {
        try {
            String stored = ProfileImageStore.chooseAndStore(this, "İşletme Logosu Seç", ProfileImageStore.LOGOS_DIR);
            if (stored != null) setLogo(stored);
        } catch (Exception ex) {
            Toasts.show(this, Toast.Type.ERROR, Messages.get("toast.logo.copyFailed", ex.getMessage()));
        }
    }

    private void setLogo(String name) {
        selectedLogoName = name;
        showLogo(name);
        changed();
    }

    /** Logoyu kutuya sığacak şekilde, oranını bozmadan gösterir; yoksa boş durum metni. */
    private void showLogo(String name) {
        boolean has = name != null && !name.isBlank();
        btnRemoveLogo.setVisible(has);
        logoPreview.setIcon(null);
        logoPreview.setText(has ? "" : "Logo yok");
        logoName.setText(has ? name : "Belgelerde yalnızca işletme adı basılır.");
        if (!has) return;
        Image image = loadLogo(name);
        if (image == null) {
            logoPreview.setText("Görsel okunamadı");
            return;
        }
        logoPreview.setIcon(ScaledImageIcon.fit(image, UIScale.scale(LOGO_W - 14), UIScale.scale(LOGO_H - 14)));
    }

    private static Image loadLogo(String name) {
        if (name == null || name.isBlank()) return null;
        File file = ProfileImageStore.resolve(ProfileImageStore.LOGOS_DIR, name);
        if (!file.isFile()) return null;
        Image image = new ImageIcon(file.getAbsolutePath()).getImage();
        return image.getWidth(null) > 0 && image.getHeight(null) > 0 ? image : null;
    }

    // ------------------------------------------------------------------ IBAN

    static String normalizeIban(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    static String formatIban(String iban) {
        return Business.formatIban(iban);
    }

    /** Türkiye IBAN'ı: TR + 24 rakam ve ISO 13616 mod-97 kontrol hanesi. */
    static boolean isValidIban(String iban) {
        if (!iban.matches("TR\\d{24}")) return false;
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------ önizleme

    /**
     * Antet önizlemesi: PDF antedinin düzeni (solda logo, yanında kalın işletme adı, altında her
     * bilgi kendi satırında ikonuyla: adres, telefonlar, e-posta, web, çalışma saatleri, vergi).
     * Belgeler siyah-beyaz basıldığı için renk kullanılmaz; boş alanlar yazılmaz, işletme adı
     * boşsa soluk yer tutucu görünür.
     */
    private static final class LetterheadPreview extends JPanel {
        private final JLabel logo = new JLabel();
        private final JLabel name = new JLabel();
        private final JPanel lines = new JPanel(new MigLayout("insets 0, wrap, gap 0 3, hidemode 3", "[grow, fill]", ""));

        LetterheadPreview() {
            super(new MigLayout("insets 16 18 14 18, fillx, gapx 14, gapy 0, hidemode 3", "[][grow, fill]", "[top]"));
            putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
            lines.setOpaque(false);
            add(logo, "spany 2, aligny top");
            add(name, "wmin 0, wrap");
            add(lines, "wmin 0, gaptop 6, wrap");
            add(new JSeparator(), "span 2, growx, gaptop 12");
        }

        void update(Business b, String logoName) {
            boolean hasName = b.getBusinessName() != null;
            name.setText(hasName ? b.getBusinessName() : "İşletme adı");
            name.putClientProperty(FlatClientProperties.STYLE, hasName ? "font: bold +5"
                    : "font: bold +5; foreground: $Label.disabledForeground");

            lines.removeAll();
            line("icons/map-pin.svg", b.getAddress());
            line("icons/phone.svg", b.getPhoneNumber() != null ? PhoneHelper.formatForDisplay(b.getPhoneNumber()) : null);
            line("icons/phone.svg", b.getPhoneNumber2() != null ? PhoneHelper.formatForDisplay(b.getPhoneNumber2()) : null);
            line("icons/mail.svg", b.getEmail());
            line("icons/globe.svg", b.getWebsite());
            line("icons/clock.svg", b.getWorkingHours());
            line("icons/landmark.svg", b.getTaxLine());

            Image image = loadLogo(logoName);
            logo.setIcon(image != null ? ScaledImageIcon.ofHeight(image, UIScale.scale(44)) : null);
            logo.setVisible(image != null);
            revalidate();
            repaint();
        }

        private void line(String icon, String text) {
            if (text == null || text.isBlank()) return;
            JLabel l = new JLabel(text, new Ikon(icon, 12, "Label.disabledForeground"), SwingConstants.LEADING);
            l.setIconTextGap(7);
            l.setToolTipText(text);
            l.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            lines.add(l, "wmin 0");
        }
    }
}
