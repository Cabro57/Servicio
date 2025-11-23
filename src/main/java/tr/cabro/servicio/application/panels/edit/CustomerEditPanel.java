package tr.cabro.servicio.application.panels.edit;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.renderer.CustomerTypeRenderer;
import tr.cabro.servicio.component.PhoneField;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.CustomerType;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;

public class CustomerEditPanel extends AbstractEditPanel<Customer> {

    public CustomerEditPanel() {
        super();


    }

//    @Override
//    protected boolean validateForm() {
//        if (Validator.isEmpty(nameField.getText())) {
//            showValidationError("Lütfen müşteri adını giriniz.");
//            nameField.requestFocus();
//            return false;
//        }
//
//        if (Validator.isEmpty(surnameField.getText())) {
//            showValidationError("Lütfen müşteri soyadını giriniz.");
//            surnameField.requestFocus();
//            return false;
//        }
//
//        String phone1 = phone1Field.getNormalizedNumber().trim();
//        if (Validator.isEmpty(phone1)) {
//            showValidationError("Lütfen birinci telefon numarasını giriniz.");
//            phone1Field.requestFocus();
//            return false;
//        }
//        if (!Validator.isNumeric(phone1) || !Validator.hasLength(phone1, 10)) {
//            showValidationError("Telefon numarası 10 rakamdan oluşmalı. (örn: 5321234567)");
//            phone1Field.requestFocus();
//            return false;
//        }
//
//        String email = emailField.getText().trim();
//        if (!Validator.isEmpty(email) && !Validator.isValidEmail(email)) {
//            showValidationError("Geçerli bir e-posta adresi giriniz.");
//            emailField.requestFocus();
//            return false;
//        }
//
//        String idNo = idNoField.getText().trim();
//        if (!Validator.isEmpty(idNo) &&
//                (!Validator.isNumeric(idNo) || !Validator.hasLength(idNo, 11))) {
//            showValidationError("T.C. Kimlik numarası 11 rakamdan oluşmalı.");
//            idNoField.requestFocus();
//            return false;
//        }
//
//        if (customerTypeBox.getSelectedItem() == null) {
//            showValidationError("Lütfen müşteri tipini seçiniz.");
//            customerTypeBox.requestFocus();
//            return false;
//        }
//
//        return true;
//    }

    @Override
    protected Customer collectFormData() {
        Customer c = new Customer();
        c.setBusinessName(businessNameField.getText().trim());
        c.setName(nameField.getText().trim());
        c.setSurname(surnameField.getText().trim());
        c.setPhoneNumber1(phone1Field.getNormalizedNumber());
        c.setPhoneNumber2(phone2Field.getNormalizedNumber());
        c.setIdNo(idNoField.getText().trim());
        c.setAddress(addressField.getText().trim());
        c.setEmail(emailField.getText().trim());
        c.setNote(notesField.getText().trim());
        c.setType((CustomerType) customerTypeBox.getSelectedItem());
        return c;
    }

    @Override
    public void populateFormWith(Customer data) {
        if (data == null) return;
        businessNameField.setText(data.getBusinessName());
        nameField.setText(data.getName());
        surnameField.setText(data.getSurname());
        phone1Field.setNumber(data.getPhoneNumber1());
        phone2Field.setNumber(data.getPhoneNumber2());
        idNoField.setText(data.getIdNo());
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
        customerTypeBox.setSelectedIndex(-1);
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
