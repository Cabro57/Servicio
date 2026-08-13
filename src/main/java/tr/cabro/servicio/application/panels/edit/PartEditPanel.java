package tr.cabro.servicio.application.panels.edit;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.service.PartCategoryManager;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.util.Barcode;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class PartEditPanel extends AbstractEditPanel<Part> {

    private static final DecimalFormat TRY_FORMAT = new DecimalFormat("#,##0.00");

    private PartService partService;
    private SupplierService supplierService;
    private PartCategoryManager partCategoryService;

    // Seçilebilir para birimlerinin TL karşılığı (ExchangeRateManager'dan yüklenir)
    private final Map<String, BigDecimal> ratesCache = new HashMap<>();

    // Arayüz Bileşenleri
    private JTextField barcode_field;
    private JTextField name_field;
    private JComboBox<PartCategory> category_combo;
    private JComboBox<DeviceType> device_type_combo;
    private JTextField models_field;
    private CurrencyField purchase_price_field;
    private CurrencyField sale_price_field;
    private JLabel purchase_try_label;
    private JLabel sale_try_label;
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

    private void loadExchangeRates() {
        ServiceManager.getExchangeRateManager().getAll().thenAccept(rates -> SwingUtilities.invokeLater(() -> {
            ratesCache.clear();
            rates.forEach(r -> ratesCache.put(r.getCurrencyCode(), r.getRate()));
            updatePurchaseTryLabel();
            updateSaleTryLabel();
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Döviz kurları yüklenemedi", ex);
            return null;
        });
    }

    private void updatePurchaseTryLabel() {
        updateTryLabel(purchase_price_field, purchase_try_label);
    }

    private void updateSaleTryLabel() {
        updateTryLabel(sale_price_field, sale_try_label);
    }

    private void updateTryLabel(CurrencyField field, JLabel label) {
        String code = field.getSelectedCurrency();
        if ("TRY".equals(code)) {
            label.setText(" ");
            return;
        }
        BigDecimal rate = ratesCache.get(code);
        if (rate == null || rate.signum() <= 0) {
            label.setText("Bu döviz için kur tanımlı değil (Ayarlar > Döviz Kurları)");
            return;
        }
        BigDecimal tl = toBigDecimal(field.getValue()).multiply(rate);
        label.setText("≈ " + TRY_FORMAT.format(tl) + " ₺");
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

    private void loadCategories(Long selectedCategoryId) {
        partCategoryService.getAll().thenAccept(categories -> SwingUtilities.invokeLater(() -> {
            category_combo.removeAllItems();

            PartCategory target = null;
            for (PartCategory c : categories) {
                category_combo.addItem(c);
                if (c.getId().equals(selectedCategoryId)) {
                    target = c;
                }
            }

            category_combo.setSelectedItem(target);
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Kategori listesi combobox'a yüklenemedi", ex);
            return null;
        });
    }

    private void onAddCategory() {
        DialogHelper.prompt(this, "category.add.title", "category.add.label", "", name -> {
            if (name == null || name.trim().isEmpty()) return;

            partCategoryService.add(name.trim()).thenAccept(id ->
                    loadCategories((long) id)
            ).exceptionally(ex -> ErrorHandler.handle(this, "Kategori eklenemedi", ex));
        });
    }

    @Override
    protected boolean validateForm() {
        // Barkod zorunlu
        if (Validator.isEmpty(barcode_field.getText())) {
            showValidationError("Barkod alanı boş olamaz.");
            barcode_field.requestFocus();
            return false;
        }
        if (Validator.exceedsMaxLength(barcode_field.getText(), 50)) {
            showValidationError("Barkod en fazla 50 karakter olabilir.");
            barcode_field.requestFocus();
            return false;
        }

        // Parça adı zorunlu
        if (Validator.isEmpty(name_field.getText())) {
            showValidationError("Parça adı boş olamaz.");
            name_field.requestFocus();
            return false;
        }
        if (Validator.exceedsMaxLength(name_field.getText(), 255)) {
            showValidationError("Parça adı en fazla 255 karakter olabilir.");
            name_field.requestFocus();
            return false;
        }

        // Alış fiyatı negatif olamaz
        Object ppVal = purchase_price_field.getValue();
        if (ppVal instanceof Number && ((Number) ppVal).doubleValue() < 0) {
            showValidationError("Alış fiyatı negatif olamaz.");
            purchase_price_field.requestFocus();
            return false;
        }

        // Satış fiyatı negatif olamaz
        Object spVal = sale_price_field.getValue();
        if (spVal instanceof Number && ((Number) spVal).doubleValue() < 0) {
            showValidationError("Satış fiyatı negatif olamaz.");
            sale_price_field.requestFocus();
            return false;
        }

        // Döviz seçiliyken kur tanımlı olmalı
        if (!hasUsableRate(purchase_price_field)) {
            showValidationError("Alış fiyatı için seçilen dövizin kuru tanımlı değil. Önce Ayarlar > Döviz Kurları'ndan kur girin.");
            purchase_price_field.requestFocus();
            return false;
        }
        if (!hasUsableRate(sale_price_field)) {
            showValidationError("Satış fiyatı için seçilen dövizin kuru tanımlı değil. Önce Ayarlar > Döviz Kurları'ndan kur girin.");
            sale_price_field.requestFocus();
            return false;
        }

        return true;
    }

    private boolean hasUsableRate(CurrencyField field) {
        String code = field.getSelectedCurrency();
        if ("TRY".equals(code)) return true;
        BigDecimal rate = ratesCache.get(code);
        return rate != null && rate.signum() > 0;
    }

    @Override
    protected Part collectFormData(@NonNull Part data) {
        data.setName(name_field.getText().trim());
        data.setBarcode(barcode_field.getText().trim());

        PartCategory selectedCategory = (PartCategory) category_combo.getSelectedItem();
        data.setCategoryId(selectedCategory != null ? selectedCategory.getId() : null);

        Supplier selectedSupplier = (Supplier) supplier_combo.getSelectedItem();
        if (selectedSupplier != null) {
            data.setSupplierId(selectedSupplier.getId());
        }

        data.setModelCompatibility(models_field.getText().trim());

        // BigDecimal dönüşümleri için güvenli yöntem; döviz seçiliyse TL karşılığı hesaplanır
        applyCurrencyPrice(data, purchase_price_field, Part::setPurchaseCurrency, Part::setPurchasePriceOriginal, Part::setPurchasePrice);
        applyCurrencyPrice(data, sale_price_field, Part::setSaleCurrency, Part::setSalePriceOriginal, Part::setSalePrice);

        data.setStockQuantity((Integer) stock_spinner.getValue());
        data.setMinStockLevel((Integer) min_stock_spinner.getValue());
        data.setDescription(description_area.getText().trim());

        return data;
    }

    /**
     * TRY seçiliyse alandaki tutar doğrudan TL fiyatı olur; döviz seçiliyse ham tutar
     * "original" alana, kur ile çarpılmış TL karşılığı ise kanonik fiyat alanına yazılır.
     */
    private void applyCurrencyPrice(Part data, CurrencyField field,
                                     java.util.function.BiConsumer<Part, String> currencySetter,
                                     java.util.function.BiConsumer<Part, BigDecimal> originalSetter,
                                     java.util.function.BiConsumer<Part, BigDecimal> tryPriceSetter) {
        String code = field.getSelectedCurrency();
        BigDecimal amount = toBigDecimal(field.getValue());
        currencySetter.accept(data, code);

        if ("TRY".equals(code)) {
            originalSetter.accept(data, null);
            tryPriceSetter.accept(data, amount);
        } else {
            originalSetter.accept(data, amount);
            BigDecimal rate = ratesCache.getOrDefault(code, BigDecimal.ZERO);
            tryPriceSetter.accept(data, amount.multiply(rate));
        }
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
        loadCategories(data.getCategoryId());

        Integer st = data.getStockQuantity();
        Integer ms = data.getMinStockLevel();

        populateCurrencyField(purchase_price_field, data.getPurchaseCurrency(), data.getPurchasePriceOriginal(), data.getPurchasePrice());
        populateCurrencyField(sale_price_field, data.getSaleCurrency(), data.getSalePriceOriginal(), data.getSalePrice());
        updatePurchaseTryLabel();
        updateSaleTryLabel();

        if (st != null) stock_spinner.setValue(data.getStockQuantity());
        if (ms != null) min_stock_spinner.setValue(data.getMinStockLevel());

        description_area.setText(data.getDescription());

        // Tedarikçileri yükle ve varsa seç
        loadSuppliers(data.getSupplierId());
    }

    private void populateCurrencyField(CurrencyField field, String currencyCode, BigDecimal originalAmount, BigDecimal tryPrice) {
        if (currencyCode != null && !"TRY".equals(currencyCode) && originalAmount != null) {
            field.setSelectedCurrency(currencyCode);
            field.setValue(originalAmount);
        } else {
            field.setSelectedCurrency("TRY");
            field.setValue(tryPrice != null ? tryPrice : BigDecimal.ZERO);
        }
    }

    @Override
    public void clearForm() {
        loadCategories(null);
        supplier_combo.setSelectedIndex(-1);
        name_field.setText("");
        models_field.setText("");
        purchase_price_field.setSelectedCurrency("TRY");
        purchase_price_field.setValue(0.0);
        sale_price_field.setSelectedCurrency("TRY");
        sale_price_field.setValue(0.0);
        updatePurchaseTryLabel();
        updateSaleTryLabel();
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
        this.partCategoryService = ServiceManager.getPartCategoryManager();

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
        category_combo = new JComboBox<>();
        category_combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PartCategory) {
                    setText(((PartCategory) value).getName());
                } else if (value == null) {
                    setText("Seçiniz...");
                }
                return this;
            }
        });
        JButton addCategoryButton = new JButton(new Ikon("icons/plus.svg", name_field.getFont().getSize()));
        addCategoryButton.setToolTipText("Yeni kategori ekle");
        addCategoryButton.addActionListener(e -> onAddCategory());

        JPanel categoryPanel = new JPanel(new MigLayout("insets 0, fillx", "[grow][pref!]", "[]"));
        categoryPanel.add(category_combo, "growx");
        categoryPanel.add(addCategoryButton);
        add(categoryPanel, "growx");

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

        add(new JLabel("Alış Fiyatı"));
        purchase_price_field = new CurrencyField();
        purchase_try_label = new JLabel(" ");
        purchase_try_label.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -2");
        JPanel purchasePricePanel = new JPanel(new MigLayout("insets 0, wrap 1, fillx", "[grow,fill]"));
        purchasePricePanel.add(purchase_price_field, "growx");
        purchasePricePanel.add(purchase_try_label, "growx");
        add(purchasePricePanel);

        add(new JLabel("Satış Fiyatı"));
        sale_price_field = new CurrencyField();
        sale_try_label = new JLabel(" ");
        sale_try_label.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -2");
        JPanel salePricePanel = new JPanel(new MigLayout("insets 0, wrap 1, fillx", "[grow,fill]"));
        salePricePanel.add(sale_price_field, "growx");
        salePricePanel.add(sale_try_label, "growx");
        add(salePricePanel);

        purchase_price_field.addCurrencyChangeListener(this::updatePurchaseTryLabel);
        sale_price_field.addCurrencyChangeListener(this::updateSaleTryLabel);
        purchase_price_field.addPropertyChangeListener("value", e -> updatePurchaseTryLabel());
        sale_price_field.addPropertyChangeListener("value", e -> updateSaleTryLabel());
        loadExchangeRates();

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