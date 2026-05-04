package tr.cabro.servicio.application.panels.edit;

import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.renderer.CustomerTypeRenderer;
import tr.cabro.servicio.application.component.PhoneField;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;

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

        // TC Kimlik — isteğe bağlı, doluysa 11 hane ve sadece rakam
        String idNo = idNoField.getText().trim();
        if (!Validator.isEmpty(idNo) && (!Validator.isNumeric(idNo) || !Validator.hasLength(idNo, 11))) {
            showValidationError("T.C. Kimlik numarası 11 rakamdan oluşmalı.");
            idNoField.requestFocus();
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
        data.setBusinessName(businessNameField.getText().trim());
        data.setFirstName(nameField.getText().trim());
        data.setLastName(surnameField.getText().trim());
        data.setPhoneNumber1(phone1Field.getNormalizedNumber());
        data.setPhoneNumber2(phone2Field.getNormalizedNumber());
        String identity = idNoField.getText().trim();
        if (!identity.isEmpty()) {
            data.setIdentityNo(idNoField.getText().trim());
        }
        data.setAddress(addressField.getText().trim());
        data.setEmail(emailField.getText().trim());
        data.setNote(notesField.getText().trim());
        data.setType((CustomerType) customerTypeBox.getSelectedItem());
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
        addressField.setText(data.getAddress());
        emailField.setText(data.getEmail());
        notesField.setText(data.getNote());
        customerTypeBox.setSelectedItem(data.getType());
    }

    @Override
    public void clearForm() {
        businessNameField.setText("");
        nameField.setText("");
        surnameField.setText("");
        phone1Field.setNumber("");
        phone2Field.setNumber("");
        idNoField.setText("");
        addressField.setText("");
        emailField.setText("");
        notesField.setText("");
        customerTypeBox.setSelectedItem(CustomerType.NORMAL);
    }

    @Override
    protected Customer createEmptyObject() {
        return new Customer();
    }

    @Override
    protected void initComponent() {
        JPanel formPanel = new JPanel(new MigLayout(
                "wrap 1, insets 5, width 400", // az boşluk, tek sütun
                "[grow,fill]",
                "[]1[]10[]1[]10[]1[]10[]1[]10[]1[]10[]1[]10[]1[]10[]1[]10[]1[]10[]1[]"
        ));

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane);

        java.util.function.Function<String, JLabel> label = text ->
                new JLabel("<html><b>" + text + "</b></html>");

        formPanel.add(label.apply("Firma İsmi (İsteğe Bağlı):"));
        businessNameField = new JTextField();
        formPanel.add(businessNameField, "growx");

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

        formPanel.add(label.apply("TC Kimlik No:"));
        idNoField = new JTextField();
        formPanel.add(idNoField, "growx");

        formPanel.add(label.apply("Adres:"));
        addressField = new JTextField();
        formPanel.add(addressField, "growx");

        formPanel.add(label.apply("E-Posta:"));
        emailField = new JTextField();
        formPanel.add(emailField, "growx");

        formPanel.add(label.apply("Müşteri Tipi:"));
        customerTypeBox = new JComboBox<>();
        DefaultComboBoxModel<CustomerType> model = new DefaultComboBoxModel<>(CustomerType.values());
        model.setSelectedItem(CustomerType.NORMAL);
        customerTypeBox.setModel(model);
        customerTypeBox.setRenderer(new CustomerTypeRenderer());
        formPanel.add(customerTypeBox, "growx");

        formPanel.add(label.apply("Notlar (İsteğe Bağlı):"));
        notesField = new JTextField();
        formPanel.add(notesField, "growx");
    }


    private JTextField businessNameField;
    private JTextField nameField;
    private JTextField surnameField;
    private PhoneField phone1Field;
    private PhoneField phone2Field;
    private JTextField idNoField;
    private JTextField addressField;
    private JTextField emailField;
    private JComboBox<CustomerType> customerTypeBox;
    private JTextField notesField;
}
