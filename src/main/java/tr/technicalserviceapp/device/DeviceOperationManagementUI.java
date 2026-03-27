package tr.technicalserviceapp.device;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import raven.datetime.DatePicker;
import tr.technicalserviceapp.customer.Customer;
import tr.technicalserviceapp.customer.Customers;
import tr.technicalserviceapp.device.addedpart.AddedPart;
import tr.technicalserviceapp.device.addedpart.AddedPartTableModel;
import tr.technicalserviceapp.device.addedpart.AddedParts;
import tr.technicalserviceapp.devicepart.DevicePart;
import tr.technicalserviceapp.devicepart.DeviceParts;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

public class DeviceOperationManagementUI extends JDialog {
    private JPanel main_panel;
    private JPanel content_panel;
    private JFormattedTextField service_fee_textfield;
    private JFormattedTextField track_fee_textfield;
    private JComboBox<String> service_status_combobox;
    private JComboBox<String> payment_status_combobox;
    private JTextArea detected_fault_textarea;
    private JComboBox<String> warranty_status_combobox;
    private JFormattedTextField warranty_end_date_textfield;
    private JFormattedTextField shipping_fee_textfield;
    private JComboBox<String> delivery_type_combobox;
    private JTable added_parts_table;
    private JComboBox<DevicePart> device_part_combobox;
    private JButton device_part_add_button;
    private JScrollPane table_scroll;
    private JButton update_button;
    private JButton delete_button;
    private JLabel customer_label;
    private JLabel total_price_label;
    private JPanel info_panel;
    private JLabel added_parts_label;
    private JToolBar device_part_toolbar;
    private JPanel added_parts_panel;
    private JLabel service_fee_label;
    private JLabel track_fee_label;
    private JLabel service_status_label;
    private JLabel payment_status_label;
    private JLabel detected_fault_label;
    private JScrollPane detected_fault_scrollpane;
    private JLabel operation_made_label;
    private JTextArea operation_made_textarea;
    private JScrollPane operation_made_scrollpane;
    private JLabel ordered_part_label;
    private JTextArea ordered_part_textarea;
    private JScrollPane ordered_part_scrollpane;
    private JLabel warranty_status_label;
    private JLabel warranty_end_date_label;
    private JLabel shipping_fee_label;
    private JLabel delivery_type_label;
    private JButton device_part_remove_button;
    private JButton device_part_edit_button;

    private final ServiceRecord serviceRecord;
    private DatePicker warranty_datepicker;
    private int payment_status = 0;

    public DeviceOperationManagementUI(ServiceRecord serviceRecord) {
        this.serviceRecord = serviceRecord;

        Setup();
        contentSetup();

        add(main_panel);
    }

