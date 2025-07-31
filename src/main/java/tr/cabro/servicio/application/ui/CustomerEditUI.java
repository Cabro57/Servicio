package tr.cabro.servicio.application.ui;

import lombok.Getter;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.CustomerType;
import tr.cabro.servicio.application.renderer.CustomerTypeRenderer;

import javax.swing.*;
import java.awt.*;

public class CustomerEditUI extends JDialog {
    private JPanel main_panel;
    private JButton save_button;
    private JButton cancel_button;
    private JTextField business_name_field;
    private JLabel business_name_label;
    private JLabel name_label;
    private JTextField name_field;
    private JLabel surname_label;
    private JTextField surname_field;
    private JTextField phone_1_field;
    private JLabel phone_1_label;
    private JTextField phone_2_field;
    private JLabel phone_2_label;
    private JTextField address_field;
    private JLabel address_label;
    private JLabel email_label;
    private JTextField email_field;
    private JLabel customer_type_label;
    private JComboBox<CustomerType> customer_type_box;
    private JLabel notes_label;
    private JTextField notes_field;
    private JButton picture_add_button;
    private JLabel selected_picture_label;
    private JLabel id_no_label;
    private JTextField id_no_field;

    @Getter
    private boolean confirmed = false;
    private final Customer customer;


    public CustomerEditUI(Customer customer) {
        this.customer = customer;

        init();
        add(main_panel);
    }

    private void init() {
        setTitle("Müşteri Düzenle");

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.25);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);

        setupCustomerTypeBox();

        if (customer != null) {
            save_button.setText("Değiştir");

            editSetup();
        }

        save_button.addActionListener(e -> customer_save_cmd());
        cancel_button.addActionListener(e -> dispose());
    }

    public Customer getCustomerFromForm() {
        Customer c = (customer != null) ? customer : new Customer();
        c.setBusiness_name(business_name_field.getText().trim());
        c.setName(name_field.getText().trim());
        c.setSurname(surname_field.getText().trim());
        c.setPhone_number_1(phone_1_field.getText().trim());
        c.setPhone_number_2(phone_2_field.getText().trim());
        c.setAddress(address_field.getText().trim());
        c.setEmail(email_field.getText().trim());
        c.setId_no(id_no_field.getText().trim());
        CustomerType selectedType = (CustomerType) customer_type_box.getSelectedItem();
        if (selectedType != null) {
            c.setStatus(selectedType.getDisplayName());
        }
        return c;
    }

    private void customer_save_cmd() {
        if (validateForm()) {
            confirmed = true;
            dispose();
        }
    }

    private boolean validateForm() {
        if (name_field.getText().trim().isEmpty()) {
            showValidationError("Lütfen müşteri adını giriniz.");
            name_field.requestFocus();
            return false;
        }

        if (surname_field.getText().trim().isEmpty()) {
            showValidationError("Lütfen müşteri soyadını giriniz.");
            surname_field.requestFocus();
            return false;
        }

        String phone1 = phone_1_field.getText().trim();
        if (phone1.isEmpty()) {
            showValidationError("Lütfen birinci telefon numarasını giriniz.");
            phone_1_field.requestFocus();
            return false;
        }
        if (!phone1.matches("\\d{10}")) {
            showValidationError("Telefon numarası 10 rakamdan oluşmalı. (örn: 5321234567)");
            phone_1_field.requestFocus();
            return false;
        }

        String email = email_field.getText().trim();
        if (!email.isEmpty() && !email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            showValidationError("Geçerli bir e-posta adresi giriniz.");
            email_field.requestFocus();
            return false;
        }

        String idNo = id_no_field.getText().trim();
        if (!idNo.isEmpty() && !idNo.matches("\\d{11}")) {
            showValidationError("T.C. Kimlik numarası 11 rakamdan oluşmalı.");
            id_no_field.requestFocus();
            return false;
        }

        if (customer_type_box.getSelectedItem() == null) {
            showValidationError("Lütfen müşteri tipini seçiniz.");
            customer_type_box.requestFocus();
            return false;
        }

        return true;
    }


    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(this, message, "Doğrulama Hatası", JOptionPane.WARNING_MESSAGE);
    }


    private void editSetup() {

        business_name_field.setText(customer.getBusiness_name());
        name_field.setText(customer.getName());
        surname_field.setText(customer.getSurname());
        phone_1_field.setText(customer.getPhone_number_1());
        phone_2_field.setText(customer.getPhone_number_2());
        id_no_field.setText(customer.getId_no());
        address_field.setText(customer.getAddress());
        email_field.setText(customer.getEmail());
        notes_field.setText(customer.getNote());

        String status = customer.getStatus();

        for (int i = 0; i < customer_type_box.getItemCount(); i++) {
            CustomerType ct = customer_type_box.getItemAt(i);
            if (ct.getDisplayName().equals(status)) {
                customer_type_box.setSelectedIndex(i);
                break;
            }
        }

    }

    private void setupCustomerTypeBox() {
        DefaultComboBoxModel<CustomerType> model = new DefaultComboBoxModel<>(CustomerType.values());
        customer_type_box.setModel(model);
        customer_type_box.setRenderer(new CustomerTypeRenderer());
    }


}
