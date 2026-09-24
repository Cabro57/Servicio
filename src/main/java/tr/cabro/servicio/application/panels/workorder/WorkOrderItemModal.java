package tr.cabro.servicio.application.panels.workorder;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.SegmentedButtons;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Labor;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.WorkOrderItem;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.SourceType;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * "Parça / İşçilik Ekle" penceresi. Tek pencere, iki yarı: solda katalog (stoktaki parçalar, hazır
 * işçilikler ya da elle giriş), sağda "Eklenecekler" sepeti. Bir satıra tıklamak (ya da arama
 * kutusunda Enter) kalemi sepete koyar; adet ve birim fiyat sepette değişir; "Ekle" hepsini tek
 * seferde kaydeder. Eskiden parça seçmek pencereyi kapatıp ikinci bir düzenleme penceresi açıyordu,
 * hazır işçilik ise sessizce eklenip listeyi güncellemiyordu.
 * <p>
 * Klavye: arama kutusuna yazıp ↑ ↓ ile gezin, Enter sepete koyar, Ctrl+Enter kaydeder.
 */
public class WorkOrderItemModal extends JPanel {

    private static final String MODAL_ID = "itemAddModal";
    private static final Locale TR = Locale.forLanguageTag("tr");

    private enum Mode { PART, LABOR, MANUAL }

    /** Katalog satırı: parça ya da hazır işçilik. */
    private record Entry(Part part, Labor labor, boolean compatible) {
        boolean isPart() { return part != null; }
        String name() { return isPart() ? part.getName() : labor.getName(); }
        BigDecimal price() {
            BigDecimal p = isPart() ? part.getSalePrice() : labor.getDefaultPrice();
            return p != null ? p : BigDecimal.ZERO;
        }
        int stock() { return isPart() && part.getStockQuantity() != null ? part.getStockQuantity() : Integer.MAX_VALUE; }
        Object key() { return isPart() ? "P" + part.getId() : "L" + labor.getId(); }
    }

    /** Sepet satırı: kaleme dönüşecek durum + satırın bileşenleri. */
    private static final class Line {
        final Entry entry;          // katalogtan geldiyse dolu, manuelde null
        final ItemType type;
        final String name;
        String serialNo;
        JTextField serialField;
        final BigDecimal purchasePrice;
        int qty = 1;
        BigDecimal unit;
        CurrencyField priceField;
        JLabel qtyLabel, totalLabel;
        JButton minus, plus;

        Line(Entry entry, ItemType type, String name, String serialNo, BigDecimal purchasePrice, BigDecimal unit) {
            this.entry = entry;
            this.type = type;
            this.name = name;
            this.serialNo = serialNo;
            this.purchasePrice = purchasePrice;
            this.unit = unit;
        }

        int maxQty() { return entry != null ? Math.max(entry.stock(), 0) : 999; }
        BigDecimal total() { return unit.multiply(BigDecimal.valueOf(qty)); }
    }

    private final WorkOrder workOrder;
    private final Consumer<List<WorkOrderItem>> onSaved;

    private final List<Entry> parts = new ArrayList<>();
    private final List<Entry> labors = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();

    private SegmentedButtons<Mode> modeTabs;
    private JTextField txtSearch;
    private JPanel partFilters, laborFilters;
    private JComboBox<String> cmbCategory, cmbModel, cmbSupplier, cmbPartSort;
    private JCheckBox chkInStock, chkCompatible;
    private JComboBox<String> cmbLaborCategory, cmbLaborSort;
    private JComboBox<DeviceType> cmbLaborType;
    private JCheckBox chkPriced;
    private JButton resetPart, resetLabor;
    /** Combo'lar kod ile doldurulurken süzgeç yeniden uygulanmasın. */
    private boolean populating;
    private JPanel centerHolder;
    private CardLayout centerCards;
    private CardLayout resultCards;
    private JPanel resultHolder;
    private JLabel emptyTitle, emptyHint;
    private final DefaultListModel<Entry> listModel = new DefaultListModel<>();
    private JList<Entry> resultList;
    private int hoverIndex = -1;

    private JPanel basketList;
    private JScrollPane basketScroll;
    private JLabel lblBasketCount, lblTotal;
    private JButton btnSave;

    private boolean partsLoaded, laborsLoaded;
    /** Kayıt sürerken yeni kayıt, sepet ve katalog değişikliği yok (çift kalem/stok düşümü olmasın). */
    private boolean saving;
    private JLabel lblSaveText;
    private JLabel keyChip;
    private JLabel lblHint;

