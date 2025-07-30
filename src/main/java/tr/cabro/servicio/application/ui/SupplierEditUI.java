package tr.cabro.servicio.application.ui;

import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.util.Edit;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;
import java.awt.*;

public class SupplierEditUI extends JDialog implements Edit<Supplier> {
    private JPanel main_panel;
    private JLabel business_name_label;
    private JTextField business_name_field;
    private JLabel phone_label;
    private JTextField phone_field;
    private JLabel address_label;
    private JTextField address_field;
    private JLabel notes_label;
    private JTextArea notes_field;
    private JLabel name_label;
    private JTextField name_field;
    private JLabel id_no_label;
    private JTextField id_no_field;
    private JLabel email_label;
    private JTextField email_field;
    private JLabel tax_no_label;
    private JTextField tax_no_field;
    private JLabel tax_office_label;
    private JTextField tax_office_field;
    private JButton save_button;
    private JButton cancel_button;

    private final Supplier supplier;
    private final SupplierService supplierService;
    private final Runnable onSaveCallback;

    public SupplierEditUI(Supplier supplier, Runnable onSaveCallback) {
        super((Frame) null, supplier == null ? "Tedarikçi Ekle" : "Tedarikçi Düzenle", true);
        this.supplier = supplier;
        this.supplierService = ServiceManager.getSupplierService();
        this.onSaveCallback = onSaveCallback;
        init();

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.25);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);

        setContentPane(main_panel);
    }

    public SupplierEditUI(Supplier supplier) {
        this(supplier, null);
    }

    private void init() {
        if (supplier != null) {
            populateFormWith(supplier);
        }

        save_button.addActionListener(e -> onSave());
        cancel_button.addActionListener(e -> dispose());
    }

    private void onSave() {
        if (!validateForm()) return;

        Supplier s = collectFormData();
        boolean success = supplierService.save(s, supplier != null);
        if (success) {
            JOptionPane.showMessageDialog(this, "Tedarikçi kaydedildi.");
            if (onSaveCallback != null) {
                onSaveCallback.run(); // Liste ekranını yenile
            }
            clearForm();
        } else {
            JOptionPane.showMessageDialog(this, "Tedarikçi kaydedilemedi!", "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public Supplier collectFormData() {
        Supplier s = supplier != null ? supplier : new Supplier();
        s.setName(name_field.getText().trim());
        s.setBusiness_name(business_name_field.getText().trim());
        s.setPhone(phone_field.getText().trim());
        s.setAddress(address_field.getText().trim());
        s.setNotes(notes_field.getText().trim());
        s.setId_no(id_no_field.getText().trim());
        s.setEmail(email_field.getText().trim());
        s.setTax_no(tax_no_field.getText().trim());
        s.setTax_office(tax_office_field.getText().trim());
        return s;
    }

    @Override
    public void populateFormWith(Supplier s) {
        name_field.setText(s.getName());
        business_name_field.setText(s.getBusiness_name());
        phone_field.setText(s.getPhone());
        address_field.setText(s.getAddress());
        notes_field.setText(s.getNotes());
        id_no_field.setText(s.getId_no());
        email_field.setText(s.getEmail());
        tax_no_field.setText(s.getTax_no());
        tax_office_field.setText(s.getTax_office());
    }

    @Override
    public boolean validateForm() {
        // Ad alanı zorunlu
        if (Validator.isEmpty(name_field.getText())) {
            JOptionPane.showMessageDialog(this, "Ad alanı boş olamaz!");
            return false;
        }
        // Firma adı zorunlu
        if (Validator.isEmpty(business_name_field.getText())) {
            JOptionPane.showMessageDialog(this, "Firma adı boş olamaz!");
            return false;
        }
        // Telefon doluysa geçerli uzunluk ve format
        String phone = phone_field.getText().trim();
        if (!Validator.isEmpty(phone) && (!Validator.isNumeric(phone) || !Validator.hasMinLength(phone, 10))) {
            JOptionPane.showMessageDialog(this, "Telefon numarası sadece rakamlardan oluşmalı ve en az 10 haneli olmalı!");
            return false;
        }
        // Kimlik numarası doluysa geçerli uzunluk
        String idNo = id_no_field.getText().trim();
        if (!Validator.isEmpty(idNo) && (!Validator.isNumeric(idNo) || !Validator.hasLength(idNo, 11))) {
            JOptionPane.showMessageDialog(this, "Kimlik numarası 11 haneli ve sadece rakamlardan oluşmalı!");
            return false;
        }
        // E-posta doluysa format kontrolü
        String email = email_field.getText().trim();
        if (!Validator.isEmpty(email) && !Validator.isValidEmail(email)) {
            JOptionPane.showMessageDialog(this, "Geçerli bir e-posta adresi girin!");
            return false;
        }

        return true;
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
}
