package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import raven.datetime.DatePicker;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.compenents.CurrencyField;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.settings.Settings;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;

public class PartEditUI extends JDialog {

    private JPanel main_panel;
    private JScrollPane panel_scroll;

    private JPanel content_panel;

    private JPanel barcode_panel;
    private JLabel barcode_label;
    private JTextField barcode_field;
    private JLabel situation_label;
    private JLabel barcode_info_label;
    private JLabel barcode_info;

    private JLabel name_label;
    private JTextField name_field;

    private JLabel brand_label;
    private JTextField brand_field;

    private JLabel device_type_label;
    private JComboBox<String> device_type_combo;

    private JLabel models_label;
    private JTextField models_field;

    private JLabel purchase_price_label;
    private JFormattedTextField purchase_price_field;
    private JLabel sale_price_label;
    private JFormattedTextField sale_price_field;

    private JLabel stock_label;
    private JSpinner stock_spinner;
    private JLabel min_stock_label;
    private JSpinner min_stock_spinner;

    private JLabel warranty_period_label;
    private JSpinner warranty_period_spinner;

    private JLabel purchase_date_label;
    private JFormattedTextField purchase_date_field;

    private JLabel description_label;
    private JScrollPane description_scroll;
    private JTextArea description_area;

    private JPanel buttons_panel;
    private JButton save_button;
    private JButton delete_button;
    private JButton cancel_button;
    private JLabel supplier_label;
    private JComboBox<Supplier> supplier_combo;

    private final DatePicker purchase_picker;
    private final DefaultComboBoxModel<String> deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<Supplier> supplierTypeComboBoxModel = new DefaultComboBoxModel<>();

    @Getter
    private String barcode;

    private boolean updated = false;
    private final PartService partService;

    public PartEditUI() {
        super((JFrame) null, "Parça Ekle/Güncelle", true);
        partService = ServiceManager.getPartService();
        purchase_picker  = new DatePicker();

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        setSize((int) (screen_size.width * 0.4), (int) (screen_size.height * 0.8));
        setLocationRelativeTo(null);

        init();
        setContentPane(main_panel);
    }

    private void init() {

        // Barkod üretici butonu
        JButton generate_barcode_button = new JButton(new FlatSVGIcon("icon/barcode.svg", 24, 24));
        generate_barcode_button.setToolTipText("Rastgele barkod üret");
        generate_barcode_button.addActionListener(e -> {
            if (barcode_field.isEditable()) {
                String randomBarcode = generateRandomBarcode();
                barcode_field.setText(randomBarcode);
                handleBarcodeInput(randomBarcode);
            }
        });
        barcode_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, generate_barcode_button);
        barcode_field.addActionListener(e -> handleBarcodeInput(barcode_field.getText()));

        device_type_combo.setModel(deviceTypeComboBoxModel);
        loadDeviceTypes();
        device_type_combo.setSelectedItem(null);

        supplier_combo.setModel(supplierTypeComboBoxModel);
        loadSuppliers();
        supplier_combo.setSelectedItem(null);

        // DatePicker bağlantısı
        purchase_picker.setEditor(purchase_date_field);

