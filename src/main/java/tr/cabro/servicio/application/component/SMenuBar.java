package tr.cabro.servicio.application.component;

import tr.cabro.servicio.application.ui.*;

import javax.swing.*;

public class SMenuBar extends JMenuBar {

    public SMenuBar() {
        init();
    }

    private void init() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        SettingsButton settings_menu = new SettingsButton();
        AboutButton about = new AboutButton();
        JMenu service = new JMenu("Servis");
        JMenu part = new JMenu("Parça");
        JMenu supplier = new JMenu("Tedarikçi");
        JMenu customer = new JMenu("Müşteri");

        // Service
        JMenuItem new_service = new JMenuItem("Yeni Servis");
        new_service.addActionListener(e -> {
            ServiceEditUI dialog = new ServiceEditUI();
            dialog.setVisible(true);
        });
        service.add(new_service);

        JMenuItem service_records = new JMenuItem("Servis Kayıtları");
        service_records.addActionListener(e -> {
            ServiceListUI dialog = new ServiceListUI();
            dialog.setVisible(true);
        });
        service.add(service_records);


        // Part
        JMenuItem add_part = new JMenuItem("Parça Ekle");
        add_part.addActionListener(e -> {
            PartEditUI dialog = new PartEditUI();
            dialog.setModal(true);
            dialog.setVisible(true);
        });
        part.add(add_part);

        JMenuItem parts_list = new JMenuItem("Parça Listesi");
        parts_list.addActionListener(e -> {
            PartManagementUI dialog = new PartManagementUI();
            dialog.setModal(true);
            dialog.setVisible(true);
        });
        part.add(parts_list);


        // Supplier
        JMenuItem add_supplier = new JMenuItem("Tedarikçi Ekle");
        add_supplier.addActionListener(e -> {
            SupplierEditUI dialog = new SupplierEditUI(null);
            dialog.setVisible(true);
        });
        supplier.add(add_supplier);

        JMenuItem supplier_list = new JMenuItem("Tedarikçi Listesi");
        supplier_list.addActionListener(e -> {
            SupplierListUI dialog = new SupplierListUI();
            dialog.setVisible(true);
        });
        supplier.add(supplier_list);


        // Customer
        JMenuItem add_customer = new JMenuItem("Müşteri Ekle");
        add_customer.addActionListener(e -> {
            CustomerEditUI dialog = new CustomerEditUI(null);
            dialog.setVisible(true);
        });
        customer.add(add_customer);

        JMenuItem customer_list = new JMenuItem("Müşteri Listesi");
        customer_list.addActionListener(e -> {
            CustomerListUI dialog = new CustomerListUI();
            dialog.setVisible(true);
        });
        customer.add(customer_list);


        add(service);
        add(supplier);
        add(customer);
        add(part);
        add(about);

        add(Box.createHorizontalGlue());

        add(settings_menu);
    }
}
