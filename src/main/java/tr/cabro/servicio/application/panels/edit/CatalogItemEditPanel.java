package tr.cabro.servicio.application.panels.edit;

import tr.cabro.servicio.model.enums.CategoryScope;
import tr.cabro.servicio.model.enums.StockItemKind;
import tr.cabro.servicio.model.enums.SupplierRole;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.PriceFields;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.model.Warehouse;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Barcode;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Parça ({@code PartEditPanel}) ve POS ürünü ({@code ProductEditPanel}) formlarının ortak iskeleti.
 * <p>
 * İki form eskiden satır satır kopyaydı (barkod, ad, kategori, dövizli fiyat, stok, açıklama).
 * Burada dört bölüm kurulur: Tanım, Fiyat, Stok, Açıklama. Alt sınıf yalnızca kendine özgü
 * tanım alanlarını ({@link #addIdentityFields}) ve model eşlemesini ekler.
 * <ul>
 *   <li>Barkod okutulunca (Enter) başka bir kayıtta kullanılıp kullanılmadığı alanın altında yazar;
 *       kullanılıyorsa kayıt engellenir. Boş bırakılırsa kayıtta otomatik barkod üretilir.</li>
 *   <li>Stok yalnızca yeni kayıtta girilir (açılış stoğu, seçilen depoya). Düzenlemede mevcut stok
 *       ve depo dağılımı salt okunur gösterilir; stok kart sayfasındaki "Stok Hareketi" ile değişir.</li>
 * </ul>
 * DİKKAT: {@link #initComponent()} üst sınıfın kurucusundan çağrılır — bu sınıfta ve alt sınıflarda
 * alanlara başlangıç değeri ({@code = ...}) VERİLMEMELİ, aksi halde kurucu bittikten sonra sıfırlanırlar.
 */
public abstract class CatalogItemEditPanel<T> extends AbstractEditPanel<T> {

    protected JTextField barcodeField;
    protected JTextField nameField;
    protected JComboBox<PartCategory> categoryCombo;
    protected PriceFields priceFields;
    protected JSpinner stockSpinner;
    protected JSpinner minStockSpinner;
    protected JTextArea descriptionArea;

    private JLabel barcodeStatus;
    private JLabel barcodeError;
    private JLabel nameError;
    private JLabel stockNote;
    private JComboBox<Warehouse> warehouseCombo;
    private JPanel openingStockCell;
    private JPanel warehouseCell;
    private JPanel currentStockCell;
    private JLabel currentStockValue;
    private JLabel currentStockSplit;

    /** Düzenlenen kaydın kimliği; yeni kayıtta null. Barkod çakışma kontrolünde hariç tutulur. */
    protected Long editingId;
    /** Kayıt açıldığındaki stok (düzenlemede salt okunur gösterilir). Yeni kayıtta null. */
    private Integer originalStock;
    /** Son barkod kontrolünde barkod başka bir kayıtta bulunduysa o kaydın adı. */
    private String barcodeOwner;
    private int barcodeToken;

    protected CatalogItemEditPanel(T data) {
        super(data);
    }

    // -------------------------------------------------------------------------
    // Alt sınıf kancaları
    // -------------------------------------------------------------------------

    /** "Parça" / "Ürün" — mesajlarda kullanılır. */
    protected abstract String itemNoun();

    /** Stok defteri türü: parça ekranında PART, ürün ekranında PRODUCT. */
    protected abstract StockItemKind stockKind();

    /** Kategori listesinin kapsamı: parça ekranında PART, ürün ekranında PRODUCT. */
    protected abstract CategoryScope categoryScope();

    /** Barkodu kullanan kaydı arar: bulunursa {@code [id, ad]}. */
    protected abstract CompletableFuture<Optional<Object[]>> findByBarcode(String barcode);

    /** Tanım bölümüne (ad ve kategoriden sonra) türe özgü alanları ekler; ızgara iki kolonludur. */
    protected abstract void addIdentityFields(JPanel grid);

    /** Türe özgü ek doğrulama; ilk hatalı alanı döndürür, yoksa null. */
    protected JComponent validateExtra() {
        return null;
    }

    // -------------------------------------------------------------------------
    // Arayüz
    // -------------------------------------------------------------------------

    @Override
    protected void initComponent() {
        setLayout(new BorderLayout());
        JPanel form = FormKit.railForm(800);
        add(FormKit.scroll(form), BorderLayout.CENTER);

        form.add(FormKit.rail("Tanım", "Barkodu okutun ya da yazıp Enter'a basın; başka kayıtta varsa uyarılır."), "top");
        form.add(buildIdentitySection());
        form.add(FormKit.separator(), "span 2, growx, gaptop 12, gapbottom 12");

        form.add(FormKit.rail("Fiyat", "Döviz seçilirse TL karşılığı Döviz Kurları ayarındaki kurla hesaplanır."), "top");
        priceFields = new PriceFields();
        form.add(priceFields);
        form.add(FormKit.separator(), "span 2, growx, gaptop 12, gapbottom 12");

        form.add(FormKit.rail("Stok", "Açılış stoğu seçilen depoya yazılır. Sonraki her değişiklik kart sayfasındaki Stok Hareketi ile, sebebiyle kaydedilir."), "top");
        form.add(buildStockSection());
        form.add(FormKit.separator(), "span 2, growx, gaptop 12, gapbottom 12");

        form.add(FormKit.rail("Açıklama", "İsteğe bağlı; iç kullanım içindir."), "top");
        descriptionArea = FormKit.textArea(3);
        form.add(FormKit.areaScroll(descriptionArea));

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(() -> barcodeField.requestFocusInWindow());
            }
        });
    }

    private JPanel buildIdentitySection() {
        JPanel grid = FormKit.grid(2);

        barcodeField = new JTextField();
        barcodeField.putClientProperty(FlatClientProperties.STYLE, "font: +2");
        barcodeField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Okutun veya yazın");
        barcodeField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/barcode.svg", 0.9f));
        JButton generate = new JButton(new Ikon("icons/dices.svg", 0.85f));
        generate.setToolTipText("Barkodu olmayan kayıt için rastgele barkod üret");
        generate.getAccessibleContext().setAccessibleName("Rastgele barkod üret");
        generate.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        generate.addActionListener(e -> {
            barcodeField.setText(Barcode.generate());
            checkBarcode();
        });
        barcodeField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, generate);
        barcodeField.addActionListener(e -> checkBarcode());
        barcodeField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (!e.isTemporary()) checkBarcode();
            }
        });

        barcodeStatus = FormKit.note("Boş bırakılırsa kayıtta otomatik barkod üretilir.");
        barcodeStatus.setIconTextGap(6);
        barcodeError = FormKit.errorLabel();
        JPanel below = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx, hidemode 3", "[grow, fill]", "[][]"));
        below.setOpaque(false);
        below.add(barcodeStatus, "wmin 0");
        below.add(barcodeError, "wmin 0");
        grid.add(FormKit.cell("Barkod", barcodeField, below), "span 2");
        barcodeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                barcodeOwner = null;
                FormKit.clear(barcodeField, barcodeError);
                showBarcodeStatus(null, "Boş bırakılırsa kayıtta otomatik barkod üretilir.", "$Label.disabledForeground",
                        barcodeField.getText().isBlank());
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });

        nameField = new JTextField();
        nameError = FormKit.errorLabel();
        grid.add(FormKit.cell(itemNoun() + " Adı *", nameField, nameError), "span 2");

        categoryCombo = new JComboBox<>();
        categoryCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof PartCategory c ? c.getName() : "Kategori yok");
                return this;
            }
        });
        JButton addCategory = new JButton(new Ikon("icons/plus.svg", 0.8f));
        addCategory.setToolTipText("Yeni kategori ekle");
        addCategory.getAccessibleContext().setAccessibleName("Yeni kategori ekle");
        addCategory.addActionListener(e -> onAddCategory());
        JPanel category = new JPanel(new MigLayout("insets 0, gap 6, fillx", "[grow, fill][]", "[]"));
        category.setOpaque(false);
        category.add(categoryCombo, "wmin 0");
        category.add(addCategory);
        grid.add(FormKit.cell("Kategori", category, null));

        addIdentityFields(grid);
        return grid;
    }

    private JPanel buildStockSection() {
        JPanel grid = FormKit.grid(3);
        stockSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 99999, 1));
        minStockSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 99999, 1));
        warehouseCombo = new JComboBox<>();
        stockNote = FormKit.note(" ");

        currentStockValue = new JLabel("—");
        currentStockValue.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        currentStockSplit = FormKit.note(" ");

        openingStockCell = FormKit.cell("Açılış stoğu", stockSpinner, null);
        warehouseCell = FormKit.cell("Depo", warehouseCombo, null);
        currentStockCell = FormKit.cell("Mevcut stok", currentStockValue, currentStockSplit);
        grid.add(openingStockCell);
        grid.add(warehouseCell);
        grid.add(currentStockCell, "span 2");
        grid.add(FormKit.cell("Minimum Stok", minStockSpinner, FormKit.note("Uyarı eşiği")), "wrap");
        grid.add(stockNote, "span 3, wmin 0");
        stockSpinner.addChangeListener(e -> updateStockNote());
        minStockSpinner.addChangeListener(e -> updateStockNote());
        warehouseCombo.addActionListener(e -> updateStockNote());
        loadWarehouses();
        return grid;
    }

    private void loadWarehouses() {
        ServiceManager.getWarehouseService().getActive().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            warehouseCombo.removeAllItems();
            Warehouse def = null;
            for (Warehouse w : list) {
                warehouseCombo.addItem(w);
                if (w.isDefaultWarehouse()) def = w;
            }
            warehouseCombo.setSelectedItem(def);
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Depo listesi yüklenemedi", ex);
            return null;
        });
    }

    /** Firma seçim listesi: parçada "Tedarikçi", üründe "Toptancı". Boş seçenek {@code emptyText} yazar. */
    protected static JComboBox<Supplier> supplierCombo(String emptyText) {
        JComboBox<Supplier> combo = new JComboBox<>();
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof Supplier s) {
                    setText(s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName() : s.getName());
                } else {
                    setText(emptyText);
                }
                return this;
            }
        });
        return combo;
    }

    /**
     * Listeyi rolü uyan firmalarla doldurur (parça → tedarikçi, ürün → toptancı). Kayıtlı firma
     * sonradan rolünü kaybettiyse de listede kalır; kaydı açmak onu sessizce silmesin.
     */
    protected void loadSupplierCombo(JComboBox<Supplier> combo, boolean forProducts, Long selectedId) {
        ServiceManager.getSupplierService().getAll().thenAccept(all -> SwingUtilities.invokeLater(() -> {
            combo.removeAllItems();
            combo.addItem(null);
            Supplier target = null;
            for (Supplier s : all) {
                SupplierRole role = s.getRole() != null ? s.getRole() : SupplierRole.SUPPLIER;
                boolean fits = forProducts ? role.suppliesProducts() : role.suppliesParts();
                boolean current = s.getId().equals(selectedId);
                if (!fits && !current) continue;
                combo.addItem(s);
                if (current) target = s;
            }
            combo.setSelectedItem(target);
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Firma listesi yüklenemedi", ex);
            return null;
        });
    }

    protected static Long selectedSupplierId(JComboBox<Supplier> combo) {
        Supplier s = (Supplier) combo.getSelectedItem();
        return s != null ? s.getId() : null;
    }

    /** Yeni kayıtta açılış stoğunun yazılacağı depo (null → varsayılan). */
    protected Long selectedOpeningWarehouseId() {
        Warehouse w = (Warehouse) warehouseCombo.getSelectedItem();
        return w != null ? w.getId() : null;
    }

    private void updateStockNote() {
        boolean editing = originalStock != null;
        openingStockCell.setVisible(!editing);
        warehouseCell.setVisible(!editing);
        currentStockCell.setVisible(editing);

        int stock = editing ? originalStock : (Integer) stockSpinner.getValue();
        int min = (Integer) minStockSpinner.getValue();
        String text;
        String color = "$Label.disabledForeground";
        if (editing) {
            text = "Stok bu formda değişmez. Giriş, çıkış, sayım ve transfer için kart sayfasında Stok Hareketi'ni kullanın.";
        } else {
            Warehouse w = (Warehouse) warehouseCombo.getSelectedItem();
            text = stock > 0
                    ? stock + " adet açılış stoğu " + (w != null ? w.getName() : "varsayılan depo") + " deposuna yazılacak."
                    : "Stoksuz kaydedilecek; stok sonra kart sayfasından girilir.";
        }
        if (min > 0 && stock <= min) {
            text += "  Stok minimum seviyede ya da altında.";
            color = "$Servicio.warningColor";
        }
        stockNote.setText(text);
        stockNote.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: " + color);
    }

    /** Düzenlemede mevcut stoğun depo dağılımı: "Ana Depo 9  ·  Vitrin 5". */
    private void loadStockSplit(Long id) {
        currentStockSplit.setText(" ");
        ServiceManager.getStockService().getLevels(stockKind(), id).thenAccept(levels -> SwingUtilities.invokeLater(() -> {
            if (!java.util.Objects.equals(id, editingId)) return;
            String split = levels.stream().filter(l -> l.getQuantity() != 0)
                    .map(l -> l.getWarehouseName() + " " + l.getQuantity())
                    .collect(java.util.stream.Collectors.joining("  ·  "));
            currentStockSplit.setText(split.isEmpty() ? "Hiçbir depoda yok" : split);
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Depo dağılımı okunamadı", ex);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Barkod kontrolü
    // -------------------------------------------------------------------------

    private void checkBarcode() {
        String barcode = barcodeField.getText().trim();
        if (barcode.isEmpty()) return;
        int token = ++barcodeToken;
        findByBarcode(barcode).thenAccept(found -> SwingUtilities.invokeLater(() -> {
            if (token != barcodeToken || !barcode.equals(barcodeField.getText().trim())) return;
            Optional<Object[]> other = found.filter(r -> !java.util.Objects.equals(r[0], editingId));
            if (other.isPresent()) {
                barcodeOwner = String.valueOf(other.get()[1]);
                FormKit.fail(barcodeField, barcodeError, "Bu barkod \"" + barcodeOwner + "\" kaydında kullanılıyor.");
                showBarcodeStatus(null, null, null, false);
            } else {
                barcodeOwner = null;
                showBarcodeStatus("icons/circle-check.svg", "Barkod kullanılabilir.", "$Servicio.successColor", true);
            }
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Barkod kontrolü yapılamadı", ex);
            return null;
        });
    }

    private void showBarcodeStatus(String icon, String text, String color, boolean visible) {
        barcodeStatus.setVisible(visible);
        if (!visible) return;
        barcodeStatus.setText(text);
        barcodeStatus.setIcon(icon != null ? new Ikon(icon, 0.75f, color.substring(1)) : null);
        barcodeStatus.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: " + color);
    }

    // -------------------------------------------------------------------------
    // Kategori
    // -------------------------------------------------------------------------

    protected void loadCategories(Long selectedCategoryId) {
        ServiceManager.getPartCategoryManager().getFor(categoryScope(), selectedCategoryId).thenAccept(categories -> SwingUtilities.invokeLater(() -> {
            categoryCombo.removeAllItems();
            categoryCombo.addItem(null);
            PartCategory target = null;
            for (PartCategory c : categories) {
                categoryCombo.addItem(c);
                if (c.getId().equals(selectedCategoryId)) target = c;
            }
            categoryCombo.setSelectedItem(target);
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Kategori listesi yüklenemedi", ex);
            return null;
        });
    }

    private void onAddCategory() {
        DialogHelper.prompt(this, "category.add.title", "category.add.label", "", name -> {
            if (name == null || name.trim().isEmpty()) return;
            ServiceManager.getPartCategoryManager().add(name.trim(), categoryScope()).thenAccept(id ->
                    loadCategories((long) id)
            ).exceptionally(ex -> ErrorHandler.handle(this, "Kategori eklenemedi", ex));
        });
    }

    protected Long selectedCategoryId() {
        PartCategory c = (PartCategory) categoryCombo.getSelectedItem();
        return c != null ? c.getId() : null;
    }

    // -------------------------------------------------------------------------
    // Doğrulama ve ortak veri
    // -------------------------------------------------------------------------

    @Override
    protected boolean validateForm() {
        JComponent first = null;

        String barcode = barcodeField.getText().trim();
        if (barcodeOwner != null) {
            first = FormKit.fail(barcodeField, barcodeError, "Bu barkod \"" + barcodeOwner + "\" kaydında kullanılıyor.");
        } else if (Validator.exceedsMaxLength(barcode, 50)) {
            first = FormKit.fail(barcodeField, barcodeError, "En fazla 50 karakter olabilir.");
        }

        if (Validator.isEmpty(nameField.getText())) {
            JComponent c = FormKit.fail(nameField, nameError, itemNoun() + " adını yazın.");
            if (first == null) first = c;
        } else if (Validator.exceedsMaxLength(nameField.getText(), 255)) {
            JComponent c = FormKit.fail(nameField, nameError, "En fazla 255 karakter olabilir.");
            if (first == null) first = c;
        }

        JComponent price = priceFields.validateRates();
        if (first == null) first = price;
        JComponent extra = validateExtra();
        if (first == null) first = extra;

        if (first != null) {
            first.requestFocusInWindow();
            return false;
        }
        // Barkodu olmayan kayıt (elle fiyat etiketi vb.) için zar düğmesindeki üretici kullanılır.
        if (barcode.isEmpty()) barcodeField.setText(Barcode.generate());
        return true;
    }

    /** Ortak alanları doldurur; {@code stock} kayıt açılışındaki miktar olarak saklanır. */
    protected void populateCommon(Long id, String barcode, String name, Long categoryId,
                                  Integer stock, Integer minStock, String description) {
        editingId = id;
        barcodeField.setText(barcode != null ? barcode : "");
        nameField.setText(name != null ? name : "");
        loadCategories(categoryId);
        originalStock = (id != null && id > 0) ? (stock != null ? stock : 0) : null;
        stockSpinner.setValue(originalStock == null && stock != null ? stock : 0);
        currentStockValue.setText((originalStock != null ? originalStock : 0) + " adet");
        if (originalStock != null) loadStockSplit(id);
        minStockSpinner.setValue(minStock != null ? minStock : 0);
        descriptionArea.setText(description != null ? description : "");
        barcodeOwner = null;
        FormKit.clear(barcodeField, barcodeError);
        FormKit.clear(nameField, nameError);
        updateStockNote();
    }

    protected void clearCommon() {
        populateCommon(null, "", "", null, 0, 0, "");
        priceFields.clear();
    }
}
