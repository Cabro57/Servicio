package tr.cabro.servicio.application.panels.edit;

import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.PhoneField;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;
import java.awt.event.ActionListener;

public class CustomerEditPanel extends AbstractEditPanel<Customer> {

    public CustomerEditPanel(Customer data) {
        super(data);
    }


    @Override
    protected boolean validateForm() {
        // Ad zorunlu
        if (Validator.isEmpty(nameField.getText())) {
            showValidationError("Lütfen müşteri adını giriniz.");
            nameField.requestFocus();
            return false;
        }
        if (Validator.exceedsMaxLength(nameField.getText(), 50)) {
            showValidationError("Müşteri adı en fazla 50 karakter olabilir.");
            nameField.requestFocus();
            return false;
        }

        // Soyad zorunlu
        if (Validator.isEmpty(surnameField.getText())) {
            showValidationError("Lütfen müşteri soyadını giriniz.");
            surnameField.requestFocus();
            return false;
        }
        if (Validator.exceedsMaxLength(surnameField.getText(), 50)) {
            showValidationError("Müşteri soyadı en fazla 50 karakter olabilir.");
            surnameField.requestFocus();
            return false;
        }

        // Telefon 1 zorunlu
        String phone1 = phone1Field.getNormalizedNumber();
        if (Validator.isEmpty(phone1)) {
            showValidationError("Lütfen birinci telefon numarasını giriniz.");
            phone1Field.requestFocus();
            return false;
        }

        boolean kurumsal = btnKurumsal.isSelected();

        // TC Kimlik — sadece Bireysel'de anlamlı; isteğe bağlı, doluysa 11 hane ve sadece rakam
        if (!kurumsal) {
            String idNo = idNoField.getText().trim();
            if (!Validator.isEmpty(idNo) && (!Validator.isNumeric(idNo) || !Validator.hasLength(idNo, 11))) {
                showValidationError("T.C. Kimlik numarası 11 rakamdan oluşmalı.");
                idNoField.requestFocus();
                return false;
            }
        }

        // Firma İsmi — sadece Kurumsal'da zorunlu
        if (kurumsal && Validator.isEmpty(businessNameField.getText())) {
            showValidationError("Kurumsal müşteri için firma ismi zorunludur.");
            businessNameField.requestFocus();
            return false;
        }

        // E-posta — isteğe bağlı, doluysa geçerli format
        String email = emailField.getText().trim();
        if (!Validator.isEmpty(email) && !Validator.isValidEmail(email)) {
            showValidationError("Geçerli bir e-posta adresi giriniz.");
            emailField.requestFocus();
            return false;
        }

        return true;
    }


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
            data.setTaxNumber(taxNumberField.getText().trim());
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
        businessNameField.setText(data.getBusinessName());
        nameField.setText(data.getFirstName());
        surnameField.setText(data.getLastName());
        phone1Field.setNumber(data.getPhoneNumber1());
        phone2Field.setNumber(data.getPhoneNumber2());
        idNoField.setText(data.getIdentityNo());
        taxNumberField.setText(nvl(data.getTaxNumber()));
        taxOfficeField.setText(nvl(data.getTaxOffice()));
        addressField.setText(data.getAddress());
        emailField.setText(data.getEmail());
        notesField.setText(data.getNote());
        problematicCheck.setSelected(data.isProblematic());
        syncCard(data.getType() == CustomerType.KURUMSAL);
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
        syncCard(false);
    }

    @Override
    protected Customer createEmptyObject() {
        return new Customer();
    }

    /** Bireysel/Kurumsal toggle ile alan görünürlüğünün senkron kalmasını garantileyen tek nokta. */
    private void syncCard(boolean kurumsal) {
        btnKurumsal.setSelected(kurumsal);
        btnBireysel.setSelected(!kurumsal);
        bireyselCard.setVisible(!kurumsal);
        kurumsalCard.setVisible(kurumsal);
        revalidate();
        repaint();
    }

    @Override
    protected void initComponent() {
        JPanel formPanel = new JPanel(new MigLayout(
                "wrap 1, insets 5, width 400", // az boşluk, tek sütun
                "[grow,fill]",
                "[]1[]10[]1[]10[]1[]10[]1[]10[]1[]10[]1[]10[]1[]10[]1[]10[]1[]"
        ));

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane);

        java.util.function.Function<String, JLabel> label = text ->
                new JLabel("<html><b>" + text + "</b></html>");

        // --- Bireysel / Kurumsal segmented toggle ---
        btnBireysel = new JToggleButton("Bireysel", new Ikon("icons/user.svg", 0.8f));
        btnKurumsal = new JToggleButton("Kurumsal", new Ikon("icons/store.svg", 0.8f));
        ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(btnBireysel);
        typeGroup.add(btnKurumsal);
        btnBireysel.setSelected(true);

        JPanel togglePanel = new JPanel(new MigLayout("insets 0, gap 0", "[grow,fill][grow,fill]", "[]"));
        togglePanel.add(btnBireysel);
        togglePanel.add(btnKurumsal);
        formPanel.add(togglePanel, "growx");

        // --- Bireysel/Kurumsal'a göre değişen alanlar ---
        // CardLayout KULLANILMIYOR: gösterilmeyen kart için bile en büyük kartın (Kurumsal,
        // 3 alan) boyutunu ayırıp Bireysel seçiliyken altında boş alan bırakıyordu. Bunun yerine
        // DeviceAccessField'daki pattern: hidemode 3 + setVisible — gizlenen bileşen düzende
        // hiç yer kaplamaz.
        JPanel typeFieldsPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 1, hidemode 3", "[fill,grow]"));

        bireyselCard = new JPanel(new MigLayout("wrap 1, insets 0", "[grow,fill]"));
        idNoField = new JTextField();
        bireyselCard.add(label.apply("TC Kimlik No:"));
        bireyselCard.add(idNoField, "growx");

        kurumsalCard = new JPanel(new MigLayout("wrap 1, insets 0", "[grow,fill]"));
        businessNameField = new JTextField();
        taxNumberField = new JTextField();
        taxOfficeField = new JTextField();
        kurumsalCard.add(label.apply("Firma İsmi:"));
        kurumsalCard.add(businessNameField, "growx");
        kurumsalCard.add(label.apply("Vergi No:"));
        kurumsalCard.add(taxNumberField, "growx");
        kurumsalCard.add(label.apply("Vergi Dairesi:"));
        kurumsalCard.add(taxOfficeField, "growx");

        typeFieldsPanel.add(bireyselCard, "growx");
        typeFieldsPanel.add(kurumsalCard, "growx");
        formPanel.add(typeFieldsPanel, "growx");

        ActionListener toggleListener = e -> syncCard(btnKurumsal.isSelected());
        btnBireysel.addActionListener(toggleListener);
        btnKurumsal.addActionListener(toggleListener);

        formPanel.add(label.apply("Ad:"));
        nameField = new JTextField();
        formPanel.add(nameField, "growx");

        formPanel.add(label.apply("Soyad:"));
        surnameField = new JTextField();
        formPanel.add(surnameField, "growx");

        formPanel.add(label.apply("Telefon 1:"));
        phone1Field = new PhoneField();
        formPanel.add(phone1Field, "growx");

        formPanel.add(label.apply("Telefon 2 (İsteğe Bağlı):"));
        phone2Field = new PhoneField();
        formPanel.add(phone2Field, "growx");

        formPanel.add(label.apply("Adres:"));
        addressField = new JTextField();
        formPanel.add(addressField, "growx");

        formPanel.add(label.apply("E-Posta:"));
        emailField = new JTextField();
        formPanel.add(emailField, "growx");

        problematicCheck = new JCheckBox("Sorunlu Müşteri (İş Yapılmayacak)");
        formPanel.add(problematicCheck, "growx");

        formPanel.add(label.apply("Notlar (İsteğe Bağlı):"));
        notesField = new JTextField();
        formPanel.add(notesField, "growx");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }


    private JToggleButton btnBireysel;
    private JToggleButton btnKurumsal;
    private JPanel bireyselCard;
    private JPanel kurumsalCard;
    private JTextField businessNameField;
    private JTextField taxNumberField;
    private JTextField taxOfficeField;
    private JTextField nameField;
    private JTextField surnameField;
    private PhoneField phone1Field;
    private PhoneField phone2Field;
    private JTextField idNoField;
    private JTextField addressField;
    private JTextField emailField;
    private JCheckBox problematicCheck;
    private JTextField notesField;
}
