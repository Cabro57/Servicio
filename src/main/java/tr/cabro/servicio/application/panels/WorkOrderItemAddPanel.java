package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.ModalBorderAction;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.editors.AddButtonEditor;
import tr.cabro.servicio.application.renderer.AddButtonRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.SourceType;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class WorkOrderItemAddPanel extends JPanel {

    public static final int SELECTED_ITEM = 100;
    public static final int CREAT_ITEM = 101;

    private final WorkOrder workOrder;

    private static final DeviceType ALL_TYPES = new DeviceType(null, "Tümü", 0);
    private static final String ALL_CATEGORIES = "Tümü";
    private static final String STOCK_ALL = "Tümü";
    private static final String STOCK_IN = "Stokta Olanlar";
    private static final String STOCK_OUT = "Tükenenler";

    private GenericTableModel<Part> partTableModel;
    private GenericTableModel<Labor> laborTableModel;

    // Tabloları filtrelemek için Sorter tanımlamaları ekledik
    private TableRowSorter<GenericTableModel<Part>> partSorter;
    private TableRowSorter<GenericTableModel<Labor>> laborSorter;

    private JTextField txtPartSearch;
    private JComboBox<String> categoryCombo;
    private JComboBox<String> stockCombo;

    private JTextField txtLaborSearch;
    private JComboBox<DeviceType> laborTypeCombo;

    @Getter
    private WorkOrderItem item;

    public WorkOrderItemAddPanel(WorkOrder workOrder) {
        this.workOrder = workOrder;
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 0", "[grow]", "[grow]"));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.putClientProperty(FlatClientProperties.STYLE, "tabType: card; tabInsets: 5,20,5,20; focusColor: null;");

        tabbedPane.addTab("Stoktakiler", new Ikon("icons/blocks.svg", 0.8f), createStockPanel());
        tabbedPane.addTab("Hazır Paketler", new Ikon("icons/pickaxe.svg", 0.8f), createLaborPanel());
        tabbedPane.addTab("Manuel Ekle", new Ikon("icons/pen-line.svg", 0.8f), createManualPanel());

        add(tabbedPane, "grow");
        loadData();
    }

    private Long getWorkOrderDeviceTypeId() {
        return (workOrder.getDevice() != null && workOrder.getDevice().getDeviceType() != null)
                ? workOrder.getDevice().getDeviceType().getId() : null;
    }

    private void loadData() {
        ServiceManager.getPartService().getAll().thenAccept(parts -> {
            SwingUtilities.invokeLater(() -> {
                partTableModel.setData(parts);
                populateCategoryCombo(parts);
                applyPartFilters();
            });
        });

        ServiceManager.getDeviceDictionaryManager().getAllTypes().thenAccept(types -> {
            SwingUtilities.invokeLater(() -> {
                Long currentTypeId = getWorkOrderDeviceTypeId();
                types.forEach(laborTypeCombo::addItem);

                if (currentTypeId != null) {
                    for (int i = 0; i < laborTypeCombo.getItemCount(); i++) {
                        DeviceType candidate = laborTypeCombo.getItemAt(i);
                        if (currentTypeId.equals(candidate.getId())) {
                            laborTypeCombo.setSelectedItem(candidate);
                            break;
                        }
                    }
                }
            });
        });

        loadLabors(getWorkOrderDeviceTypeId());
    }

    private void loadLabors(Long deviceTypeId) {
        var laborFuture = deviceTypeId != null
                ? ServiceManager.getLaborService().getByTypeId(deviceTypeId)
                : ServiceManager.getLaborService().getAll();

        laborFuture.thenAccept(labors -> {
            SwingUtilities.invokeLater(() -> {
                laborTableModel.setData(labors);
                applyFilter(txtLaborSearch != null ? txtLaborSearch.getText() : null, laborSorter);
            });
        });
    }

    private void populateCategoryCombo(List<Part> parts) {
        String previous = (String) categoryCombo.getSelectedItem();

        categoryCombo.removeAllItems();
        categoryCombo.addItem(ALL_CATEGORIES);

        new TreeSet<>(parts.stream()
                .map(Part::getCategory)
                .filter(c -> c != null && !c.trim().isEmpty())
                .collect(java.util.stream.Collectors.toSet()))
                .forEach(categoryCombo::addItem);

        if (previous != null) {
            categoryCombo.setSelectedItem(previous);
        }
    }

    private void applyPartFilters() {
        if (partSorter == null) return;

        String text = txtPartSearch.getText();
        String category = (String) categoryCombo.getSelectedItem();
        String stock = (String) stockCombo.getSelectedItem();

        partSorter.setRowFilter(new RowFilter<GenericTableModel<Part>, Integer>() {
            @Override
            public boolean include(Entry<? extends GenericTableModel<Part>, ? extends Integer> entry) {
                Part p = entry.getModel().getItemAt(entry.getIdentifier());

                if (category != null && !ALL_CATEGORIES.equals(category) && !category.equals(p.getCategory())) {
                    return false;
                }

                if (STOCK_IN.equals(stock) && p.getStockQuantity() <= 0) return false;
                if (STOCK_OUT.equals(stock) && p.getStockQuantity() > 0) return false;

                if (text != null && !text.trim().isEmpty()) {
                    String needle = text.trim().toLowerCase(Locale.forLanguageTag("tr"));
                    String haystack = ((p.getName() != null ? p.getName() : "") + " "
                            + (p.getBarcode() != null ? p.getBarcode() : "")).toLowerCase(Locale.forLanguageTag("tr"));
                    return haystack.contains(needle);
                }

                return true;
            }
        });
    }

    // --- 1. STOKTAKİ PARÇALAR PANELİ ---
    private JPanel createStockPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 15", "[grow]", "[][][][grow]"));

        txtPartSearch = new JTextField();
        txtPartSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Parça adı veya barkod ara...");
        txtPartSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg", 0.8f));
        panel.add(txtPartSearch, "growx, wrap");

        categoryCombo = new JComboBox<>();
        categoryCombo.addItem(ALL_CATEGORIES);
        panel.add(categoryCombo, "growx, split 2");

        stockCombo = new JComboBox<>(new String[]{STOCK_ALL, STOCK_IN, STOCK_OUT});
        panel.add(stockCombo, "growx, wrap");

        List<ColumnDef<Part>> cols = Arrays.asList(
                new ColumnDef<>("Parça Bilgisi", Part.class, p -> p),
                new ColumnDef<>("Fiyat", String.class, p -> Format.formatPrice(p.getSalePrice())),
                new ColumnDef<>("İşlem", String.class, p -> "Ekle")
        );
        partTableModel = new GenericTableModel<>(cols);
        JTable table = new JTable(partTableModel);
        table.setRowHeight(50);

        // Sorter'ı oluşturup tabloya atıyoruz
        partSorter = new TableRowSorter<>(partTableModel);
        table.setRowSorter(partSorter);

        // Arama kutusu ve filtre kutuları değiştikçe birleşik filtreyi uyguluyoruz
        txtPartSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyPartFilters(); }
            public void removeUpdate(DocumentEvent e) { applyPartFilters(); }
            public void changedUpdate(DocumentEvent e) { applyPartFilters(); }
        });
        categoryCombo.addActionListener(e -> applyPartFilters());
        stockCombo.addActionListener(e -> applyPartFilters());

        table.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Part>(
                Part::getName,
                p -> p.getBarcode() + " • Stok: " + p.getStockQuantity()
        ));

        DefaultTableCellRenderer priceRenderer = new DefaultTableCellRenderer();
        priceRenderer.setHorizontalAlignment(SwingConstants.TRAILING);
        priceRenderer.setFont(priceRenderer.getFont().deriveFont(Font.BOLD));
        table.getColumnModel().getColumn(1).setCellRenderer(priceRenderer);

        table.getColumnModel().getColumn(2).setCellRenderer(new AddButtonRenderer());
        table.getColumnModel().getColumn(2).setCellEditor(new AddButtonEditor(row -> {
            if (table.isEditing()) table.getCellEditor().cancelCellEditing();
            int modelRow = table.convertRowIndexToModel(row);
            Part selectedPart = partTableModel.getItemAt(modelRow);

            if (selectedPart.getStockQuantity() <= 0) {
                Toast.show(WorkOrderItemAddPanel.this, Toast.Type.WARNING, "Bu ürünün stoğu kalmamıştır!");
                return;
            }

            item = new WorkOrderItem();
            item.setServiceId(workOrder.getId());
            item.setItemType(ItemType.PART);
            item.setSourceType(SourceType.PRESET);
            item.setPartId(selectedPart.getId());
            item.setItemName(selectedPart.getName());
            item.setPurchasePrice(selectedPart.getPurchasePrice());
            item.setUnitPrice(selectedPart.getSalePrice());

            ModalBorderAction borderAction = ModalBorderAction.getModalBorderAction(this);
            if (borderAction != null) {
                borderAction.doAction(SELECTED_ITEM);
            }

        }));

        table.getColumnModel().getColumn(1).setMaxWidth(120);
        table.getColumnModel().getColumn(2).setMaxWidth(80);

        JScrollPane scroll = new JScrollPane(table);
        panel.add(scroll, "grow");
        return panel;
    }

    // --- 2. HAZIR İŞÇİLİK (LABOR) PANELİ ---
    private JPanel createLaborPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 15", "[grow]", "[][][grow]"));

        txtLaborSearch = new JTextField();
        txtLaborSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "İşlem veya paket ara...");
        txtLaborSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg", 0.8f));
        panel.add(txtLaborSearch, "wrap, growx");

        laborTypeCombo = new JComboBox<>();
        laborTypeCombo.addItem(ALL_TYPES);
        panel.add(laborTypeCombo, "wrap, growx");

        List<ColumnDef<Labor>> cols = Arrays.asList(
                new ColumnDef<>("İşlem Bilgisi", Labor.class, l -> l),
                new ColumnDef<>("Fiyat", String.class, l -> Format.formatPrice(l.getDefaultPrice())),
                new ColumnDef<>("Ekle", String.class, l -> "Ekle")
        );
        laborTableModel = new GenericTableModel<>(cols);
        JTable table = new JTable(laborTableModel);
        table.setRowHeight(50);

        // Sorter'ı oluşturup tabloya atıyoruz
        laborSorter = new TableRowSorter<>(laborTableModel);
        table.setRowSorter(laborSorter);

        // İşçilik arama kutusuna DocumentListener ekliyoruz
        txtLaborSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(txtLaborSearch.getText(), laborSorter); }
            public void removeUpdate(DocumentEvent e) { applyFilter(txtLaborSearch.getText(), laborSorter); }
            public void changedUpdate(DocumentEvent e) { applyFilter(txtLaborSearch.getText(), laborSorter); }
        });

        // Tür değiştikçe veriyi yeniden yükle (backend'de filtreleniyor)
        laborTypeCombo.addActionListener(e -> {
            DeviceType selected = (DeviceType) laborTypeCombo.getSelectedItem();
            Long typeId = (selected != null) ? selected.getId() : null;
            loadLabors(typeId);
        });

        table.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Labor>(
                Labor::getName, Labor::getCategory
        ));

        DefaultTableCellRenderer priceRenderer = new DefaultTableCellRenderer();
        priceRenderer.setHorizontalAlignment(SwingConstants.TRAILING);
        priceRenderer.setFont(priceRenderer.getFont().deriveFont(Font.BOLD));
        table.getColumnModel().getColumn(1).setCellRenderer(priceRenderer);

        table.getColumnModel().getColumn(2).setCellRenderer(new AddButtonRenderer());
        table.getColumnModel().getColumn(2).setCellEditor(new AddButtonEditor(row -> {
            Labor labor = laborTableModel.getItemAt(table.convertRowIndexToModel(row));
            WorkOrderItem item = new WorkOrderItem();
            item.setServiceId(workOrder.getId());
            item.setItemType(ItemType.LABOR);
            item.setSourceType(SourceType.PRESET);
            item.setLaborId(labor.getId());
            item.setItemName(labor.getName());
            item.setPurchasePrice(BigDecimal.ZERO);
            item.setUnitPrice(labor.getDefaultPrice());

            ServiceManager.getWorkOrderService().addItem(item).thenAccept(i -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(WorkOrderItemAddPanel.this, Toast.Type.SUCCESS, "İşçilik başarıyla eklendi.");
                });
            });
        }));

        table.getColumnModel().getColumn(1).setMaxWidth(120);
        table.getColumnModel().getColumn(2).setMaxWidth(80);

        panel.add(new JScrollPane(table), "grow");
        return panel;
    }

    // --- 3. MANUEL EKLEME PANELİ ---
    private JPanel createManualPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 20, gapy 15", "[100!][grow]", "[]"));

        JComboBox<String> cmbType = new JComboBox<>(new String[]{"Parça", "İşçilik"});
        JTextField txtName = new JTextField();
        JFormattedTextField txtPurchasePrice = new CurrencyField();
        JFormattedTextField txtSalePrice = new CurrencyField();
        JTextField txtSerialNo = new JTextField();

        txtSerialNo.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Opsiyonel seri numarası...");

        cmbType.addActionListener(e -> {
            boolean isPart = cmbType.getSelectedIndex() == 0;
            txtSerialNo.setEnabled(isPart);
            if(!isPart) {
                txtPurchasePrice.setValue(BigDecimal.ZERO);
                txtSerialNo.setText("");
            }
        });

        panel.add(new JLabel("Tür:")); panel.add(cmbType, "w 150!, wrap");
        panel.add(new JLabel("Adı:")); panel.add(txtName, "growx, wrap");
        panel.add(new JLabel("Alış Fiyatı:")); panel.add(txtPurchasePrice, "growx, wrap");
        panel.add(new JLabel("Satış Fiyatı:")); panel.add(txtSalePrice, "growx, wrap");
        panel.add(new JLabel("Seri No:")); panel.add(txtSerialNo, "growx, wrap");

        JButton btnSave = new JButton("Kaydet ve Ekle");
        btnSave.putClientProperty(FlatClientProperties.STYLE, "background: $Component.accentColor; foreground: #fff; arc: 10; font: bold");
        btnSave.addActionListener(e -> {
            // Ad zorunlu ve max 255 karakter
            String itemName = txtName.getText().trim();
            if (itemName.isEmpty()) {
                Toast.show(this, Toast.Type.WARNING, "Lütfen kalem için bir ad girin.");
                txtName.requestFocus();
                return;
            }
            if (itemName.length() > 255) {
                Toast.show(this, Toast.Type.WARNING, "Kalem adı en fazla 255 karakter olabilir.");
                txtName.requestFocus();
                return;
            }

            // Alış fiyatı negatif olamaz
            double purchaseVal = txtPurchasePrice.getValue() instanceof Number
                    ? ((Number) txtPurchasePrice.getValue()).doubleValue() : 0.0;
            if (purchaseVal < 0) {
                Toast.show(this, Toast.Type.WARNING, "Alış fiyatı negatif olamaz.");
                txtPurchasePrice.requestFocus();
                return;
            }

            // Satış fiyatı negatif olamaz
            double saleVal = txtSalePrice.getValue() instanceof Number
                    ? ((Number) txtSalePrice.getValue()).doubleValue() : 0.0;
            if (saleVal < 0) {
                Toast.show(this, Toast.Type.WARNING, "Satış fiyatı negatif olamaz.");
                txtSalePrice.requestFocus();
                return;
            }

            item = new WorkOrderItem();
            item.setServiceId(workOrder.getId());
            item.setItemType(cmbType.getSelectedIndex() == 0 ? ItemType.PART : ItemType.LABOR);
            item.setSourceType(SourceType.MANUAL);
            item.setItemName(itemName);
            item.setPurchasePrice(BigDecimal.valueOf(purchaseVal));
            item.setUnitPrice(BigDecimal.valueOf(saleVal));
            item.setUsedSerialNo(txtSerialNo.getText().trim());

            ModalBorderAction borderAction = ModalBorderAction.getModalBorderAction(this);
            if (borderAction != null) {
                borderAction.doAction(CREAT_ITEM);
            }
        });

        panel.add(btnSave, "span 2, align right, gaptop 15");
        return panel;
    }

    /**
     * Hem parça hem de işçilik tablosu için kullanılabilen jenerik filtreleme metodu.
     * @param text Arama kutusuna girilen metin
     * @param sorter Filtrelenecek tablonun Sorter nesnesi
     */
    private void applyFilter(String text, TableRowSorter<?> sorter) {
        if (sorter == null) return;

        if (text == null || text.trim().isEmpty()) {
            sorter.setRowFilter(null); // Metin boşsa filtreyi kaldır
        } else {
            try {
                // (?iu) parametresi: Büyük/küçük harf duyarlılığını (case-insensitive)
                // ve Türkçe karakter desteğini (unicode) aktif eder.
                sorter.setRowFilter(RowFilter.regexFilter("(?iu)" + Pattern.quote(text.trim())));
            } catch (PatternSyntaxException e) {
                // Regex hatası olursa filtreyi sıfırla
                sorter.setRowFilter(null);
            }
        }
    }
}