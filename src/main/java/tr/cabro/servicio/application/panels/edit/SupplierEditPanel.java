package tr.cabro.servicio.application.panels.edit;

import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.component.PhoneField;
import tr.cabro.servicio.model.Supplier;

import javax.swing.*;

public class SupplierEditPanel extends AbstractEditPanel<Supplier> {

    public SupplierEditPanel(Supplier data) {
        super(data);
    }
//    @Override
//    protected boolean validateForm() {
//        // Ad alanı zorunlu
//        if (Validator.isEmpty(name_field.getText())) {
//            showValidationError("Ad alanı boş olamaz!");
//            name_field.requestFocus();
//            return false;
//        }
//        // Firma adı zorunlu
//        if (Validator.isEmpty(business_name_field.getText())) {
//            showValidationError("Firma adı boş olamaz!");
//            business_name_field.requestFocus();
//            return false;
//        }
//        // Telefon doluysa geçerli uzunluk ve format
//        String phone = phone_field.getText().trim();
//        if (!Validator.isEmpty(phone) && (!Validator.isNumeric(phone) || !Validator.hasMinLength(phone, 10))) {
//            showValidationError("Telefon numarası sadece rakamlardan oluşmalı ve en az 10 haneli olmalı!");
//            phone_field.requestFocus();
//            return false;
//        }
//        // Kimlik numarası doluysa geçerli uzunluk
//        String idNo = id_no_field.getText().trim();
//        if (!Validator.isEmpty(idNo) && (!Validator.isNumeric(idNo) || !Validator.hasLength(idNo, 11))) {
//            showValidationError("Kimlik numarası 11 haneli ve sadece rakamlardan oluşmalı!");
//            id_no_field.requestFocus();
//            return false;
//        }
//        // E-posta doluysa format kontrolü
//        String email = email_field.getText().trim();
//        if (!Validator.isEmpty(email) && !Validator.isValidEmail(email)) {
//            showValidationError("Geçerli bir e-posta adresi girin!");
//            email_field.requestFocus();
//            return false;
//        }
//
//        return true;
//    }

    @Override
    protected Supplier collectFormData(@NonNull Supplier data) {
        data.setName(name_field.getText().trim());
        data.setBusinessName(business_name_field.getText().trim());
        data.setPhone(phone_field.getNormalizedNumber());
        data.setAddress(address_field.getText().trim());
        data.setNotes(notes_field.getText().trim());
        data.setIdNo(id_no_field.getText().trim());
        data.setEmail(email_field.getText().trim());
        data.setTaxNo(tax_no_field.getText().trim());
        data.setTaxOffice(tax_office_field.getText().trim());
        return data;
    }

    @Override
    public void populateFormWith(Supplier data) {
        name_field.setText(data.getName());
        business_name_field.setText(data.getBusinessName());
        phone_field.setText(data.getPhone());
        address_field.setText(data.getAddress());
        notes_field.setText(data.getNotes());
        id_no_field.setText(data.getIdNo());
        email_field.setText(data.getEmail());
        tax_no_field.setText(data.getTaxNo());
        tax_office_field.setText(data.getTaxOffice());
    }

    @Override
    public void clearForm() {
        name_field.setText("");
        business_name_field.setText("");
        phone_field.setText("");
        address_field.setText("");
        notes_field.setText("");
        id_no_field.setText("");
        email_field.setText("");
        tax_no_field.setText("");
        tax_office_field.setText("");
    }

    @Override
    protected Supplier createEmptyObject() {
        return new Supplier();
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

        formPanel.add(label.apply("Ad - Soyad"));
        name_field = new JTextField();
        formPanel.add(name_field, "growx");

        formPanel.add(label.apply("Firma İsmi"));
        business_name_field = new JTextField();
        formPanel.add(business_name_field, "growx");

        formPanel.add(label.apply("TC Kimlik No"));
        id_no_field = new JTextField();
        formPanel.add(id_no_field, "growx");

        formPanel.add(label.apply("Vergi Numarası"));
        tax_no_field = new JTextField();
        formPanel.add(tax_no_field, "growx");

        formPanel.add(label.apply("Vergi Dairesi"));
        tax_office_field = new JTextField();
        formPanel.add(tax_office_field, "growx");

        formPanel.add(label.apply("E-Posta"));
        email_field = new JTextField();
        formPanel.add(email_field, "growx");

        formPanel.add(label.apply("Telefon"));
        phone_field = new PhoneField();
        formPanel.add(phone_field, "growx");

        formPanel.add(label.apply("Adres"));
        address_field = new JTextField();
        formPanel.add(address_field, "growx");

        formPanel.add(label.apply("Notlar (İsteğe Bağlı)"), "top");
        notes_field = new JTextArea(5, 20);
        formPanel.add(new JScrollPane(notes_field), "grow, h 100!");


    }

    private JTextField business_name_field;
    private PhoneField phone_field;
    private JTextField address_field;
    private JTextArea notes_field;
    private JTextField name_field;
    private JTextField id_no_field;
    private JTextField email_field;
    private JTextField tax_no_field;
    private JTextField tax_office_field;
}