        // Butonlar
        save_button.addActionListener(e -> part_save_cmd());
        delete_button.addActionListener(e -> part_delete_cmd());
        cancel_button.addActionListener(e -> dispose());
    }

    private void part_save_cmd() {
        if (validateForm()) {
            Part partToSave = getPartFromForm();
            boolean success = partService.savePart(partToSave, updated);

            if (success) {
                barcode_info.setText("[Barkod Numarası Bekleniyor]");
                if (updated) {
                    situation_label.setText("Durum: Ürün başarıyla güncellendi.");
                    situation_label.setForeground(new Color(0, 128, 0)); // yeşil
                } else {
                    situation_label.setText("Durum: Yeni ürün kaydınız başarıyla tamamlandı.");
                    situation_label.setForeground(new Color(0, 128, 0)); // yeşil
                }
                clearFormExceptBarcode();
                barcode = null;
            } else {
                JOptionPane.showMessageDialog(this, "Ürün kaydedilemedi. Lütfen tekrar deneyin.",
                        "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void part_delete_cmd() {
        int result = JOptionPane.showConfirmDialog(this,
                "Bu ürünü silmek istediğinize emin misiniz?",
                "Ürünü Sil", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            boolean success = partService.deletePartByBarcode(barcode);
            if (success) {
                situation_label.setText("Durum: Ürün başarıyla silindi.");
                situation_label.setForeground(Color.RED);
                clearFormExceptBarcode();
            } else {
                JOptionPane.showMessageDialog(this, "Ürün silinemedi. Lütfen tekrar deneyin.",
                        "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean validateForm() {
        if (barcode.isEmpty()) {
            showValidationError("Barkod boş olamaz.");
            return false;
        }

        if (brand_field.getText().trim().isEmpty()) {
            showValidationError("Marka boş olamaz.");
            return false;
        }

        if (name_field.getText().trim().isEmpty()) {
            showValidationError("Ürün adı boş olamaz.");
            return false;
        }

        double purchasePrice = (double) purchase_price_field.getValue();
        int stock = (int) stock_spinner.getValue();
        int minStock = (int) min_stock_spinner.getValue();

        if (purchasePrice < 0) {
            showValidationError("Alış fiyatı negatif olamaz.");
            return false;
        }
        if (stock < 0) {
            showValidationError("Stok negatif olamaz.");
            return false;
        }
        if (minStock < 0) {
            showValidationError("Min stok negatif olamaz.");
            return false;
        }
        if (stock < minStock) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Stok miktarı minimum stok seviyesinin altında. Devam etmek istiyor musunuz?",
                    "Uyarı", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            return confirm == JOptionPane.YES_OPTION;
        }

        return true;
    }

    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(this, message, "Doğrulama Hatası", JOptionPane.WARNING_MESSAGE);
    }

    private void editSetup(Part part) {
        barcode_field.setText(part.getBarcode());
        brand_field.setText(part.getBrand());
        name_field.setText(part.getName());
        device_type_combo.setSelectedItem(part.getDevice_type());
        models_field.setText(part.getModels());
        purchase_price_field.setValue(part.getPurchase_price());
        sale_price_field.setValue(part.getSale_price());
        stock_spinner.setValue(part.getStock());
        min_stock_spinner.setValue(part.getMin_stock());
        warranty_period_spinner.setValue(part.getWarranty_period());
        if (part.getPurchase_date() != null)
            purchase_picker.setSelectedDate(part.getPurchase_date());
        description_area.setText(part.getDescription());

        // Supplier seçimi
        SupplierService supplierService = ServiceManager.getSupplierService();
        Optional<Supplier> supplier = supplierService.get(part.getSupplier_id());
        supplier.ifPresent(value -> supplier_combo.setSelectedItem(value));

    }

    public Part getPartFromForm() {

        String brand = brand_field.getText().trim();
        String name = name_field.getText().trim();

        Part p = new Part(barcode, brand, name);

        p.setBarcode(barcode);
        p.setBrand(brand);
        Supplier selectedSupplier = (Supplier) supplier_combo.getSelectedItem();
        if (selectedSupplier != null) {
            p.setSupplier_id(selectedSupplier.getId());
        }
        p.setName(name);
        p.setDevice_type((String) device_type_combo.getSelectedItem());
        p.setModels(models_field.getText().trim());
        p.setPurchase_price((Double) purchase_price_field.getValue());
        p.setSale_price((Double) sale_price_field.getValue());
        p.setStock((Integer) stock_spinner.getValue());
        p.setMin_stock((Integer) min_stock_spinner.getValue());
        p.setWarranty_period((Integer) warranty_period_spinner.getValue());
        p.setPurchase_date(purchase_picker.getSelectedDate());
        p.setDescription(description_area.getText().trim());

        return p;
    }

    private void loadDeviceTypes() {
        deviceTypeComboBoxModel.removeAllElements();
        Settings settings = Servicio.getSettings();
        List<String> types = settings.getDevice_types();
        for (String type : types) {
            deviceTypeComboBoxModel.addElement(type);
        }
    }

    private void loadSuppliers() {
        supplierTypeComboBoxModel.removeAllElements();
        SupplierService service = ServiceManager.getSupplierService();
        List<Supplier> suppliers = service.getAll();
        for (Supplier supplier : suppliers) {
            supplierTypeComboBoxModel.addElement(supplier);
        }
    }

    private String generateRandomBarcode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int digit = (i == 0) ? (1 + (int) (Math.random() * 9)) : (int) (Math.random() * 10);
            sb.append(digit);
        }
        return sb.toString();
    }

    public void loadPartByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            showValidationError("Geçerli bir barkod girilmedi.");
            return;
        }
        handleBarcodeInput(barcode);
    }

    private void handleBarcodeInput(String barcode) {
        barcode = barcode.trim();

        if (barcode.isEmpty()) {
            showValidationError("Lütfen barkod girin.");
            barcode = null;
            return;
        }

        Part existingPart = partService.getPartByBarcode(barcode);

        if (existingPart != null) {
            updated = true;
            editSetup(existingPart); // alanları doldur
            situation_label.setText("Durum: '" + barcode + "' barkodlu ürün bulundu. Aşağıdaki değerleri değiştirerek güncelleme yapabilirsiniz.");
            situation_label.setForeground(Color.BLUE);
        } else {
            updated = false;
            clearFormExceptBarcode();
            situation_label.setText("Durum: '" + barcode + "' barkodlu ürün bulunamadı. Ürün adını ve fiyatını girerek yeni ürün ekleyebilirsiniz.");
            situation_label.setForeground(Color.RED);
        }
        barcode_field.setText("");
        barcode_info.setText(barcode);
    }

    private void clearFormExceptBarcode() {
        brand_field.setText("");
        supplier_combo.setSelectedItem(null);
        name_field.setText("");
        device_type_combo.setSelectedItem(null);
        models_field.setText("");
        purchase_price_field.setValue(0.0);
        stock_spinner.setValue(0);
        min_stock_spinner.setValue(0);
        warranty_period_spinner.getValue();
        purchase_picker.clearSelectedDate();
        description_area.setText("");

        barcode_info.setText("[Barkod Numarası Beklenyior]");
    }

    private void createUIComponents() {
        purchase_price_field = new CurrencyField();
        sale_price_field = new CurrencyField();
    }
}