    /** Katalogdan (ya da elle girişten) kaydedilen kalemler tamamlanınca çağrılır; pencere kapanır. */
    public static void open(Component parent, WorkOrder workOrder, Consumer<List<WorkOrderItem>> onSaved) {
        WorkOrderItemModal panel = new WorkOrderItemModal(workOrder, onSaved);
        AppModal.showModal(parent, new SimpleModalBorder(panel, "Parça / İşçilik Ekle", null, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) panel.txtSearch.requestFocusInWindow();
        }), MODAL_ID);
    }

    private WorkOrderItemModal(WorkOrder workOrder, Consumer<List<WorkOrderItem>> onSaved) {
        this.workOrder = workOrder;
        this.onSaved = onSaved;
        setLayout(new MigLayout("insets 4 20 16 20, fill, gap 18", "[grow, fill][340!, fill]", "[grow, fill]"));
        setPreferredSize(new Dimension(900, 580));
        add(buildCatalog(), "wmin 0, hmin 0");
        add(buildBasket(), "hmin 0");
        bindKeys();
        refreshBasket();
        loadData();
    }

    // =========================================================================
    // KATALOG (SOL)
    // =========================================================================

    private JComponent buildCatalog() {
        JPanel left = new JPanel(new MigLayout("insets 0, fill, wrap, gapy 10, hidemode 3", "[0::, grow, fill]", "[][][][grow, fill][]"));
        left.setOpaque(false);

        modeTabs = new SegmentedButtons<Mode>()
                .add(Mode.PART, "Stoktaki parçalar", "icons/blocks.svg")
                .add(Mode.LABOR, "Hazır işçilik", "icons/pickaxe.svg")
                .add(Mode.MANUAL, "Elle gir", "icons/pen-line.svg");
        modeTabs.setOnChange(this::switchMode);
        left.add(modeTabs, "growx 0");

        txtSearch = new JTextField();
        txtSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg", 16, "Label.disabledForeground"));
        txtSearch.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,8,4,8");
        txtSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        txtSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Parça adı, barkod ya da uyumlu model ara…");
        txtSearch.getAccessibleContext().setAccessibleName("Katalogda ara");
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilters(); }
            public void removeUpdate(DocumentEvent e) { applyFilters(); }
            public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        left.add(txtSearch, "growx");

        String deviceModel = workOrder.getDevice() != null && workOrder.getDevice().getModel() != null
                && !workOrder.getDevice().getModel().isBlank() ? workOrder.getDevice().getModel().trim() : null;

        // Parça süzgeçleri: 1. satır kategori, uyumlu model, sıralama; 2. satır tedarikçi ve hızlı süzgeçler
        partFilters = new JPanel(new MigLayout("insets 0, fillx, wrap 3, gap 8 6, hidemode 3", "[0::, grow, fill][0::, grow, fill][0::, grow, fill]", "[][]"));
        partFilters.setOpaque(false);
        cmbCategory = filterCombo("Tüm kategoriler");
        cmbModel = filterCombo("Tüm modeller");
        cmbPartSort = sortCombo("Önerilen sıra", "Ad A–Z", "Fiyat: düşükten yükseğe", "Fiyat: yüksekten düşüğe", "Stok: çoktan aza");
        cmbSupplier = filterCombo("Tüm tedarikçiler");
        chkInStock = new JCheckBox("Yalnızca stokta", true);
        chkInStock.addActionListener(e -> applyFilters());
        chkCompatible = new JCheckBox(deviceModel != null ? deviceModel + " ile uyumlu" : "Bu cihazla uyumlu");
        chkCompatible.setVisible(deviceModel != null);
        chkCompatible.addActionListener(e -> applyFilters());
        resetPart = resetLink(this::resetPartFilters);
        partFilters.add(cmbCategory);
        partFilters.add(cmbModel);
        partFilters.add(cmbPartSort);
        partFilters.add(cmbSupplier);
        JPanel checks = new JPanel(new MigLayout("insets 0, gap 14, hidemode 3", "[][][]", "[center]"));
        checks.setOpaque(false);
        checks.add(chkInStock);
        checks.add(chkCompatible);
        checks.add(resetPart);
        partFilters.add(checks, "span 2, wmin 0");

        // İşçilik süzgeçleri: kategori, cihaz türü, sıralama; ücreti tanımlı olanlar
        laborFilters = new JPanel(new MigLayout("insets 0, fillx, wrap 3, gap 8 6, hidemode 3", "[0::, grow, fill][0::, grow, fill][0::, grow, fill]", "[][]"));
        laborFilters.setOpaque(false);
        cmbLaborCategory = filterCombo("Tüm kategoriler");
        cmbLaborType = new JComboBox<>();
        cmbLaborType.addItem(new DeviceType(null, "Tüm cihaz türleri", 0));
        cmbLaborType.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        cmbLaborType.setPrototypeDisplayValue(new DeviceType(null, "Tüm cihaz türleri", 0));
        cmbLaborType.setMinimumSize(new Dimension(0, cmbLaborType.getMinimumSize().height));
        cmbLaborType.addActionListener(e -> { if (!populating) loadLabors(); });
        cmbLaborSort = sortCombo("Ad A–Z", "Fiyat: düşükten yükseğe", "Fiyat: yüksekten düşüğe", "Kategoriye göre");
        chkPriced = new JCheckBox("Yalnızca fiyatı tanımlı olanlar");
        chkPriced.addActionListener(e -> applyFilters());
        resetLabor = resetLink(this::resetLaborFilters);
        laborFilters.add(cmbLaborCategory);
        laborFilters.add(cmbLaborType);
        laborFilters.add(cmbLaborSort);
        JPanel laborChecks = new JPanel(new MigLayout("insets 0, gap 14, hidemode 3", "[][]", "[center]"));
        laborChecks.setOpaque(false);
        laborChecks.add(chkPriced);
        laborChecks.add(resetLabor);
        laborFilters.add(laborChecks, "span 3, wmin 0");
        laborFilters.setVisible(false);

        JPanel filters = new JPanel(new MigLayout("insets 0, fillx, hidemode 3", "[0::, grow]", "[]"));
        filters.setOpaque(false);
        filters.add(partFilters, "hidemode 3, growx, wmin 0");
        filters.add(laborFilters, "hidemode 3, growx, wmin 0");
        left.add(filters, "growx");

        // Sonuç listesi + boş durum
        resultList = new JList<>(listModel);
        resultList.setFixedCellHeight(56);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new EntryRenderer());
        resultList.setFocusable(false);
        resultList.setOpaque(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { setHover(indexAt(e)); }
            @Override public void mouseExited(MouseEvent e) { setHover(-1); }
            private int pressed = -1;
            @Override public void mousePressed(MouseEvent e) { pressed = indexAt(e); }
            // Tıklama, basıp bırakırken fare birkaç piksel kayınca kaybolur; bu yüzden bırakmada işlenir.
            @Override public void mouseReleased(MouseEvent e) {
                int i = indexAt(e);
                if (i >= 0 && i == pressed && SwingUtilities.isLeftMouseButton(e)) {
                    resultList.setSelectedIndex(i);
                    addToBasket(listModel.get(i));
                }
                pressed = -1;
            }
        };
        resultList.addMouseListener(mouse);
        resultList.addMouseMotionListener(mouse);
        resultList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "width: 8");

        emptyTitle = new JLabel("Yükleniyor…");
        emptyTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        emptyHint = new JLabel(" ");
        emptyHint.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        JPanel empty = new JPanel(new MigLayout("insets 0, wrap, fill", "[center]", "push[]4[]push"));
        empty.setOpaque(false);
        empty.add(emptyTitle);
        empty.add(emptyHint);

        resultCards = new CardLayout();
        resultHolder = new JPanel(resultCards);
        resultHolder.setOpaque(false);
        resultHolder.add(scroll, "list");
        resultHolder.add(empty, "empty");

        JPanel resultCard = new JPanel(new MigLayout("insets 4 6 4 6, fill", "[grow, fill]", "[grow, fill]"));
        resultCard.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        resultCard.add(resultHolder);

        centerCards = new CardLayout();
        centerHolder = new JPanel(centerCards);
        centerHolder.setOpaque(false);
        centerHolder.add(resultCard, "catalog");
        centerHolder.add(buildManualForm(), "manual");
        left.add(centerHolder, "grow, hmin 0");
        lblHint = WorkOrderPanelSupport.createCaption("↑ ↓ gez  ·  Enter listeye ekle  ·  Ctrl+Enter kaydet");
        left.add(lblHint, "gaptop 2");
        return left;
    }

    private JComboBox<String> filterCombo(String all) {
        JComboBox<String> c = new JComboBox<>(new String[]{all});
        c.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        c.setPrototypeDisplayValue(all); // en uzun öğe (ör. uzun model listesi) sütunu genişletmesin
        c.setMinimumSize(new Dimension(0, c.getMinimumSize().height));
        c.addActionListener(e -> { if (!populating) applyFilters(); });
        return c;
    }

    private JComboBox<String> sortCombo(String... options) {
        JComboBox<String> c = new JComboBox<>(options);
        c.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        c.setToolTipText("Sıralama");
        c.setPrototypeDisplayValue("Fiyat: düşükten yükseğe");
        c.setMinimumSize(new Dimension(0, c.getMinimumSize().height));
        c.getAccessibleContext().setAccessibleName("Sıralama");
        c.addActionListener(e -> { if (!populating) applyFilters(); });
        return c;
    }

    private static JButton resetLink(Runnable action) {
        JButton b = new JButton("Süzgeçleri sıfırla");
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.linkColor; margin: 2,6,2,6");
        b.setVisible(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void resetPartFilters() {
        populating = true;
        cmbCategory.setSelectedIndex(0);
        cmbModel.setSelectedIndex(0);
        cmbSupplier.setSelectedIndex(0);
        cmbPartSort.setSelectedIndex(0);
        chkInStock.setSelected(true);
        chkCompatible.setSelected(false);
        populating = false;
        applyFilters();
    }

    private void resetLaborFilters() {
        populating = true;
        cmbLaborCategory.setSelectedIndex(0);
        cmbLaborSort.setSelectedIndex(0);
        chkPriced.setSelected(false);
        populating = false;
        cmbLaborType.setSelectedIndex(defaultTypeIndex());
    }

    /** İş emrinin cihaz türü listedeyse o, yoksa "Tüm cihaz türleri". */
    private int defaultTypeIndex() {
        Long typeId = workOrder.getDevice() != null && workOrder.getDevice().getDeviceType() != null
                ? workOrder.getDevice().getDeviceType().getId() : null;
        for (int i = 1; i < cmbLaborType.getItemCount(); i++) {
            if (typeId != null && typeId.equals(cmbLaborType.getItemAt(i).getId())) return i;
        }
        return 0;
    }

    private static boolean chosen(JComboBox<?> c) {
        return c.getSelectedIndex() > 0;
    }

    private static String sel(JComboBox<String> c) {
        return (String) c.getSelectedItem();
    }

    private int indexAt(MouseEvent e) {
        int i = resultList.locationToIndex(e.getPoint());
        if (i < 0) return -1;
        Rectangle r = resultList.getCellBounds(i, i);
        return r != null && r.contains(e.getPoint()) ? i : -1;
    }

    private void setHover(int i) {
        if (i == hoverIndex) return;
        hoverIndex = i;
        resultList.repaint();
    }

    private void switchMode(Mode mode) {
        boolean manual = mode == Mode.MANUAL;
        txtSearch.setVisible(!manual);
        partFilters.setVisible(mode == Mode.PART);
        laborFilters.setVisible(mode == Mode.LABOR);
        centerCards.show(centerHolder, manual ? "manual" : "catalog");
        lblHint.setText(manual ? "Enter eklenecekler listesine ekler  ·  Ctrl+Enter kaydeder" : "↑ ↓ gez  ·  Enter listeye ekle  ·  Ctrl+Enter kaydet");
        txtSearch.setText("");
        txtSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                mode == Mode.PART ? "Parça adı, barkod ya da uyumlu model ara…" : "İşlem ya da paket ara…");
        if (!manual) {
            applyFilters();
            txtSearch.requestFocusInWindow();
        } else if (nameField != null) {
            nameField.requestFocusInWindow();
        }
        revalidate();
        repaint();
    }

    private void applyFilters() {
        Mode mode = modeTabs.getSelected();
        if (mode == Mode.MANUAL) return;
        String needle = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(TR);
        listModel.clear();
        if (mode == Mode.PART) {
            String category = chosen(cmbCategory) ? sel(cmbCategory) : null;
            String modelFilter = chosen(cmbModel) ? sel(cmbModel).toLowerCase(TR) : null;
            String supplier = chosen(cmbSupplier) ? sel(cmbSupplier) : null;
            boolean inStockOnly = chkInStock.isSelected();
            boolean compatibleOnly = chkCompatible.isSelected();
            List<Entry> out = new ArrayList<>();
            for (Entry e : parts) {
                Part p = e.part();
                if (category != null && (p.getCategory() == null || !category.equals(p.getCategory().getName()))) continue;
                if (supplier != null && (p.getSupplier() == null || !supplier.equals(p.getSupplier().getName()))) continue;
                if (modelFilter != null && !nz(p.getModelCompatibility()).toLowerCase(TR).contains(modelFilter)) continue;
                if (compatibleOnly && !e.compatible()) continue;
                if (inStockOnly && (p.getStockQuantity() == null || p.getStockQuantity() <= 0)) continue;
                if (!needle.isEmpty() && !(nz(p.getName()) + " " + nz(p.getBarcode()) + " " + nz(p.getModelCompatibility()))
                        .toLowerCase(TR).contains(needle)) continue;
                out.add(e);
            }
            switch (cmbPartSort.getSelectedIndex()) {
                case 1 -> out.sort((a, b) -> nz(a.name()).compareToIgnoreCase(nz(b.name())));
                case 2 -> out.sort((a, b) -> a.price().compareTo(b.price()));
                case 3 -> out.sort((a, b) -> b.price().compareTo(a.price()));
                case 4 -> out.sort((a, b) -> Integer.compare(Math.max(b.stock(), 0), Math.max(a.stock(), 0)));
                default -> { }
            }
            out.forEach(listModel::addElement);
            boolean active = category != null || supplier != null || modelFilter != null || compatibleOnly
                    || !inStockOnly || cmbPartSort.getSelectedIndex() != 0;
            resetPart.setVisible(active);
        } else {
            String category = chosen(cmbLaborCategory) ? sel(cmbLaborCategory) : null;
            boolean pricedOnly = chkPriced.isSelected();
            List<Entry> out = new ArrayList<>();
            for (Entry e : labors) {
                Labor l = e.labor();
                if (category != null && !category.equals(l.getCategory())) continue;
                if (pricedOnly && e.price().signum() <= 0) continue;
                if (!needle.isEmpty() && !(nz(l.getName()) + " " + nz(l.getCategory())).toLowerCase(TR).contains(needle)) continue;
                out.add(e);
            }
            switch (cmbLaborSort.getSelectedIndex()) {
                case 1 -> out.sort((a, b) -> a.price().compareTo(b.price()));
                case 2 -> out.sort((a, b) -> b.price().compareTo(a.price()));
                case 3 -> out.sort((a, b) -> {
                    int c = nz(a.labor().getCategory()).compareToIgnoreCase(nz(b.labor().getCategory()));
                    return c != 0 ? c : nz(a.name()).compareToIgnoreCase(nz(b.name()));
                });
                default -> out.sort((a, b) -> nz(a.name()).compareToIgnoreCase(nz(b.name())));
            }
            out.forEach(listModel::addElement);
            resetLabor.setVisible(category != null || pricedOnly || cmbLaborSort.getSelectedIndex() != 0
                    || cmbLaborType.getSelectedIndex() != defaultTypeIndex());
        }
        boolean loaded = mode == Mode.PART ? partsLoaded : laborsLoaded;
        if (listModel.isEmpty()) {
            if (!loaded) {
                emptyTitle.setText("Yükleniyor…");
                emptyHint.setText(" ");
            } else if (!needle.isEmpty()) {
                emptyTitle.setText("“" + txtSearch.getText().trim() + "” için sonuç yok");
                emptyHint.setText("Listede yoksa “Elle gir” ile kalemi kendiniz ekleyebilirsiniz.");
            } else {
                emptyTitle.setText(mode == Mode.PART ? "Bu süzgeçle parça yok" : "Hazır işçilik tanımlı değil");
                emptyHint.setText(mode == Mode.PART ? "Süzgeçleri gevşetin (kategori, model, “yalnızca stokta”) ya da sıfırlayın."
                        : "Süzgeçleri gevşetin ya da “Elle gir” ile kalemi kendiniz ekleyin.");
            }
            resultCards.show(resultHolder, "empty");
        } else {
            resultCards.show(resultHolder, "list");
            resultList.setSelectedIndex(0);
            resultList.ensureIndexIsVisible(0);
        }
        hoverIndex = -1;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // =========================================================================
    // ELLE GİRİŞ
    // =========================================================================

    private JTextField nameField;
    private SegmentedButtons<ItemType> manualType;
    private CurrencyField manualSale, manualCost;
    private JPanel costRow;

    private JComponent buildManualForm() {
        JPanel form = new JPanel(new MigLayout("insets 16 18 16 18, fillx, wrap, gapy 6, hidemode 3", "[grow, fill]", ""));
        form.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        form.add(WorkOrderPanelSupport.createCaption("Ne ekleniyor?"));
        manualType = new SegmentedButtons<ItemType>()
                .add(ItemType.PART, "Parça", null)
                .add(ItemType.LABOR, "İşçilik", null);
        manualType.setOnChange(t -> {
            boolean part = t == ItemType.PART;
            costRow.setVisible(part);
            form.revalidate();
        });
        form.add(manualType, "growx 0, gapbottom 8");

        form.add(WorkOrderPanelSupport.createCaption("Ad"));
        nameField = new JTextField();
        nameField.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,8,4,8");
        nameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Örn. Ekran değişimi, batarya, lehim işçiliği");
        form.add(nameField, "gapbottom 8");

        form.add(WorkOrderPanelSupport.createCaption("Müşteriye satış fiyatı"));
        manualSale = tryField();
        form.add(manualSale, "w 220!, gapbottom 8");

        costRow = new JPanel(new MigLayout("insets 0, wrap, gapy 6", "[grow, fill]", ""));
        costRow.setOpaque(false);
        costRow.add(WorkOrderPanelSupport.createCaption("Alış maliyeti (isteğe bağlı, kâr hesabı için)"));
        manualCost = tryField();
        costRow.add(manualCost, "w 220!, gapbottom 8");
        form.add(costRow);

        JButton add = new JButton("Eklenecekler'e ekle", new Ikon("icons/plus.svg", 16, "Label.foreground"));
        add.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,14,7,14; iconTextGap: 6");
        add.addActionListener(e -> addManual());
        form.add(add, "gaptop 12, growx 0");
        nameField.addActionListener(e -> addManual());
        return form;
    }

    private static CurrencyField tryField() {
        CurrencyField f = new CurrencyField();
        f.setAvailableCurrencies(List.of("TRY"));
        f.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,8,4,4");
        return f;
    }

    private void addManual() {
        if (saving) return;
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.item.nameRequired"));
            nameField.requestFocusInWindow();
            return;
        }
        if (name.length() > 255) {
            Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.item.nameTooLong"));
            nameField.requestFocusInWindow();
            return;
        }
        boolean part = manualType.getSelected() == ItemType.PART;
        try { manualSale.commitEdit(); manualCost.commitEdit(); } catch (java.text.ParseException ignored) { }
        BigDecimal sale = money(manualSale);
        BigDecimal cost = part ? money(manualCost) : BigDecimal.ZERO;
        Line line = new Line(null, part ? ItemType.PART : ItemType.LABOR, name, "", cost, sale);
        lines.add(line);
        nameField.setText("");
        manualSale.setValue(0.0);
        manualCost.setValue(0.0);
        refreshBasket();
        scrollBasketToEnd();
        nameField.requestFocusInWindow();
    }

    private static BigDecimal money(CurrencyField f) {
        Object v = f.getValue();
        return v instanceof Number ? BigDecimal.valueOf(((Number) v).doubleValue()).max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    // =========================================================================
    // SEPET (SAĞ)
    // =========================================================================

    private JComponent buildBasket() {
        JPanel right = new JPanel(new MigLayout("insets 0, fill, wrap, gapy 10", "[grow, fill]", "[][grow, fill][][]"));
        right.setOpaque(false);

        lblBasketCount = new JLabel("Eklenecekler");
        lblBasketCount.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
        JPanel head = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[center]"));
        head.setOpaque(false);
        head.add(lblBasketCount);
        JButton clear = new JButton("Temizle");
        clear.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        clear.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.linkColor; margin: 2,6,2,6");
        clear.addActionListener(e -> { if (saving) return; lines.clear(); refreshBasket(); });
        head.add(clear);
        right.add(head);

        basketList = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0 0", "[grow, fill]", ""));
        basketList.setOpaque(false);
        basketScroll = new JScrollPane(basketList);
        basketScroll.setBorder(BorderFactory.createEmptyBorder());
        basketScroll.getViewport().setOpaque(false);
        basketScroll.setOpaque(false);
        basketScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        basketScroll.getVerticalScrollBar().setUnitIncrement(16);
        basketScroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "width: 8");
        JPanel card = new JPanel(new MigLayout("insets 4 12 4 12, fill", "[grow, fill]", "[grow, fill]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        card.add(basketScroll);
        right.add(card, "hmin 0");

        lblTotal = new JLabel("0,00 ₺");
        lblTotal.putClientProperty(FlatClientProperties.STYLE, "font: bold +4");
        JPanel totalRow = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[center]"));
        totalRow.setOpaque(false);
        totalRow.add(WorkOrderPanelSupport.createCaption("Toplam"));
        totalRow.add(lblTotal);
        right.add(totalRow, "gaptop 2");

        btnSave = new JButton() {
            @Override public Dimension getPreferredSize() { return getLayout().preferredLayoutSize(this); }
            @Override public Dimension getMinimumSize() { return getLayout().minimumLayoutSize(this); }
            @Override public void setEnabled(boolean b) {
                super.setEnabled(b);
                styleSave(b);
            }
        };
        btnSave.setLayout(new MigLayout("insets 0, gap 8, al center", "[][]", "[center]"));
        lblSaveText = new JLabel("Ekle", new Ikon("icons/plus.svg", 16, "Servicio.onAccentForeground"), SwingConstants.LEADING);
        lblSaveText.setIconTextGap(6);
        lblSaveText.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Servicio.onAccentForeground");
        keyChip = new JLabel("Ctrl+Enter");
        JLabel key = keyChip;
        key.putClientProperty(FlatClientProperties.STYLE, "font: -2; foreground: fade($Servicio.onAccentForeground,85%);"
                + " background: fade($Servicio.onAccentForeground,12%);"
                + " border: 1,5,1,5,fade($Servicio.onAccentForeground,40%),1,6");
        btnSave.add(lblSaveText);
        btnSave.add(key);
        btnSave.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 9,16,9,14; borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                + " background: $Component.accentColor; hoverBackground: darken($Component.accentColor,6%);"
                + " pressedBackground: darken($Component.accentColor,12%)");
        btnSave.getAccessibleContext().setAccessibleName("Kalemleri ekle");
        btnSave.addActionListener(e -> save());
        styleSave(true);
        right.add(btnSave, "growx");
        return right;
    }

    /**
     * Pasifken vurgu dolgusu yok: sönük nötr zemin, soluk yazı ve ikon, kısayol çipi gizli. (Yalnızca
     * düğmenin kendisi pasifleşiyor, içindeki etiketler beyaz kalıp zemin gri olunca kayboluyordu.)
     */
    private void styleSave(boolean enabled) {
        if (lblSaveText == null) return;
        lblSaveText.setIcon(new Ikon("icons/plus.svg", 16, enabled ? "Servicio.onAccentForeground" : "Label.disabledForeground"));
        lblSaveText.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: "
                + (enabled ? "$Servicio.onAccentForeground" : "$Label.disabledForeground"));
        keyChip.setVisible(enabled);
        btnSave.putClientProperty(FlatClientProperties.STYLE, enabled
                ? "arc: 10; margin: 9,16,9,14; borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                + " background: $Component.accentColor; hoverBackground: darken($Component.accentColor,6%);"
                + " pressedBackground: darken($Component.accentColor,12%)"
                : "arc: 10; margin: 9,16,9,14; borderWidth: 0; focusWidth: 0;"
                + " disabledBackground: fade($Label.foreground,7%); background: fade($Label.foreground,7%)");
        btnSave.repaint();
    }

    private void addToBasket(Entry entry) {
        if (saving) return;
        if (entry.isPart() && basketQty(entry) >= Math.max(entry.stock(), 0) && entry.stock() > 0) return; // satır zaten sönük
        if (entry.isPart() && entry.stock() <= 0) {
            Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.part.outOfStock"));
            return;
        }
        for (Line l : lines) {
            if (l.entry != null && l.entry.key().equals(entry.key())) {
                if (l.qty >= l.maxQty()) {
                    Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.item.stockLimit", String.valueOf(l.maxQty())));
                } else {
                    l.qty++;
                    updateLine(l);
                    updateTotals();
                }
                afterPick(l);
                return;
            }
        }
        Line l = new Line(entry, entry.isPart() ? ItemType.PART : ItemType.LABOR, entry.name(), "",
                entry.isPart() && entry.part().getPurchasePrice() != null ? entry.part().getPurchasePrice() : BigDecimal.ZERO,
                entry.price());
        lines.add(l);
        refreshBasket();
        afterPick(l);
    }

    /** Seçimden sonra arama kutusu hazır kalsın: yazıp Enter ile peş peşe kalem eklenir. */
    private void afterPick(Line l) {
        scrollBasketToEnd();
        txtSearch.requestFocusInWindow();
        txtSearch.selectAll();
    }

    private int basketQty(Entry e) {
        for (Line l : lines) if (l.entry != null && l.entry.key().equals(e.key())) return l.qty;
        return 0;
    }

    private void scrollBasketToEnd() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = basketScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void refreshBasket() {
        basketList.removeAll();
        if (lines.isEmpty()) {
            JPanel empty = new JPanel(new MigLayout("insets 40 8 40 8, wrap, fillx", "[center]", "[]4[]"));
            empty.setOpaque(false);
            JLabel t = new JLabel("Henüz kalem seçilmedi");
            t.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            JLabel h = new JLabel("<html><div style='text-align:center'>Soldan parça ya da işçilik seçin;<br>adet ve fiyatı burada ayarlarsınız.</div></html>");
            h.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            empty.add(t);
            empty.add(h);
            basketList.add(empty, "growx");
        }
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) basketList.add(new JSeparator(), "growx");
            basketList.add(buildLine(lines.get(i)), "growx");
        }
        basketList.revalidate();
        basketList.repaint();
        updateTotals();
        resultList.repaint();
    }

    private JComponent buildLine(Line l) {
        JPanel row = new JPanel(new MigLayout("insets 10 0 10 0, fillx, gap 8 3", "[][grow, fill][]", "[]0[]6[]6[]"));
        row.setOpaque(false);

        JLabel name = new JLabel(l.name);
        name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        name.setToolTipText(l.name);
        JButton remove = new JButton(new Ikon("icons/x.svg", 14, "Label.disabledForeground"));
        remove.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        remove.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 3,3,3,3");
        remove.setToolTipText("Sepetten çıkar");
        remove.getAccessibleContext().setAccessibleName(l.name + " sepetten çıkar");
        remove.addActionListener(e -> { if (saving) return; lines.remove(l); refreshBasket(); });
        row.add(name, "span 2, wmin 0");
        row.add(remove, "wrap");

        String kind = l.type == ItemType.PART ? "Parça" : "İşçilik";
        String sub = kind + (l.entry != null && l.entry.isPart() ? " · stok " + l.entry.stock() : "")
                + (l.entry == null ? " · elle girildi" : "");
        JLabel secondary = WorkOrderPanelSupport.createCaption(sub);
        row.add(secondary, "span 3, wmin 0, wrap");

        // Adet adımlayıcı
        l.minus = stepButton("icons/minus.svg", "Adedi azalt");
        l.plus = stepButton("icons/plus.svg", "Adedi artır");
        l.qtyLabel = new JLabel(String.valueOf(l.qty), SwingConstants.CENTER);
        l.qtyLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        l.minus.addActionListener(e -> { if (!saving && l.qty > 1) { l.qty--; updateLine(l); updateTotals(); } });
        l.plus.addActionListener(e -> {
            if (saving) return;
            if (l.qty >= l.maxQty()) {
                Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.item.stockLimit", String.valueOf(l.maxQty())));
                return;
            }
            l.qty++;
            updateLine(l);
            updateTotals();
        });
        JPanel stepper = new JPanel(new MigLayout("insets 0, gap 0", "[][28!][]", "[center]"));
        stepper.setOpaque(false);
        stepper.add(l.minus);
        stepper.add(l.qtyLabel, "growx");
        stepper.add(l.plus);

        // Birim fiyat (satırda düzenlenir)
        l.priceField = tryField();
        l.priceField.setValue(l.unit.doubleValue());
        l.priceField.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 2,6,2,2");
        l.priceField.getAccessibleContext().setAccessibleName(l.name + " birim fiyat");
        l.priceField.addPropertyChangeListener("value", e -> {
            l.unit = money(l.priceField);
            updateLine(l);
            updateTotals();
        });

        l.totalLabel = new JLabel();
        l.totalLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        JLabel times = WorkOrderPanelSupport.createCaption("×");
        row.add(stepper, "split 4, gapright 2");
        row.add(times);
        row.add(l.priceField, "w 100!");
        row.add(l.totalLabel, "growx, al right, span, wrap");
        if (l.type == ItemType.PART) {
            // Garanti takibi için seri no; her parça satırında, katalogtan da elle girişten de yazılır.
            l.serialField = new JTextField(l.serialNo);
            l.serialField.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 2,8,2,8");
            l.serialField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Seri no (isteğe bağlı)");
            l.serialField.getAccessibleContext().setAccessibleName(l.name + " seri no");
            row.add(l.serialField, "span 3, growx, wmin 0, wrap");
        }
        updateLine(l);
        return row;
    }

    private static JButton stepButton(String icon, String tip) {
        JButton b = new JButton(new Ikon(icon, 14));
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,6,4,6");
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        return b;
    }

    private void updateLine(Line l) {
        l.qtyLabel.setText(String.valueOf(l.qty));
        l.minus.setEnabled(l.qty > 1);
        l.plus.setEnabled(l.qty < l.maxQty());
        boolean free = l.total().signum() == 0;
        l.totalLabel.setText(free ? "Ücretsiz" : "= " + Format.formatPrice(l.total()));
        l.totalLabel.putClientProperty(FlatClientProperties.STYLE,
                free ? "font: bold; foreground: $Label.disabledForeground" : "font: bold");
        resultList.repaint();
    }

    private void updateTotals() {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (Line l : lines) {
            total = total.add(l.total());
            count += l.qty;
        }
        lblTotal.setText(Format.formatPrice(total));
        lblBasketCount.setText(lines.isEmpty() ? "Eklenecekler" : "Eklenecekler · " + lines.size());
        lblSaveText.setText(lines.isEmpty() ? "Ekle" : (lines.size() == 1 ? "1 kalem ekle" : lines.size() + " kalem ekle"));
        btnSave.setEnabled(!lines.isEmpty());
    }

    // =========================================================================
    // VERİ
    // =========================================================================

    private void loadData() {
        String model = workOrder.getDevice() != null && workOrder.getDevice().getModel() != null
                ? workOrder.getDevice().getModel().trim().toLowerCase(TR) : "";

        ServiceManager.getPartService().getAll().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            parts.clear();
            for (Part p : list) {
                boolean compatible = !model.isEmpty() && p.getModelCompatibility() != null
                        && p.getModelCompatibility().toLowerCase(TR).contains(model);
                parts.add(new Entry(p, null, compatible));
            }
            // Bu cihaza uyumlu parçalar üstte; sonra stokta olanlar, sonra ad.
            parts.sort((a, b) -> {
                if (a.compatible() != b.compatible()) return a.compatible() ? -1 : 1;
                boolean sa = a.stock() > 0, sb = b.stock() > 0;
                if (sa != sb) return sa ? -1 : 1;
                return nz(a.name()).compareToIgnoreCase(nz(b.name()));
            });
            populating = true;
            String prevModel = sel(cmbModel), prevSupplier = sel(cmbSupplier);
            cmbModel.removeAllItems();
            cmbModel.addItem("Tüm modeller");
            java.util.TreeSet<String> models = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            java.util.TreeSet<String> suppliers = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Part p : list) {
                if (p.getModelCompatibility() != null) {
                    for (String m : p.getModelCompatibility().split(",")) if (!m.isBlank()) models.add(m.trim());
                }
                if (p.getSupplier() != null && p.getSupplier().getName() != null) suppliers.add(p.getSupplier().getName());
            }
            models.forEach(cmbModel::addItem);
            cmbSupplier.removeAllItems();
            cmbSupplier.addItem("Tüm tedarikçiler");
            suppliers.forEach(cmbSupplier::addItem);
            if (prevModel != null) cmbModel.setSelectedItem(prevModel);
            if (prevSupplier != null) cmbSupplier.setSelectedItem(prevSupplier);
            populating = false;
            partsLoaded = true;
            applyFilters();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Parçalar yüklenemedi", ex));

        ServiceManager.getPartCategoryManager().getAll().thenAccept(categories -> SwingUtilities.invokeLater(() -> {
            populating = true;
            categories.forEach(c -> cmbCategory.addItem(c.getName()));
            populating = false;
        }));

        // Cihaz türleri gelince iş emrinin türü seçilir ve işçilikler o türe göre yüklenir.
        ServiceManager.getDeviceDictionaryManager().getAllTypes().thenAccept(types -> SwingUtilities.invokeLater(() -> {
            populating = true;
            types.forEach(cmbLaborType::addItem);
            cmbLaborType.setSelectedIndex(defaultTypeIndex());
            populating = false;
            loadLabors();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(this::loadLabors);
            return null;
        });
    }

    private void loadLabors() {
        DeviceType type = (DeviceType) cmbLaborType.getSelectedItem();
        Long typeId = type != null ? type.getId() : null;
        laborsLoaded = false;
        var future = typeId != null ? ServiceManager.getLaborService().getByTypeId(typeId) : ServiceManager.getLaborService().getAll();
        future.thenAccept(list -> SwingUtilities.invokeLater(() -> {
            labors.clear();
            for (Labor l : list) labors.add(new Entry(null, l, false));
            populating = true;
            String prev = sel(cmbLaborCategory);
            cmbLaborCategory.removeAllItems();
            cmbLaborCategory.addItem("Tüm kategoriler");
            java.util.TreeSet<String> cats = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Labor l : list) if (l.getCategory() != null && !l.getCategory().isBlank()) cats.add(l.getCategory());
            cats.forEach(cmbLaborCategory::addItem);
            if (prev != null) cmbLaborCategory.setSelectedItem(prev);
            populating = false;
            laborsLoaded = true;
            applyFilters();
        })).exceptionally(ex -> ErrorHandler.handle(this, "İşçilikler yüklenemedi", ex));
    }

    // =========================================================================
    // KAYIT VE KLAVYE
    // =========================================================================

    private void save() {
        if (saving || lines.isEmpty()) return;
        saving = true;
        for (Line l : lines) {
            try { l.priceField.commitEdit(); } catch (java.text.ParseException ignored) { }
            l.unit = money(l.priceField);
            if (l.serialField != null) l.serialNo = l.serialField.getText().trim();
        }
        btnSave.setEnabled(false);
        saveNext(new ArrayList<>(lines), 0, new ArrayList<>());
    }

    /** Kalemleri sırayla kaydeder (stok düşümü sırayla işlensin); bir hata olursa kaydedilenler korunur. */
    private void saveNext(List<Line> todo, int i, List<WorkOrderItem> saved) {
        if (i >= todo.size()) {
            SwingUtilities.invokeLater(() -> {
                onSaved.accept(saved);
                AppModal.closeModal(MODAL_ID);
            });
            return;
        }
        Line l = todo.get(i);
        WorkOrderItem item = new WorkOrderItem();
        item.setServiceId(workOrder.getId());
        item.setItemType(l.type);
        item.setSourceType(l.entry != null ? SourceType.PRESET : SourceType.MANUAL);
        if (l.entry != null) {
            if (l.entry.isPart()) item.setPartId(l.entry.part().getId());
            else item.setLaborId(l.entry.labor().getId());
        }
        item.setItemName(l.name);
        item.setUsedSerialNo(l.serialNo);
        item.setQuantity(l.qty);
        item.setPurchasePrice(l.type == ItemType.PART ? l.purchasePrice : BigDecimal.ZERO);
        item.setUnitPrice(l.unit);

        ServiceManager.getWorkOrderService().addItem(item)
                .thenAccept(s -> {
                    saved.add(s);
                    saveNext(todo, i + 1, saved);
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (!saved.isEmpty()) {
                            onSaved.accept(new ArrayList<>(saved));
                            lines.removeAll(todo.subList(0, saved.size()));
                            refreshBasket();
                        }
                        saving = false;
                        btnSave.setEnabled(!lines.isEmpty());
                        ErrorHandler.handle(this, "Kalem eklenemedi", ex);
                    });
                    return null;
                });
    }

    private void bindKeys() {
        InputMap in = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap act = getActionMap();
        in.put(KeyStroke.getKeyStroke("ctrl ENTER"), "save");
        act.put("save", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { save(); }
        });

        // Arama kutusundayken ↑ ↓ listede gezer, Enter seçileni sepete koyar.
        InputMap si = txtSearch.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap sa = txtSearch.getActionMap();
        si.put(KeyStroke.getKeyStroke("DOWN"), "next");
        si.put(KeyStroke.getKeyStroke("UP"), "prev");
        si.put(KeyStroke.getKeyStroke("ENTER"), "pick");
        sa.put("next", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { moveSelection(1); }
        });
        sa.put("prev", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { moveSelection(-1); }
        });
        sa.put("pick", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int i = resultList.getSelectedIndex();
                if (i >= 0 && i < listModel.size()) addToBasket(listModel.get(i));
            }
        });
    }

    private void moveSelection(int delta) {
        int n = listModel.size();
        if (n == 0) return;
        int i = Math.max(0, Math.min(n - 1, resultList.getSelectedIndex() + delta));
        resultList.setSelectedIndex(i);
        resultList.ensureIndexIsVisible(i);
    }

    // =========================================================================
    // KATALOG SATIRI
    // =========================================================================

    /** Ad kalın, altında soluk ayrıntı; sağda fiyat (kalın) ve stok durumu. Satırın tamamı hedeftir. */
    private final class EntryRenderer extends JPanel implements ListCellRenderer<Entry> {
        private final JLabel name = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel price = new JLabel();
        private final JLabel stock = new JLabel();
        private final JLabel plus = new JLabel(new Ikon("icons/plus.svg", 16, "Label.foreground"));
        private boolean active;
        private boolean selected;

        EntryRenderer() {
            super(new MigLayout("insets 0 12 0 12, fillx, gap 10 0, aligny center", "[grow, fill][right][24!]", "[]2[]"));
            setOpaque(false);
            name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            detail.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            price.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            stock.putClientProperty(FlatClientProperties.STYLE, "font: -1");
            add(name, "wmin 0");
            add(price);
            add(plus, "spany 2, al center, wrap");
            add(detail, "wmin 0");
            add(stock);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Entry> list, Entry e, int index, boolean selected, boolean focus) {
            this.selected = selected;
            active = index == hoverIndex || selected;
            int inBasket = basketQty(e);
            boolean full = e.isPart() && e.stock() > 0 && inBasket >= e.stock();
            name.setText(e.name());
            price.setText(Format.formatPrice(e.price()));
            if (e.isPart()) {
                Part p = e.part();
                String cat = p.getCategory() != null ? p.getCategory().getName() : "";
                String d = (nz(p.getBarcode()).isEmpty() ? "" : p.getBarcode()) + (cat.isEmpty() ? "" : (nz(p.getBarcode()).isEmpty() ? "" : " · ") + cat)
                        + (e.compatible() ? " · bu cihaza uyumlu" : "");
                detail.setText(d.isEmpty() ? " " : d);
                int s = e.stock();
                boolean out = s <= 0;
                stock.setText(out ? "Tükendi" : (inBasket > 0 ? "Listede " + inBasket + " · " : "") + "Stok " + s);
                stock.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: "
                        + (out ? "$Servicio.dangerColor" : s <= (p.getMinStockLevel() != null ? p.getMinStockLevel() : 0) ? "$Servicio.warningColor"
                        : "$Label.disabledForeground"));
                name.putClientProperty(FlatClientProperties.STYLE, out || full ? "font: bold; foreground: $Label.disabledForeground" : "font: bold");
                plus.setVisible(!out && !full && active);
            } else {
                Labor l = e.labor();
                detail.setText(nz(l.getCategory()).isEmpty() ? (nz(l.getDeviceTypeName()).isEmpty() ? " " : l.getDeviceTypeName())
                        : l.getCategory() + (nz(l.getDeviceTypeName()).isEmpty() ? "" : " · " + l.getDeviceTypeName()));
                stock.setText(inBasket > 0 ? "Listede " + inBasket : " ");
                stock.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
                name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
                plus.setVisible(active);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (active) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(UIManager.getColor(selected ? "Servicio.rowSelectedBackground" : "Servicio.rowHoverBackground"));
                g2.fillRoundRect(0, 1, getWidth(), getHeight() - 2, 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