    private void Setup() {
        setTitle("Arızalı Cihaz İşlermleri");

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.6);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);

        payment_status_combobox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String current_account = "Cari Hesap";
                String current_selected = (String) payment_status_combobox.getSelectedItem();
                if (current_selected != null && current_selected.equals(current_account)) {
                    String current_status = serviceRecord.getPayment_status();
                    if (!current_selected.equals(current_status)) {
                        int confirm = JOptionPane.showConfirmDialog(DeviceOperationManagementUI.this,
                                "Bu işlemin ücretini cari hesaba yansıtmak ister misiniz?",
                                "Yansıtma Onayı",
                                JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            payment_status = 1;
                        }
                    }
                } else {
                    String current_status = serviceRecord.getPayment_status();
                    if (current_status.equals(current_account)) {
                        int confirm = JOptionPane.showConfirmDialog(DeviceOperationManagementUI.this,
                                "Cari hesaptan bu işlem için alınan ücreti kaldırmak ister misiniz?",
                                "Kesim Onayı",
                                JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            payment_status = 2;
                        }
                    }
                }
            }
        });

        warranty_datepicker = new DatePicker();
        warranty_datepicker.setEditor(warranty_end_date_textfield);
        warranty_datepicker.setDateFormat("dd/MM/yyyy");

        update_button.addActionListener(e -> updateDevice());
        delete_button.addActionListener(e -> deleteDevice());

        DeviceParts.load();
        for (DevicePart part : DeviceParts.getDeviceParts()) {
            if (part.getStock() >= 0) {
                device_part_combobox.addItem(part);
            }
        }

        device_part_add_button.setIcon(new FlatSVGIcon("icon/add.svg"));
        device_part_add_button.addActionListener(e -> {
            DevicePart selected_device_part = (DevicePart) device_part_combobox.getSelectedItem();

            if (selected_device_part != null && selected_device_part.getStock() <= 0) {
                int confirmDelete = JOptionPane.showConfirmDialog(DeviceOperationManagementUI.this,
                        "Eklenen Parçanın stoğu kalmamış yine de eklemek ister misiniz?",
                        "Ekleme İşlemi",
                        JOptionPane.YES_NO_OPTION);
                if (confirmDelete != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            int added_part_ID = AddedParts.getSafeID();
            int device_id = serviceRecord.getID();
            int device_part_id = selected_device_part != null ? selected_device_part.getID() : -1;
            double purchase_price = selected_device_part != null ? selected_device_part.getPurchase_price() : 0;
            double sale_price = selected_device_part != null ? selected_device_part.getSale_price() : 0;
            LocalDateTime date = LocalDateTime.now();

            AddedPart addedPart = new AddedPart(added_part_ID, device_id, device_part_id);
            addedPart.setPurchasePrice(purchase_price);
            addedPart.setSalePrice(sale_price);
            addedPart.setDate(date);

            AddedParts.add(addedPart);
            AddedParts.save();


            AddedParts.load();
            added_parts_table.setModel(new AddedPartTableModel(AddedParts.getAddedParts(), serviceRecord.getID()));

            trackFeeSetup();

            if (selected_device_part != null) {
                selected_device_part.setStock(selected_device_part.getStock()-1);
                DeviceParts.save();
            }
        });

        device_part_edit_button.setIcon(new FlatSVGIcon("icon/edit.svg", 16, 16));
        device_part_edit_button.addActionListener(e -> {
            int selectedRow = added_parts_table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(DeviceOperationManagementUI.this,
                        "Lütfen güncellemk için bir parça seçin.",
                        "Uyarı",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            AddedPartTableModel model = (AddedPartTableModel) added_parts_table.getModel();
            AddedPart selectedPart = model.getAddedPart(selectedRow);
            AddedPartEditUI addedPartEditUI = new AddedPartEditUI(selectedPart);
            addedPartEditUI.setModal(true);
            addedPartEditUI.setVisible(true);
            addedPartEditUI.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            addedPartEditUI.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    AddedParts.load();
                    added_parts_table.setModel(new AddedPartTableModel(AddedParts.getAddedParts(), serviceRecord.getID()));
                    trackFeeSetup();
                }
            });
        });

        device_part_remove_button.setIcon(new FlatSVGIcon("icon/delete.svg"));
        device_part_remove_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedRow = added_parts_table.getSelectedRow();
                if (selectedRow == -1) {
                    JOptionPane.showMessageDialog(DeviceOperationManagementUI.this,
                            "Lütfen kaldırmak için bir parça seçin.",
                            "Uyarı",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                AddedPartTableModel model = (AddedPartTableModel) added_parts_table.getModel();
                AddedPart selectedPart = model.getAddedPart(selectedRow);
                int confirmDelete = JOptionPane.showConfirmDialog(DeviceOperationManagementUI.this,
                        "Seçilen parçayı silmek istediğinize emin misiniz?",
                        "Silme Onayı",
                        JOptionPane.YES_NO_OPTION);
                if (confirmDelete != JOptionPane.YES_OPTION) {
                    return;
                }
                AddedParts.remove(selectedPart);
                AddedParts.save();
                int confirmStockUpdate = JOptionPane.showConfirmDialog(DeviceOperationManagementUI.this,
                        "Bu parçayı tekrar stoğa eklemek ister misiniz?",
                        "Stok Güncellemesi",
                        JOptionPane.YES_NO_OPTION);
                if (confirmStockUpdate == JOptionPane.YES_OPTION) {
                    DevicePart associatedPart = DeviceParts.getDevicePart(selectedPart.getDevicePartID());
                    if (associatedPart != null) {
                        associatedPart.setStock(associatedPart.getStock() + 1);
                        DeviceParts.save();
                    }
                }
                AddedParts.load();
                added_parts_table.setModel(new AddedPartTableModel(AddedParts.getAddedParts(), serviceRecord.getID()));
                trackFeeSetup();

                JOptionPane.showMessageDialog(DeviceOperationManagementUI.this,
                        "Seçilen parça başarıyla kaldırıldı.",
                        "Bilgi",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        AddedParts.load();
        added_parts_table.setModel(new AddedPartTableModel(AddedParts.getAddedParts(), serviceRecord.getID()));
        added_parts_table.getModel().addTableModelListener(e -> trackFeeSetup());

        trackFeeSetup();
    }

    private void contentSetup() {
        Customer customer = Customers.getCustomer(serviceRecord.getCustomer());
        double service_fee = serviceRecord.getService_fee();
        String service_status = serviceRecord.getService_status();
        String payment_status = serviceRecord.getPayment_status();
        String detected_fault = serviceRecord.getDetected_fault();
        String operation_made = serviceRecord.getOperation_made();
        String ordered_part = serviceRecord.getOrdered_part();
        boolean warranty_status = serviceRecord.isWarranty_status();
        LocalDate warranty_end_date = serviceRecord.getWarranty_end_date() == null ? LocalDate.now() : serviceRecord.getWarranty_end_date();
        double shipping_fee = serviceRecord.getShipping_fee();
        String delivery_type = serviceRecord.getDelivery_type();

        customer_label.setText(customer.toString());
        total_price_label.setText(String.format("Toplam Tutar: %.2f ₺", setTotalPrice()));
        service_fee_textfield.setValue(service_fee);
        service_status_combobox.setSelectedItem(service_status);
        payment_status_combobox.setSelectedItem(payment_status);
        detected_fault_textarea.setText(detected_fault);
        operation_made_textarea.setText(operation_made);
        ordered_part_textarea.setText(ordered_part);
        warrantySetup(warranty_status, warranty_end_date);
        shipping_fee_textfield.setValue(shipping_fee);
        delivery_type_combobox.setSelectedItem(delivery_type);
    }

    private void warrantySetup(boolean status, LocalDate date) {
        if (status) {
            warranty_status_combobox.setSelectedIndex(1);
            warranty_end_date_textfield.setEditable(true);
            warranty_datepicker.setSelectedDate(date);
        } else {
            warranty_status_combobox.setSelectedIndex(0);
            warranty_end_date_textfield.setEditable(false);
            warranty_datepicker.clearSelectedDate();
        }

        warranty_status_combobox.addActionListener(e -> {
            int selectedIndex = warranty_status_combobox.getSelectedIndex();
            warranty_end_date_textfield.setEditable(selectedIndex != 0);
        });
    }

    private void trackFeeSetup() {
        double totalTrackFee = 0.0;

        // Tablo modelini AddedPartTableModel olarak cast et
        AddedPartTableModel model = (AddedPartTableModel) added_parts_table.getModel();

        // Tablo modelindeki toplam tutarı hesapla
        for (int i = 0; i < added_parts_table.getRowCount(); i++) {
            AddedPart addedPart = model.getAddedPart(i);  // Satırdaki AddedPart objesini al
            double salePrice = addedPart.getSalePrice(); // Satış fiyatını al
            totalTrackFee += salePrice; // Toplam ücreti arttır
        }

        // Track Fee alanını güncelle
        track_fee_textfield.setValue(totalTrackFee);

        // Toplam tutarı yeniden hesapla
        setTotalPrice();
    }

    private DocumentListener feeFieldListener() {
        return new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                total_price_label.setText(String.format("Toplam Tutar: %.2f ₺", setTotalPrice()));
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                total_price_label.setText(String.format("Toplam Tutar: %.2f ₺", setTotalPrice()));
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                total_price_label.setText(String.format("Toplam Tutar: %.2f ₺", setTotalPrice()));
            }
        };
    }

    private Double setTotalPrice() {
        double service_fee = (double) service_fee_textfield.getValue();
        double track_fee = (double) track_fee_textfield.getValue();
        double shipping_fee = (double) shipping_fee_textfield.getValue();

        return service_fee + track_fee + shipping_fee;
    }

    private void updateDevice() {
        double service_fee = ((Number) service_fee_textfield.getValue()).doubleValue();
        String service_status = (String) service_status_combobox.getSelectedItem();
        String payment_status = (String) payment_status_combobox.getSelectedItem();
        String detected_fault = detected_fault_textarea.getText();
        String operation_made = operation_made_textarea.getText();
        String ordered_part = ordered_part_textarea.getText();
        boolean warranty_status = warranty_status_combobox.getSelectedItem().equals("var");
        LocalDate warranty_end_date = warranty_datepicker.getSelectedDate();
        double shipping_fee = ((Number) shipping_fee_textfield.getValue()).doubleValue();
        String delivery_type = (String) delivery_type_combobox.getSelectedItem();

        serviceRecord.setService_fee(service_fee);
        serviceRecord.setService_status(service_status);
        serviceRecord.setPayment_status(payment_status);
        serviceRecord.setDetected_fault(detected_fault);
        serviceRecord.setOperation_made(operation_made);
        serviceRecord.setOrdered_part(ordered_part);
        serviceRecord.setWarranty_status(warranty_status);
        serviceRecord.setWarranty_end_date(warranty_end_date);
        serviceRecord.setShipping_fee(shipping_fee);
        serviceRecord.setDelivery_type(delivery_type);

        if (!warranty_status) {
            serviceRecord.setWarranty_end_date((LocalDate) null);
        }

        ServiceRecords.update(serviceRecord);
        if (this.payment_status != 0) {
            Customer customer = Customers.getCustomer(serviceRecord.getCustomer());
            switch (this.payment_status) {
                case 1: customer.addPrice(setTotalPrice()); break;
                case 2: customer.removePrice(setTotalPrice()); break;
                default: break;
            }
            Customers.save();
        }
        ServiceRecords.save();
    }

    private void deleteDevice() {
        // İşleme ait eklenen parçaların olup olmadığını kontrol et
        boolean hasAddedParts = false;
        for (AddedPart addedPart : AddedParts.getAddedParts()) {
            if (addedPart.getDeviceID() == serviceRecord.getID()) {
                hasAddedParts = true;
                break; // Eklenen parça bulundu, döngüyü sonlandır
            }
        }

        // Eğer eklenen parça varsa kullanıcıyı bilgilendir ve silme işlemini iptal et
        if (hasAddedParts) {
            JOptionPane.showMessageDialog(this,
                    "Bu işleme ait eklenen parçalar mevcut.", "Mevcut Parça", JOptionPane.ERROR_MESSAGE);
        } else {
            // Eklenen parça yoksa, işlemi silmek için onay al
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bu işlemi silmek istediğinizden emin misiniz?",
                    "Silme Onayı", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                ServiceRecords.remove(serviceRecord); // İşlemi sil
                dispose(); // Pencereden çık
            }
        }
    }


    private NumberFormatter createPriceFormatter() {
        DecimalFormat format = (DecimalFormat) DecimalFormat.getInstance(new Locale("tr", "TR"));
        format.applyPattern("#,##0.00 ₺");
        NumberFormatter priceFormatter = new NumberFormatter(format);
        priceFormatter.setValueClass(Double.class);  // Fiyat değeri Double olacak
        priceFormatter.setAllowsInvalid(false);      // Geçersiz girişlere izin verme
        priceFormatter.setMinimum(0.0);              // Minimum değer 0
        priceFormatter.setMaximum(1_000_000.0);      // Maksimum değer 1.000.000
        return priceFormatter;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        NumberFormatter priceFormatter = createPriceFormatter();

        // JFormattedTextField oluştur
        service_fee_textfield = new JFormattedTextField(priceFormatter);
        service_fee_textfield.setFocusLostBehavior(JFormattedTextField.COMMIT);
        service_fee_textfield.setColumns(10);
        service_fee_textfield.setValue(0.0);
        service_fee_textfield.getDocument().addDocumentListener(feeFieldListener());

        priceFormatter = createPriceFormatter();

        track_fee_textfield = new JFormattedTextField(priceFormatter);
        track_fee_textfield.setFocusLostBehavior(JFormattedTextField.COMMIT);
        track_fee_textfield.setColumns(10);
        track_fee_textfield.setValue(0.0);
        track_fee_textfield.getDocument().addDocumentListener(feeFieldListener());

        priceFormatter = createPriceFormatter();

        shipping_fee_textfield = new JFormattedTextField(priceFormatter);
        shipping_fee_textfield.setFocusLostBehavior(JFormattedTextField.COMMIT);
        shipping_fee_textfield.setColumns(10);
        shipping_fee_textfield.setValue(0.0);
        shipping_fee_textfield.getDocument().addDocumentListener(feeFieldListener());
    }
}
