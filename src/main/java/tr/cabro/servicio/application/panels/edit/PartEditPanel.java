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
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.util.Barcode;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

public class PartEditPanel extends AbstractEditPanel<Part> {

    private PartService partService;
    private SupplierService supplierService;

    // Arayüz Bileşenleri
    private JTextField barcode_field;
    private JTextField name_field;
    private JTextField categroy_field;
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

    }

    private void handleBarcode(String barcode) {
        if (barcode.isEmpty()) {
            return;
        }

        partService.get(barcode).thenAccept(part -> {
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
        supplierService.getAll().thenAccept(suppliers -> {
            if (suppliers.isEmpty()) {
                Servicio.getLogger().warn("Tedarikçiler yok");
            }
            SwingUtilities.invokeLater(() -> {
                supplier_combo.removeAllItems();

                Supplier target = null;
                for (Supplier s : suppliers) {
                    supplier_combo.addItem(s); // Nesneyi doğrudan ekle
                    if (s.getId().equals(selectedSupplierId)) {
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
        data.setCategory(categroy_field.getText());

        Supplier selectedSupplier = (Supplier) supplier_combo.getSelectedItem();
        if (selectedSupplier != null) {
            data.setSupplierId(selectedSupplier.getId());
        }

        data.setModelCompatibility(models_field.getText().trim());

        // BigDecimal dönüşümleri için güvenli yöntem
        data.setPurchasePrice(toBigDecimal(purchase_price_field.getValue()));
        data.setSalePrice(toBigDecimal(sale_price_field.getValue()));

        data.setStockQuantity((Integer) stock_spinner.getValue());
        data.setMinStockLevel((Integer) min_stock_spinner.getValue());
        data.setDescription(description_area.getText().trim());

        return data;
    }

    /**
     * Swing bileşenlerinden gelen numeric değerleri güvenli bir şekilde BigDecimal'a dönüştürür.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            // Double, Float, Integer vb. durumları kapsar
            return new BigDecimal(((Number) value).toString());
        }
        return BigDecimal.ZERO;
    }

    @Override
    public void populateFormWith(Part data) {
        barcode_field.setText(data.getBarcode());
        name_field.setText(data.getName());
        models_field.setText(data.getModelCompatibility());
        categroy_field.setText(data.getCategory());

        BigDecimal pp = data.getPurchasePrice();
        BigDecimal sp = data.getSalePrice();
        Integer st = data.getStockQuantity();
        Integer ms = data.getMinStockLevel();

        if (pp != null) purchase_price_field.setValue(data.getPurchasePrice());
        if (sp != null) sale_price_field.setValue(data.getSalePrice());
        if (st != null) stock_spinner.setValue(data.getStockQuantity());
        if (ms != null) min_stock_spinner.setValue(data.getMinStockLevel());

        description_area.setText(data.getDescription());

        // Tedarikçileri yükle ve varsa seç
        loadSuppliers(data.getSupplierId());
    }

    @Override
    public void clearForm() {
        categroy_field.setText("");
        supplier_combo.setSelectedIndex(-1);
        name_field.setText("");
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
        this.partService = ServiceManager.getPartService();
        this.supplierService = ServiceManager.getSupplierService();

        setLayout(new MigLayout("wrap 2, width 600", "[grow,fill][grow,fill]", "[]10[]"));

        barcode_field = new JTextField();
        barcode_field.setHorizontalAlignment(SwingConstants.CENTER);
        barcode_field.addActionListener(e -> handleBarcode(barcode_field.getText().trim()));

        JButton generate_barcode_button = new JButton(new Ikon("icons/dices.svg", 1f));
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

        add(new JLabel("Kategori"));
        categroy_field = new JTextField();
        add(categroy_field);

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

        add(new JLabel("Açıklama"), "span");
        description_area = new JTextArea(4, 40);
        description_area.setLineWrap(true);
        description_area.setWrapStyleWord(true);
        add(new JScrollPane(description_area), "span, growx");
    }
}