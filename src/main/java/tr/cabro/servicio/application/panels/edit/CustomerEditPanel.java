package tr.cabro.servicio.application.panels.edit;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.PhoneField;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Müşteri ekleme/düzenleme formu.
 * <p>
 * Eski düzen 400px genişliğinde 14 etiket+alanı alt alta diziyordu; 768px yüksekliğindeki tezgâh
 * ekranında kaydırma gerektiriyordu. Yeni düzen üç bölümden oluşur (Kimlik, İletişim, Not ve Durum);
 * her bölümün adı solda dar bir rayda durur, alanlar sağda üç kolonluk ızgarada yan yana dizilir.
 * Doğrulama hataları toast yerine ilgili alanın altında gösterilir.
 * <p>
 * DİKKAT: {@link #initComponent()} üst sınıfın kurucusundan çağrılır — bu sınıftaki alanlara
 * başlangıç değeri ({@code = ...}) VERİLMEMELİ, aksi halde kurucu bittikten sonra sıfırlanırlar.
 */
public class CustomerEditPanel extends AbstractEditPanel<Customer> {

    private static final int NAME_MAX_LENGTH = 50;

    public CustomerEditPanel(Customer data) {
        super(data);
    }

    // -------------------------------------------------------------------------
    // Doğrulama
    // -------------------------------------------------------------------------

    @Override
    protected boolean validateForm() {
        clearErrors();
        boolean kurumsal = btnKurumsal.isSelected();
        // Tüm hatalar aynı anda gösterilir; odak ilk hatalı alana gider.
        JComponent firstInvalid = null;

        if (kurumsal && Validator.isEmpty(businessNameField.getText())) {
            firstInvalid = first(firstInvalid, fail(businessNameField, businessNameError, "Kurumsal müşteri için firma ismi zorunlu."));
        }

        String nameMissing = kurumsal ? "Yetkili kişinin adını girin." : "Müşterinin adını girin.";
        if (Validator.isEmpty(nameField.getText())) {
            firstInvalid = first(firstInvalid, fail(nameField, nameError, nameMissing));
        } else if (Validator.exceedsMaxLength(nameField.getText(), NAME_MAX_LENGTH)) {
            firstInvalid = first(firstInvalid, fail(nameField, nameError, "En fazla " + NAME_MAX_LENGTH + " karakter olabilir."));
        }

        String surnameMissing = kurumsal ? "Yetkili kişinin soyadını girin." : "Müşterinin soyadını girin.";
        if (Validator.isEmpty(surnameField.getText())) {
            firstInvalid = first(firstInvalid, fail(surnameField, surnameError, surnameMissing));
        } else if (Validator.exceedsMaxLength(surnameField.getText(), NAME_MAX_LENGTH)) {
            firstInvalid = first(firstInvalid, fail(surnameField, surnameError, "En fazla " + NAME_MAX_LENGTH + " karakter olabilir."));
        }

        // TC Kimlik yalnızca Bireysel'de anlamlı; isteğe bağlı, doluysa 11 rakam.
        if (!kurumsal) {
            String idNo = idNoField.getText().trim();
            if (!Validator.isEmpty(idNo) && (!Validator.isNumeric(idNo) || !Validator.hasLength(idNo, 11))) {
                firstInvalid = first(firstInvalid, fail(idNoField, idNoError, "11 rakamdan oluşmalı."));
            }
        }

        // PhoneField geçersiz numarada da null döner; boş ile geçersizi ayırmak için metne bakılır.
        if (phone1Field.getNormalizedNumber() == null) {
            String message = Validator.isEmpty(phone1Field.getText())
                    ? "Telefon numarası zorunlu."
                    : "Numara eksik veya hatalı.";
            firstInvalid = first(firstInvalid, fail(phone1Field, phone1Error, message));
        }
        if (!Validator.isEmpty(phone2Field.getText()) && phone2Field.getNormalizedNumber() == null) {
            firstInvalid = first(firstInvalid, fail(phone2Field, phone2Error, "Numara hatalı; düzeltin ya da silin."));
        }

        String email = emailField.getText().trim();
        if (!Validator.isEmpty(email) && !Validator.isValidEmail(email)) {
            firstInvalid = first(firstInvalid, fail(emailField, emailError, "Geçerli bir e-posta adresi girin."));
        }

        if (firstInvalid != null) {
            firstInvalid.requestFocusInWindow();
            return false;
        }
        return true;
    }

    private static JComponent first(JComponent current, JComponent candidate) {
        return current != null ? current : candidate;
    }

    /** Alanı hata çerçevesine alır, mesajı altında gösterir; alan yazıldıkça {@link #watchError} temizler. */
    private JComponent fail(JComponent field, JLabel errorLabel, String message) {
        field.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        revalidate();
        return field;
    }

    private void clearErrors() {
        for (JLabel label : new JLabel[]{businessNameError, nameError, surnameError, idNoError, phone1Error, phone2Error, emailError}) {
            label.setVisible(false);
        }
        for (JComponent field : new JComponent[]{businessNameField, nameField, surnameField, idNoField, emailField}) {
            field.putClientProperty(FlatClientProperties.OUTLINE, null);
        }
        // PhoneField çerçevesini kendi anlık kontrolü yönetir, burada ezilmez.
    }

    // -------------------------------------------------------------------------
    // Veri
    // -------------------------------------------------------------------------

    @Override
    protected Customer collectFormData(@NonNull Customer data) {
        boolean kurumsal = btnKurumsal.isSelected();

        data.setType(kurumsal ? CustomerType.KURUMSAL : CustomerType.BIREYSEL);
        data.setFirstName(nameField.getText().trim());
        data.setLastName(surnameField.getText().trim());
        data.setPhoneNumber1(phone1Field.getNormalizedNumber());
        data.setPhoneNumber2(phone2Field.getNormalizedNumber());
        data.setAddress(addressField.getText().trim());
        data.setEmail(emailField.getText().trim());
        data.setNote(notesField.getText().trim());
        data.setProblematic(problematicCheck.isSelected());

        if (kurumsal) {
            data.setBusinessName(businessNameField.getText().trim());
            // tax_number UNIQUE: boş metin "" olarak yazılırsa ikinci vergi nosuz kurumsal müşteri eklenemez.
            String taxNumber = taxNumberField.getText().trim();
            data.setTaxNumber(taxNumber.isEmpty() ? null : taxNumber);
            data.setTaxOffice(taxOfficeField.getText().trim());
        } else {
            String identity = idNoField.getText().trim();
            if (!identity.isEmpty()) {
                data.setIdentityNo(identity);
            }
        }

        return data;
    }

    @Override
    public void populateFormWith(@NonNull Customer data) {
        editingId = data.getId();
        businessNameField.setText(data.getBusinessName());
        nameField.setText(data.getFirstName());
        surnameField.setText(data.getLastName());
        phone1Field.setNumber(data.getPhoneNumber1());
        phone2Field.setNumber(data.getPhoneNumber2());
        idNoField.setText(data.getIdentityNo());
        taxNumberField.setText(nvl(data.getTaxNumber()));
        taxOfficeField.setText(nvl(data.getTaxOffice()));
        addressField.setText(nvl(data.getAddress()));
        emailField.setText(data.getEmail());
        notesField.setText(nvl(data.getNote()));
        problematicCheck.setSelected(data.isProblematic());
        clearErrors();
        syncProblematic();
        syncType(data.getType() == CustomerType.KURUMSAL);
        // Mevcut kayıt açılırken de numara çakışması gösterilsin (eski mükerrer kayıtları ortaya çıkarır).
        checkDuplicatePhone(phone1Field, phone1Duplicate);
        checkDuplicatePhone(phone2Field, phone2Duplicate);
    }

    @Override
    public void clearForm() {
        businessNameField.setText("");
        nameField.setText("");
        surnameField.setText("");
        phone1Field.setNumber("");
        phone2Field.setNumber("");
        idNoField.setText("");
        taxNumberField.setText("");
        taxOfficeField.setText("");
        addressField.setText("");
        emailField.setText("");
        notesField.setText("");
        problematicCheck.setSelected(false);
        phone1Duplicate.setVisible(false);
        phone2Duplicate.setVisible(false);
        clearErrors();
        syncProblematic();
        syncType(false);
    }

    @Override
    protected Customer createEmptyObject() {
        return new Customer();
    }

    // -------------------------------------------------------------------------
    // Durum senkronu
    // -------------------------------------------------------------------------

    /**
     * Bireysel/Kurumsal seçimi ile alan görünürlüğü ve etiketlerin senkron kalmasını garantileyen tek nokta.
     * Kurumsal'da firma bilgisi öne çıkar, Ad/Soyad "yetkili kişi" anlamına gelir.
     */
    private void syncType(boolean kurumsal) {
        btnKurumsal.setSelected(kurumsal);
        btnBireysel.setSelected(!kurumsal);
        for (JComponent c : kurumsalOnly) c.setVisible(kurumsal);
        for (JComponent c : bireyselOnly) c.setVisible(!kurumsal);
        // TC alanı gizlenirken ona ait eski hata mesajı da kalkmalı; Bireysel'e dönünce yeniden doğrulanır.
        if (kurumsal) idNoError.setVisible(false);
        nameLabel.setText(kurumsal ? "Yetkili Adı *" : "Ad *");
        surnameLabel.setText(kurumsal ? "Yetkili Soyadı *" : "Soyad *");
        identityHint.setText(kurumsal
                ? "Firma ve vergi bilgisi, yetkili kişiyle birlikte kaydedilir."
                : "Müşteriyi listelerde ve belgelerde tanıtan bilgiler.");
        revalidate();
        repaint();
    }

    /** Sorunlu işareti açıkken satır tehlike rengine boyanır; kapalıyken nötr kalır. */
    private void syncProblematic() {
        boolean on = problematicCheck.isSelected();
        problematicRow.putClientProperty(FlatClientProperties.STYLE, on
                ? "arc: 12; background: fade($Servicio.dangerColor, 12%)"
                : "arc: 12; background: fade($Label.foreground, 4%)");
        problematicIcon.setIcon(new Ikon("icons/triangle-alert.svg", 1.0f,
                on ? "Servicio.dangerColor" : "Label.disabledForeground"));
        problematicTitle.putClientProperty(FlatClientProperties.STYLE, on
                ? "font: bold; foreground: $Servicio.dangerColor"
                : "font: bold");
        problematicHint.setText(on
                ? "Müşteri sayfasında kırmızı uyarı gösterilir. Sebebini nota yazın."
                : "İş yapılmayacak müşteriyi işaretleyin; sayfasında uyarı gösterilir.");
        problematicRow.repaint();
    }

    // -------------------------------------------------------------------------
    // Aynı telefon uyarısı
    // -------------------------------------------------------------------------

    /**
     * Numara başka bir müşteride kayıtlıysa alanın altında uyarı gösterir. Kaydı engellemez:
     * aynı aileden iki kişinin tek numara kullanması tezgâhta olağan bir durum.
     * Geç gelen eski sorgu sonucunun yenisini ezmemesi için her alanın bir sayaç değeri vardır.
     */
    private void checkDuplicatePhone(PhoneField field, JLabel warning) {
        String normalized = field.getNormalizedNumber();
        int token = nextLookupToken(field);
        CustomerService service = ServiceManager.getCustomerService();
        if (normalized == null || service == null) {
            warning.setVisible(false);
            revalidate();
            return;
        }
        service.findOtherByPhone(normalized, editingId).thenAccept(match -> SwingUtilities.invokeLater(() -> {
            if (token != currentLookupToken(field)) return;
            if (match.isPresent()) {
                warning.setText("Bu numara " + displayName(match.get()) + " adına da kayıtlı.");
                warning.setVisible(true);
            } else {
                warning.setVisible(false);
            }
            revalidate();
            repaint();
        })).exceptionally(ex -> null); // Uyarı yardımcıdır; sorgu hatası formu etkilemez.
    }

    private int nextLookupToken(PhoneField field) {
        return field == phone1Field ? ++phone1LookupToken : ++phone2LookupToken;
    }

    private int currentLookupToken(PhoneField field) {
        return field == phone1Field ? phone1LookupToken : phone2LookupToken;
    }

    private static String displayName(Customer c) {
        if (c.getType() == CustomerType.KURUMSAL && !Validator.isEmpty(c.getBusinessName())) {
            return c.getBusinessName().trim();
        }
        return (nvl(c.getFirstName()) + " " + nvl(c.getLastName())).trim();
    }

    // -------------------------------------------------------------------------
    // Arayüz
    // -------------------------------------------------------------------------

    @Override
    protected void initComponent() {
        setLayout(new BorderLayout());
        kurumsalOnly = new java.util.ArrayList<>();
        bireyselOnly = new java.util.ArrayList<>();

        // Rayda bölüm adı, sağda üç eşit kolon. hidemode 3: gizlenen tip alanları hiç yer kaplamaz
        // (CardLayout en büyük kartın boyunu ayırıp Bireysel'de boşluk bırakıyordu).
        JPanel form = new JPanel(new MigLayout(
                "wrap 2, insets 16 20 12 20, fillx, hidemode 3, width 760:760:",
                "[150!]24[grow, fill]"));

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // --- Kimlik ---
        identityHint = hint("");
        form.add(rail("Kimlik", identityHint), "top");
        form.add(buildIdentitySection());

        form.add(new JSeparator(), "span 2, growx, gaptop 12, gapbottom 12");

        // --- İletişim ---
        form.add(rail("İletişim", hint("WhatsApp mesajları ve arama ilk numaraya yapılır.")), "top");
        form.add(buildContactSection());

        form.add(new JSeparator(), "span 2, growx, gaptop 12, gapbottom 12");

        // --- Not ve Durum ---
        form.add(rail("Not ve Durum", hint("Not, müşteri sayfasındaki Notlar kartında görünür.")), "top");
        form.add(buildStatusSection());

        btnBireysel.addActionListener(e -> { syncType(false); focusFirstField(); });
        btnKurumsal.addActionListener(e -> { syncType(true); focusFirstField(); });

        // Modal açılınca imleç ilk alanda olsun; operatör fareye uzanmadan yazmaya başlar.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(this::focusFirstField);
            }
        });
    }

    private void focusFirstField() {
        (btnKurumsal.isSelected() ? businessNameField : nameField).requestFocusInWindow();
    }

    private JPanel buildIdentitySection() {
        JPanel p = grid();

        btnBireysel = segment("Bireysel", "icons/user.svg");
        btnKurumsal = segment("Kurumsal", "icons/building-2.svg");
        ButtonGroup group = new ButtonGroup();
        group.add(btnBireysel);
        group.add(btnKurumsal);
        btnBireysel.setSelected(true);

        JPanel segmented = new JPanel(new MigLayout("insets 3, gap 2", "[][]", "[]"));
        segmented.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: fade($Label.foreground, 7%)");
        segmented.add(btnBireysel);
        segmented.add(btnKurumsal);
        p.add(segmented, "span 3, growx 0, gapbottom 6");

        // Kurumsal: firma en üstte, altında vergi bilgisi, en sonda yetkili kişi.
        businessNameField = new JTextField();
        businessNameError = errorLabel();
        JPanel businessCell = cell(fieldLabel("Firma İsmi *"), businessNameField, businessNameError);
        p.add(businessCell, "span 3");

        taxOfficeField = new JTextField();
        taxNumberField = new JTextField();
        JPanel taxOfficeCell = cell(fieldLabel("Vergi Dairesi"), taxOfficeField, null);
        JPanel taxNumberCell = cell(fieldLabel("Vergi No"), taxNumberField, null);
        p.add(taxOfficeCell);
        // Gizliyken (Bireysel) hidemode 3 bu wrap'i de yok sayar; akış bozulmaz.
        p.add(taxNumberCell, "wrap");
        java.util.Collections.addAll(kurumsalOnly, businessCell, taxOfficeCell, taxNumberCell);

        // Ortak: Ad / Soyad (Kurumsal'da yetkili kişi), Bireysel'de yanında TC Kimlik.
        nameField = new JTextField();
        surnameField = new JTextField();
        idNoField = new JTextField();
        idNoField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "11 hane");
        nameLabel = fieldLabel("Ad *");
        surnameLabel = fieldLabel("Soyad *");
        nameError = errorLabel();
        surnameError = errorLabel();
        idNoError = errorLabel();

        p.add(cell(nameLabel, nameField, nameError));
        p.add(cell(surnameLabel, surnameField, surnameError));
        JPanel idNoCell = cell(fieldLabel("TC Kimlik No"), idNoField, idNoError);
        // hidemode 1: Kurumsal'da görünmez ama kolonunu korur, satır akışı kaymaz.
        p.add(idNoCell, "hidemode 1");
        bireyselOnly.add(idNoCell);
        return p;
    }

    private JPanel buildContactSection() {
        JPanel p = grid();

        phone1Field = new PhoneField();
        phone2Field = new PhoneField();
        phone2Field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "İsteğe bağlı");
        emailField = new JTextField();
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "İsteğe bağlı");
        phone1Error = errorLabel();
        phone2Error = errorLabel();
        emailError = errorLabel();

        p.add(cell(fieldLabel("Telefon *"), phone1Field, phone1Error));
        p.add(cell(fieldLabel("Telefon 2"), phone2Field, phone2Error));
        p.add(cell(fieldLabel("E-posta"), emailField, emailError));

        phone1Duplicate = duplicateLabel();
        phone2Duplicate = duplicateLabel();
        p.add(phone1Duplicate, "span 3, wmin 0");
        p.add(phone2Duplicate, "span 3, wmin 0");
        watchDuplicate(phone1Field, phone1Duplicate);
        watchDuplicate(phone2Field, phone2Duplicate);

        addressField = textArea(2);
        p.add(cell(fieldLabel("Adres"), areaScroll(addressField), null), "span 3");
        return p;
    }

    private JPanel buildStatusSection() {
        JPanel p = grid();

        notesField = textArea(3);
        notesField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                "Müşteriyle ilgili hatırlatmak istediğiniz her şey");
        p.add(cell(fieldLabel("Not"), areaScroll(notesField), null), "span 3, gapbottom 6");

        // Sorunlu bayrağı küçük bir onay kutusu olarak gözden kaçıyordu; satırın tamamı tıklanabilir,
        // işaretliyken tehlike rengine boyanır.
        problematicCheck = new JCheckBox();
        problematicCheck.getAccessibleContext().setAccessibleName("Sorunlu müşteri");
        problematicCheck.setOpaque(false);
        problematicCheck.addActionListener(e -> syncProblematic());

        problematicIcon = new JLabel();
        problematicTitle = new JLabel("Sorunlu müşteri");
        problematicHint = new JLabel();
        problematicHint.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");

        JPanel texts = new JPanel(new MigLayout("insets 0, gap 0, wrap", "[grow, fill]", "[]2[]"));
        texts.setOpaque(false);
        texts.add(problematicTitle);
        texts.add(problematicHint, "wmin 0");

        problematicRow = new JPanel(new MigLayout("insets 10 12 10 10, gapx 12", "[][grow, fill][]", "[center]"));
        problematicRow.add(problematicIcon);
        problematicRow.add(texts, "wmin 0");
        problematicRow.add(problematicCheck);
        problematicRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter toggleOnClick = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                problematicCheck.doClick();
            }
        };
        problematicRow.addMouseListener(toggleOnClick);
        p.add(problematicRow, "span 3");
        return p;
    }

    // -------------------------------------------------------------------------
    // Küçük bileşen yardımcıları
    // -------------------------------------------------------------------------

    /** Bölümlerin ortak ızgarası: üç eşit kolon, her hücre bir {@link #cell} (etiket + alan + hata). */
    private static JPanel grid() {
        JPanel p = new JPanel(new MigLayout("wrap 3, insets 0, fillx, hidemode 3, gapx 14, gapy 10",
                "[grow, fill, sg col][grow, fill, sg col][grow, fill, sg col]", "[top]"));
        p.setOpaque(false);
        return p;
    }

    private static JPanel rail(String title, JTextArea hint) {
        JPanel p = new JPanel(new MigLayout("insets 2 0 0 0, gap 0, wrap", "[150!]", "[]6[]"));
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        p.add(t);
        p.add(hint, "w 150!");
        return p;
    }

    /**
     * Etiket, alan ve (varsa) altındaki hata mesajını tek hücrede toplar. Etiket-alan arası sıkı,
     * hücreler arası ({@link #grid} gapy) geniş; gizli hata mesajı yer kaplamaz.
     * {@code input} bir metin alanıysa hata mesajı yazılmaya başlanınca kendiliğinden kalkar.
     */
    private JPanel cell(JLabel label, JComponent input, JLabel error) {
        JPanel c = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx, hidemode 3", "[grow, fill]", "[]4[]3[]"));
        c.setOpaque(false);
        label.setLabelFor(input instanceof JScrollPane sp ? sp.getViewport().getView() : input);
        c.add(label);
        c.add(input);
        if (error != null) {
            c.add(error, "wmin 0");
            if (input instanceof JTextComponent text) watchError(text, error);
        }
        return c;
    }

    /** Raydaki açıklama; dar kolonda kelime sınırından satır kırar. */
    private static JTextArea hint(String text) {
        JTextArea t = new JTextArea(text);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setOpaque(false);
        t.setBorder(BorderFactory.createEmptyBorder());
        t.putClientProperty(FlatClientProperties.STYLE,
                "foreground: $Label.disabledForeground; font: -1; background: null; margin: 0,0,0,0");
        return t;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        return l;
    }

    private static JLabel errorLabel() {
        JLabel l = new JLabel();
        l.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Servicio.dangerColor");
        l.setVisible(false);
        return l;
    }

    private static JLabel duplicateLabel() {
        JLabel l = new JLabel();
        l.setIcon(new Ikon("icons/users.svg", 0.75f, "Servicio.warningColor"));
        l.setIconTextGap(6);
        l.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Servicio.warningColor");
        l.setVisible(false);
        return l;
    }

    private static JToggleButton segment(String text, String icon) {
        JToggleButton b = new JToggleButton(text, new Ikon(icon, 0.85f));
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; margin: 5,14,5,14; iconTextGap: 6; "
                        + "toolbar.selectedBackground: fade($Component.accentColor, 22%); toolbar.hoverBackground: fade($Label.foreground, 6%)");
        b.setFocusable(true);
        return b;
    }

    private static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 20);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        // Tab tuşu metne sekme eklemek yerine sonraki alana geçsin (form içinde klavyeyle ilerleme).
        area.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        area.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        return area;
    }

    private static JScrollPane areaScroll(JTextArea area) {
        JScrollPane sp = new JScrollPane(area);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    /** Hatalı alana yazılmaya başlanınca hata mesajı ve çerçeve kalkar. */
    private void watchError(JTextComponent field, JLabel errorLabel) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void clear() {
                if (!errorLabel.isVisible()) return;
                errorLabel.setVisible(false);
                if (!(field instanceof PhoneField)) field.putClientProperty(FlatClientProperties.OUTLINE, null);
                revalidate();
            }
            @Override public void insertUpdate(DocumentEvent e) { clear(); }
            @Override public void removeUpdate(DocumentEvent e) { clear(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
    }

    /** Numara yazımı durduktan kısa süre sonra çakışma sorgusu yapılır; her tuşta DB'ye gidilmez. */
    private void watchDuplicate(PhoneField field, JLabel warning) {
        Timer debounce = new Timer(350, e -> checkDuplicatePhone(field, warning));
        debounce.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    // Başlangıç değeri VERMEYİN — bkz. sınıf açıklaması.
    private java.util.List<JComponent> kurumsalOnly;
    private java.util.List<JComponent> bireyselOnly;
    private Long editingId;
    private int phone1LookupToken;
    private int phone2LookupToken;

    private JToggleButton btnBireysel;
    private JToggleButton btnKurumsal;
    private JTextArea identityHint;
    private JLabel nameLabel;
    private JLabel surnameLabel;
    private JTextField businessNameField;
    private JTextField taxNumberField;
    private JTextField taxOfficeField;
    private JTextField nameField;
    private JTextField surnameField;
    private PhoneField phone1Field;
    private PhoneField phone2Field;
    private JTextField idNoField;
    private JTextArea addressField;
    private JTextField emailField;
    private JTextArea notesField;
    private JCheckBox problematicCheck;
    private JPanel problematicRow;
    private JLabel problematicIcon;
    private JLabel problematicTitle;
    private JLabel problematicHint;

    private JLabel businessNameError;
    private JLabel nameError;
    private JLabel surnameError;
    private JLabel idNoError;
    private JLabel phone1Error;
    private JLabel phone2Error;
    private JLabel emailError;
    private JLabel phone1Duplicate;
    private JLabel phone2Duplicate;
}
