package tr.cabro.servicio.application.panels.edit;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Barcode;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

public class PartEditPanel extends AbstractEditPanel<Part> {

    private final PartService service;

    // Arayüz Bileşenleri
    private JTextField barcode_field;
    private JTextField name_field;
    private JTextField brand_field;
    private JComboBox<DeviceType> device_type_combo;
    private JTextField models_field;
    private JFormattedTextField purchase_price_field;
    private JFormattedTextField sale_price_field;
    private JSpinner stock_spinner;
    private JSpinner min_stock_spinner;
    private JSpinner warranty_period_spinner;
    private JTextArea description_area;
    private JComboBox<Supplier> supplier_combo;
    private DatePicker purchase_picker;

    public PartEditPanel(Part data) {
        super(data);
        service = ServiceManager.getPartService();
    }

    private void handleBarcode(String barcode) {
        if (barcode.isEmpty()) {
            return;
        }

        service.get(barcode).thenAccept(part -> {
            if (part.isPresent()) {
                SwingUtilities.invokeLater(() -> {
                    showValidationError(Toast.Type.ERROR, "Bu barkodda bir ürün mevcut.");
                    setData(part.get());
                    barcode_field.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    barcode_field.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_SUCCESS);
                    clearForm();
                });
            }
        });
    }

    // --- KESİN ÇÖZÜM: Tedarikçi Yükleme Metodu ---
    private void loadSuppliers(Long selectedSupplierId) {
        ServiceManager.getSupplierService().getAll().thenAccept(suppliers -> {
            SwingUtilities.invokeLater(() -> {
                supplier_combo.removeAllItems(); // Kutuyu temizle

                Supplier target = null;
                for (Supplier s : suppliers) {
                    supplier_combo.addItem(s); // Nesneyi doğrudan ekle
                    if (s.getId() == selectedSupplierId) {
                        target = s; // Eşleşen ID varsa hedef olarak belirle
                    }
                }

                // Hedef varsa seçili yap, yoksa hiçbir şeyi seçme
                if (target != null) {
                    supplier_combo.setSelectedItem(target);
                } else {
                    supplier_combo.setSelectedIndex(-1);
                }
            });
        }).exceptionally(ex -> {
            Servicio.getLogger().error("Tedarikçi listesi combobox'a yüklenemedi", ex);
            return null;
        });
    }

    @Override
    protected Part collectFormData(@NonNull Part data) {
        data.setName(name_field.getText().trim());
        data.setBarcode(barcode_field.getText().trim());

        Supplier selectedSupplier = (Supplier) supplier_combo.getSelectedItem();
        if (selectedSupplier != null) {
            data.setSupplierId(selectedSupplier.getId());
        } else {
            data.setSupplierId(0L);
        }

        data.setModelCompatibility(models_field.getText().trim());
        data.setPurchasePrice((BigDecimal) purchase_price_field.getValue());
        data.setSalePrice((BigDecimal) sale_price_field.getValue());
        data.setStockQuantity((Integer) stock_spinner.getValue());
        data.setMinStockLevel((Integer) min_stock_spinner.getValue());
        //data.(purchase_picker.getSelectedDate());
        data.setDescription(description_area.getText().trim());

        return data;
    }

    @Override
    public void populateFormWith(Part data) {
        barcode_field.setText(data.getBarcode());
        name_field.setText(data.getName());
        models_field.setText(data.getModelCompatibility());
        purchase_price_field.setValue(data.getPurchasePrice());
        sale_price_field.setValue(data.getSalePrice());
        stock_spinner.setValue(data.getStockQuantity());
        min_stock_spinner.setValue(data.getMinStockLevel());
//        if (data.get() != null)
//            purchase_picker.setSelectedDate(data.getPurchaseDate());
        description_area.setText(data.getDescription());

        // Tedarikçileri yükle ve varsa seç
        loadSuppliers(data.getSupplierId());
    }

    @Override
    public void clearForm() {
        brand_field.setText("");
        supplier_combo.setSelectedIndex(-1);
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

        JButton generate_barcode_button = new JButton(new Ikon("icons/barcode.svg", 0.03f, "MenuItem.foreground"));
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
        supplier_combo = new JComboBox<>();
        supplier_combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Supplier) {
                    setText(((Supplier) value).getBusinessName());
                } else if (value == null) {
                    setText("Seçiniz...");
                }
                return this;
            }
        });
        add(supplier_combo);

        add(new JLabel("Cihaz Türü"));
        DefaultComboBoxModel<DeviceType> deviceTypeComboBoxModel = new DefaultComboBoxModel<>();

        DeviceDictionaryManager deviceDictService = ServiceManager.getDeviceDictionaryManager();
        deviceDictService.getAllTypes().thenAccept(deviceTypes -> {
            SwingUtilities.invokeLater(() -> {
                deviceTypes.forEach(deviceTypeComboBoxModel::addElement);
            });
        });

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
        JFormattedTextField purchase_date_field = new JFormattedTextField();
        purchase_picker = new DatePicker();
        purchase_picker.setEditor(purchase_date_field);
        add(purchase_date_field);

        add(new JLabel("Açıklama"), "span");
        description_area = new JTextArea(4, 40);
        description_area.setLineWrap(true);
        description_area.setWrapStyleWord(true);
        add(new JScrollPane(description_area), "span, growx");
    }
}