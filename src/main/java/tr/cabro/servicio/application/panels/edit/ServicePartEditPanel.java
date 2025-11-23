package tr.cabro.servicio.application.panels.edit;

import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;
import java.util.List;
import java.util.Optional;

public class ServicePartEditPanel extends AbstractEditPanel<AddedPart> {
//    @Override
//    protected boolean validateForm() {
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
//        int stock = (int) amount_spinner.getValue();
//
//        // Negatif sayı kontrolleri
//        if (Validator.isNegative(purchasePrice)) {
//            showValidationError("Alış fiyatı negatif olamaz.");
//            purchase_price_field.requestFocus();
//            return false;
//        }
//        if (Validator.isNegative(stock)) {
//            showValidationError("Stok negatif olamaz.");
//            amount_spinner.requestFocus();
//            return false;
//        }
//
//        return true;
//    }

    @Override
    protected AddedPart collectFormData() {
        String brand = brand_field.getText().trim();
        String name = name_field.getText().trim();
        String barcode = seri_no_field.getText().trim();

        AddedPart p = new AddedPart();

        p.setSerialNo(barcode);
        p.setBrand(brand);
        Supplier selectedSupplier = (Supplier) supplier_combo.getSelectedItem();
        if (selectedSupplier != null) {
            p.setSupplierId(selectedSupplier.getId());
        }
        p.setName(name);
        p.setDeviceType((String) device_type_combo.getSelectedItem());
        p.setModels(models_field.getText().trim());
        p.setPurchasePrice((Double) purchase_price_field.getValue());
        p.setSellingPrice((Double) sale_price_field.getValue());
        p.setAmount((Integer) amount_spinner.getValue());
        p.setWarrantyPeriod((Integer) warranty_period_spinner.getValue());
        p.setPurchaseDate(purchase_picker.getSelectedDate());
        p.setDescription(description_area.getText().trim());

        return p;
    }

    @Override
    public void populateFormWith(AddedPart data) {
        seri_no_field.setText(data.getSerialNo());
        brand_field.setText(data.getBrand());
        name_field.setText(data.getName());
        device_type_combo.setSelectedItem(data.getDeviceType());
        models_field.setText(data.getModels());
        purchase_price_field.setValue(data.getPurchasePrice());
        sale_price_field.setValue(data.getSellingPrice());
        amount_spinner.setValue(data.getAmount());
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
        seri_no_field.setText("");
        brand_field.setText("");
        supplier_combo.setSelectedItem(null);
        name_field.setText("");
        device_type_combo.setSelectedItem(null);
        models_field.setText("");
        purchase_price_field.setValue(0.0);
        amount_spinner.setValue(1);
        warranty_period_spinner.getValue();
        purchase_picker.clearSelectedDate();
        description_area.setText("");
    }

    @Override
    protected void initComponent() {
        setLayout(new MigLayout("wrap 2, width 600", "[grow,fill][grow,fill]", "[]10[]"));

        JPanel content = new JPanel(new MigLayout("wrap 2", "[grow,fill][grow,fill]", "[]10[]"));

        JPanel panel = new JPanel(new MigLayout("wrap 1", "[grow,fill]", "[]5[]5[]10[]"));
        JLabel barcodeLabel = new JLabel("Parça Seri Numarası:");
        seri_no_field = new JTextField();

        panel.add(barcodeLabel);
        panel.add(seri_no_field, "growx, h 40!");

        content.add(panel, "span, growx");

        content.add(new JLabel("Parça Adı"), "span, split 2");
        name_field = new JTextField();
        content.add(name_field, "span, growx");

        content.add(new JLabel("Parça Markası"));
        brand_field = new JTextField();
        content.add(brand_field);

        content.add(new JLabel("Tedarikçi"));
        supplierTypeComboBoxModel = new DefaultComboBoxModel<>();
        supplierTypeComboBoxModel.removeAllElements();
        java.util.List<Supplier> suppliers = ServiceManager.getSupplierService().getAll();
        for (Supplier supplier : suppliers) {
            supplierTypeComboBoxModel.addElement(supplier);
        }
        supplier_combo = new JComboBox<>(supplierTypeComboBoxModel);
        content.add(supplier_combo);

        content.add(new JLabel("Cihaz Türü"));
        deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
        deviceTypeComboBoxModel.removeAllElements();
        DeviceSettings settings = Servicio.getDeviceSettings();
        List<String> types = settings.getTypes();
        for (String type : types) {
            deviceTypeComboBoxModel.addElement(type);
        }
        device_type_combo = new JComboBox<>(deviceTypeComboBoxModel);
        content.add(device_type_combo);

        content.add(new JLabel("Uyumlu Modeller"));
        models_field = new JTextField();
        content.add(models_field);

        content.add(new JLabel("Alış Fiyatı (₺)"));
        purchase_price_field = new CurrencyField();
        content.add(purchase_price_field);

        content.add(new JLabel("Satış Fiyatı (₺)"));
        sale_price_field = new CurrencyField();
        content.add(sale_price_field);

        content.add(new JLabel("Stok"));
        amount_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
        content.add(amount_spinner);

        content.add(new JLabel("Garanti Süresi (Ay)"));
        warranty_period_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
        content.add(warranty_period_spinner);

        content.add(new JLabel("Alış Tarihi"));
        JFormattedTextField purchase_date_field = new JFormattedTextField();
        purchase_picker = new DatePicker();
        purchase_picker.setEditor(purchase_date_field);
        content.add(purchase_date_field);

        content.add(new JLabel("Açıklama"), "span");
        description_area = new JTextArea(4, 40);
        description_area.setLineWrap(true);
        description_area.setWrapStyleWord(true);
        content.add(new JScrollPane(description_area), "span, growx");

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, "span, grow, push");
    }

    private JTextField seri_no_field;
    private JTextField name_field;
    private JTextField brand_field;
    private JComboBox<String> device_type_combo;
    private JTextField models_field;
    private JFormattedTextField purchase_price_field;
    private JFormattedTextField sale_price_field;
    private JSpinner amount_spinner;
    private JSpinner warranty_period_spinner;
    private JTextArea description_area;
    private JComboBox<Supplier> supplier_combo;
    private DatePicker purchase_picker;
    private DefaultComboBoxModel<String> deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
    private DefaultComboBoxModel<Supplier> supplierTypeComboBoxModel = new DefaultComboBoxModel<>();
}
