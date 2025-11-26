package tr.cabro.servicio.application.panels.edit;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.util.SVGIconUIColor;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;
import tr.cabro.servicio.util.Barcode;

import javax.swing.*;
import java.util.List;
import java.util.Optional;

public class PartEditPanel extends AbstractEditPanel<Part> {

    PartService service;

    public PartEditPanel(Part data) {
        super(data);

        service = ServiceManager.getPartService();
    }

    private void handleBarcode(String barcode) {
        if (barcode.isEmpty()) {
            return;
        }

        Part part = service.get(barcode);

        if (part != null) {
            showValidationError(Toast.Type.ERROR, "Bu barkod da bir ürün mevcut.");
            setData(part);
            barcode_field.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
        } else {
            barcode_field.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_SUCCESS);
            clearForm();
        }
    }

//    @Override
//    protected boolean validateForm() {
//        // Barkod kontrolü
//        if (Validator.isEmpty(barcode_field.getText())) {
//            showValidationError("Barkod boş olamaz.");
//            barcode_field.requestFocus();
//            return false;
//        }
//
//        // Marka kontrolü
//        if (Validator.isEmpty(brand_field.getText())) {
//            showValidationError("Marka boş olamaz.");
//            brand_field.requestFocus();
//            return false;
//        }
//
//        // Ürün adı kontrolü
//        if (Validator.isEmpty(name_field.getText())) {
//            showValidationError("Ürün adı boş olamaz.");
//            name_field.requestFocus();
//            return false;
//        }
//
//        // Fiyat ve stok değerlerini al
//        double purchasePrice = (double) purchase_price_field.getValue();
//        int stock = (int) stock_spinner.getValue();
//        int minStock = (int) min_stock_spinner.getValue();
//
//        // Negatif sayı kontrolleri
//        if (Validator.isNegative(purchasePrice)) {
//            showValidationError("Alış fiyatı negatif olamaz.");
//            purchase_date_field.requestFocus();
//            return false;
//        }
//        if (Validator.isNegative(stock)) {
//            showValidationError("Stok negatif olamaz.");
//            stock_spinner.requestFocus();
//            return false;
//        }
//        if (Validator.isNegative(minStock)) {
//            showValidationError("Min stok negatif olamaz.");
//            min_stock_spinner.requestFocus();
//            return false;
//        }
//
//        // Minimum stok uyarısı
//        if (stock < minStock) {
//            int confirm = JOptionPane.showConfirmDialog(
//                    this,
//                    "Stok miktarı minimum stok seviyesinin altında. Devam etmek istiyor musunuz?",
//                    "Uyarı",
//                    JOptionPane.YES_NO_OPTION,
//                    JOptionPane.WARNING_MESSAGE
//            );
//            return confirm == JOptionPane.YES_OPTION;
//        }
//        return true;
//    }

    @Override
    protected Part collectFormData(@NonNull Part data) {

        data.setBrand(brand_field.getText().trim());
        data.setName(name_field.getText().trim());
        data.setBarcode(barcode_field.getText().trim());

        Supplier selectedSupplier = (Supplier) supplier_combo.getSelectedItem();
        if (selectedSupplier != null) {
            data.setSupplierId(selectedSupplier.getId());
        }
        data.setDeviceType((String) device_type_combo.getSelectedItem());
        data.setModel(models_field.getText().trim());
        data.setPurchasePrice((Double) purchase_price_field.getValue());
        data.setSalePrice((Double) sale_price_field.getValue());
        data.setStock((Integer) stock_spinner.getValue());
        data.setMinStock((Integer) min_stock_spinner.getValue());
        data.setWarrantyPeriod((Integer) warranty_period_spinner.getValue());
        data.setPurchaseDate(purchase_picker.getSelectedDate());
        data.setDescription(description_area.getText().trim());

        return data;
    }

    @Override
    public void populateFormWith(Part data) {
        barcode_field.setText(data.getBarcode());
        brand_field.setText(data.getBrand());
        name_field.setText(data.getName());
        device_type_combo.setSelectedItem(data.getDeviceType());
        models_field.setText(data.getModel());
        purchase_price_field.setValue(data.getPurchasePrice());
        sale_price_field.setValue(data.getSalePrice());
        stock_spinner.setValue(data.getStock());
        min_stock_spinner.setValue(data.getMinStock());
        warranty_period_spinner.setValue(data.getWarrantyPeriod());
        if (data.getPurchaseDate() != null)
            purchase_picker.setSelectedDate(data.getPurchaseDate());
        description_area.setText(data.getDescription());

        // Supplier seçimi
        Optional<Supplier> supplier = ServiceManager.getSupplierService().get(data.getSupplierId());
        supplier_combo.setSelectedItem(supplier.orElse(null));
    }

    @Override
    public void clearForm() {
        brand_field.setText("");
        supplier_combo.setSelectedItem(null);
        name_field.setText("");
        device_type_combo.setSelectedItem(null);
        models_field.setText("");
        purchase_price_field.setValue(0.0);
        stock_spinner.setValue(1);
        min_stock_spinner.setValue(0);
        warranty_period_spinner.getValue();
        purchase_picker.clearSelectedDate();
        description_area.setText("");
    }

    @Override
    protected Part createEmptyObject() {
        return new Part();
    }

    @Override
    protected void initComponent() {
        setLayout(new MigLayout("wrap 2, width 600", "[grow,fill][grow,fill]", "[]10[]"));

        barcode_field = new JTextField();
        barcode_field.setHorizontalAlignment(SwingConstants.CENTER);
        barcode_field.addActionListener(e -> handleBarcode(barcode_field.getText().trim()));
        JButton generate_barcode_button = new JButton(new SVGIconUIColor("icons/barcode.svg", 0.03f, "MenuItem.foreground"));
        generate_barcode_button.setToolTipText("Rastgele barkod üret");
        generate_barcode_button.addActionListener(e -> {
            if (barcode_field.isEditable()) {
                String randomBarcode = Barcode.generate();
                barcode_field.setText(randomBarcode);
            }
        });
        barcode_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, generate_barcode_button);
        barcode_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Parça barkodunu okutunuz veya girip Enter'a basınız.");

        add(barcode_field, "grow, push, h 40!, wrap, span 2");

        add(new JLabel("Parça Adı"), "span, split 2");
        name_field = new JTextField();
        add(name_field, "span, growx");

        add(new JLabel("Parça Markası"));
        brand_field = new JTextField();
        add(brand_field);

        add(new JLabel("Tedarikçi"));
        supplierTypeComboBoxModel = new DefaultComboBoxModel<>();
        supplierTypeComboBoxModel.removeAllElements();
        List<Supplier> suppliers = ServiceManager.getSupplierService().getAll();
        for (Supplier supplier : suppliers) {
            supplierTypeComboBoxModel.addElement(supplier);
        }
        supplier_combo = new JComboBox<>(supplierTypeComboBoxModel);
        add(supplier_combo);

        add(new JLabel("Cihaz Türü"));
        deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
        deviceTypeComboBoxModel.removeAllElements();
        DeviceSettings settings = Servicio.getDeviceSettings();
        List<String> types = settings.getTypes();
        for (String type : types) {
            deviceTypeComboBoxModel.addElement(type);
        }
        device_type_combo = new JComboBox<>(deviceTypeComboBoxModel);
        add(device_type_combo);

        add(new JLabel("Uyumlu Modeller"));
        models_field = new JTextField();
        add(models_field);

        add(new JLabel("Alış Fiyatı (₺)"));
        purchase_price_field = new CurrencyField();
        add(purchase_price_field);

        add(new JLabel("Satış Fiyatı (₺)"));
        sale_price_field = new CurrencyField();
        add(sale_price_field);

        add(new JLabel("Stok"));
        stock_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
        add(stock_spinner);

        add(new JLabel("Minimum Stok"));
        min_stock_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
        add(min_stock_spinner);

        add(new JLabel("Garanti Süresi (Ay)"));
        warranty_period_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
        add(warranty_period_spinner);

        add(new JLabel("Alış Tarihi"));
        purchase_date_field = new JFormattedTextField();
        purchase_picker = new DatePicker();
        purchase_picker.setEditor(purchase_date_field);
        add(purchase_date_field);

        add(new JLabel("Açıklama"), "span");
        description_area = new JTextArea(4, 40);
        description_area.setLineWrap(true);
        description_area.setWrapStyleWord(true);
        add(new JScrollPane(description_area), "span, growx");

    }
    private JTextField barcode_field;

    private JTextField name_field;

    private JTextField brand_field;

    private JComboBox<String> device_type_combo;

    private JTextField models_field;

    private JFormattedTextField purchase_price_field;
    private JFormattedTextField sale_price_field;

    private JSpinner stock_spinner;
    private JSpinner min_stock_spinner;

    private JSpinner warranty_period_spinner;

    private JFormattedTextField purchase_date_field;

    private JTextArea description_area;

    private JComboBox<Supplier> supplier_combo;

    private DatePicker purchase_picker;
    private DefaultComboBoxModel<String> deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
    private DefaultComboBoxModel<Supplier> supplierTypeComboBoxModel = new DefaultComboBoxModel<>();
}
