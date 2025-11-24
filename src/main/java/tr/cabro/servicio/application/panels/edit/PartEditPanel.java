package tr.cabro.servicio.application.panels.edit;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.util.SVGIconUIColor;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;
import tr.cabro.servicio.util.Barcode;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;

public class PartEditPanel extends AbstractEditPanel<Part> {

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
    protected Part collectFormData() {
        String brand = brand_field.getText().trim();
        String name = name_field.getText().trim();
        String barcode = barcode_field.getText().trim();

        Part p = new Part(barcode, brand, name);

        p.setBarcode(barcode);
        p.setBrand(brand);
        Supplier selectedSupplier = (Supplier) supplier_combo.getSelectedItem();
        if (selectedSupplier != null) {
            p.setSupplierId(selectedSupplier.getId());
        }
        p.setName(name);
        p.setDeviceType((String) device_type_combo.getSelectedItem());
        p.setModel(models_field.getText().trim());
        p.setPurchasePrice((Double) purchase_price_field.getValue());
        p.setSalePrice((Double) sale_price_field.getValue());
        p.setStock((Integer) stock_spinner.getValue());
        p.setMinStock((Integer) min_stock_spinner.getValue());
        p.setWarrantyPeriod((Integer) warranty_period_spinner.getValue());
        p.setPurchaseDate(purchase_picker.getSelectedDate());
        p.setDescription(description_area.getText().trim());

        return p;
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

        barcode_info.setText("[Barkod Numarası Beklenyior]");
    }

    @Override
    protected void initComponent() {
        setLayout(new MigLayout("wrap 2, width 600", "[grow,fill][grow,fill]", "[]10[]"));

        JPanel content = new JPanel(new MigLayout("wrap 2", "[grow,fill][grow,fill]", "[]10[]"));

        JPanel panel = new JPanel(new MigLayout("wrap 1", "[grow,fill]", "[]5[]5[]10[]"));
        JLabel barcodeLabel = new JLabel("Parça barkodunu okutunuz veya girip Enter'a basınız:");
        barcode_field = new JTextField();
        JButton generate_barcode_button = new JButton(new SVGIconUIColor("icons/barcode.svg", 0.03f, "MenuItem.foreground"));
        generate_barcode_button.setToolTipText("Rastgele barkod üret");
        generate_barcode_button.addActionListener(e -> {
            if (barcode_field.isEditable()) {
                String randomBarcode = Barcode.generate();
                barcode_field.setText(randomBarcode);
            }
        });
        barcode_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, generate_barcode_button);

        situation_label = new JTextPane();
        situation_label.setText("Durum: Parça barkodunun okutulması bekleniyor...");
        situation_label.setBackground(null);

        JPanel infoPanel = new JPanel(new MigLayout("insets 0", "[right][grow,fill]", "[]"));
        barcode_info_label = new JLabel("Parça Barkodu:");
        barcode_info_label.setFont(barcode_info_label.getFont().deriveFont(Font.BOLD, 16));
        barcode_info = new JLabel("[Barkod Numarası Bekleniyor]");
        barcode_info.setFont(barcode_info.getFont().deriveFont(Font.BOLD, 20));
        barcode_info.setForeground(new Color(0x12, 0x8B, 0xB8));

        infoPanel.add(barcode_info_label);
        infoPanel.add(barcode_info);

        panel.add(barcodeLabel);
        panel.add(barcode_field, "growx, h 40!");
        panel.add(situation_label);
        panel.add(infoPanel, "growx");


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
        List<Supplier> suppliers = ServiceManager.getSupplierService().getAll();
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
        stock_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
        content.add(stock_spinner);

        content.add(new JLabel("Minimum Stok"));
        min_stock_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
        content.add(min_stock_spinner);

        content.add(new JLabel("Garanti Süresi (Ay)"));
        warranty_period_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
        content.add(warranty_period_spinner);

        content.add(new JLabel("Alış Tarihi"));
        purchase_date_field = new JFormattedTextField();
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
    private JTextField barcode_field;
    private JTextPane situation_label;
    private JLabel barcode_info_label;
    private JLabel barcode_info;

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
