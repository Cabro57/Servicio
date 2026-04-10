package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.renderer.ProfileTableRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.util.Arrays;
import java.util.List;

public class PartSearchPanel extends JPanel {

    private GenericTableModel<Part> partTableModel;
    private final DefaultComboBoxModel<String> deviceTypeComboBoxModel;
    private TableRowSorter<GenericTableModel<Part>> sorter = new TableRowSorter<>();
    private PartService partService;

    private boolean inStockMode = false;
    private boolean outStockMode = false;

    public PartSearchPanel() {
        this.deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
        this.partService = ServiceManager.getPartService();
        init();
    }

    private void init() {
        initComponent();

        deviceTypeCombo.addActionListener(e -> applyCombinedFilter());
        deviceTypeCombo.setModel(deviceTypeComboBoxModel);
        loadDeviceTypes();

        if (productTable != null) {
            refreshTable();
        }

        allPartsButton.addActionListener(e -> showAllParts());
        inStockButton.addActionListener(e -> filterInStock());
        outStockButton.addActionListener(e -> filterOutStock());

        setupTable();
    }

    private void setupTable() {
        List<ColumnDef<Part>> columns = Arrays.asList(
                new ColumnDef<>("Barkod", String.class, Part::getBarcode),
                new ColumnDef<>("Marka", String.class, Part::getBrand),
                new ColumnDef<>("Ürün Adı", String.class, Part::getName),
                new ColumnDef<>("Cihaz Türü", String.class, Part::getDeviceType),
                new ColumnDef<>("Uyumlu Model", String.class, Part::getModel),
                new ColumnDef<>("Stok", Integer.class, Part::getStock),
                new ColumnDef<>("Alış Fiyatı", String.class, p -> Format.formatPrice(p.getPurchasePrice())),
                new ColumnDef<>("Satış Fiyatı", String.class, p -> Format.formatPrice(p.getSalePrice()))
        );

        partTableModel = new GenericTableModel<>(columns);
        productTable.setModel(partTableModel);

        configureTable();
    }

    private void configureTable() {
        sorter = new TableRowSorter<>(partTableModel);
        productTable.setRowSorter(sorter);

        Integer[] columnAlignments = {
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING
        };

        productTable.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(productTable, columnAlignments));
        productTable.getColumnModel().getColumn(2).setCellRenderer(new ProfileTableRenderer(productTable));
        productTable.getColumnModel().getColumn(3).setCellRenderer(new TooltipCellRenderer());
        productTable.getColumnModel().getColumn(4).setCellRenderer(new TooltipCellRenderer());

        productTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        productTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        productTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        productTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        productTable.getColumnModel().getColumn(6).setPreferredWidth(80);
        productTable.getColumnModel().getColumn(7).setPreferredWidth(80);

        applySearchFieldListener();
        applyDoubleClickListener();
    }

    private void refreshTable() {
        partService.getAll().thenAccept(parts -> {
            SwingUtilities.invokeLater(() -> partTableModel.setData(parts));
        });
    }

    private void showAllParts() {
        inStockMode = false;
        outStockMode = false;
        applyCombinedFilter();
    }

    private void filterInStock() {
        inStockMode = true;
        outStockMode = false;
        applyCombinedFilter();
    }

    private void filterOutStock() {
        inStockMode = false;
        outStockMode = true;
        applyCombinedFilter();
    }

    private void applyCombinedFilter() {
        String selectedType = (String) deviceTypeCombo.getSelectedItem();
        boolean onlyInStock = inStockMode;
        boolean onlyOutStock = outStockMode;

        sorter.setRowFilter(new RowFilter<GenericTableModel<Part>, Integer>() {
            @Override
            public boolean include(Entry<? extends GenericTableModel<Part>, ? extends Integer> entry) {
                int modelRow = entry.getIdentifier();
                Part p = partTableModel.getItemAt(modelRow);
                if (p == null) return true;

                // Arama filtresi
                String searchText = searchField.getText().trim();
                if (!searchText.isEmpty()) {
                    if (!p.getName().toLowerCase().contains(searchText.toLowerCase()) &&
                            !p.getBarcode().toLowerCase().contains(searchText.toLowerCase())) {
                        return false;
                    }
                }

                // Cihaz türü filtresi
                if (selectedType != null && !selectedType.equals("(Tümü)") && !selectedType.isEmpty()) {
                    if (!selectedType.equalsIgnoreCase(p.getDeviceType())) {
                        return false;
                    }
                }

                // Stok filtresi
                if (onlyInStock && p.getStock() <= 0) return false;
                if (onlyOutStock && p.getStock() > 0) return false;

                return true;
            }
        });
    }

    private void applySearchFieldListener() {
        if (searchField.getDocument().getProperty("listenerAttached") != Boolean.TRUE) {
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void applyFilter() {
                    applyCombinedFilter();
                }

                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            });
            searchField.getDocument().putProperty("listenerAttached", Boolean.TRUE);
        }
    }

    private void applyDoubleClickListener() {
        productTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && productTable.getSelectedRow() != -1) {
                    int viewRow = productTable.getSelectedRow();
                    int modelRow = productTable.convertRowIndexToModel(viewRow);
                    Part part = partTableModel.getItemAt(modelRow);
                    // Burada gerekirse callback kullanılabilir
                }
            }
        });
    }

    /** Tabloda seçili satırlardaki parçaları döndürür. */
    public List<Part> getSelected() {
        return partTableModel.getSelectedItems(productTable.getSelectedRows());
    }

    private void loadDeviceTypes() {
        deviceTypeComboBoxModel.removeAllElements();
        deviceTypeComboBoxModel.addElement("(Tümü)");
        DeviceSettings settings = Servicio.getDeviceSettings();
        List<String> types = settings.getTypes();
        for (String type : types) {
            deviceTypeComboBoxModel.addElement(type);
        }
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 5, gap 10, wrap 1, width 650", "[grow]", "[]10[]10[grow]10[]"));

        JPanel topPanel = new JPanel(new MigLayout("insets 0, gap 10", "[][grow][][grow]", "[]"));
        allPartsButton = new JButton("Tüm Parçalar");
        inStockButton = new JButton("Stoktakiler");
        outStockButton = new JButton("Stokta Olmayanlar");
        deviceTypeCombo = new JComboBox<>();

        topPanel.add(allPartsButton, "growx");
        topPanel.add(inStockButton, "growx");
        topPanel.add(outStockButton, "growx");
        topPanel.add(deviceTypeCombo, "growx");
        add(topPanel, "growx");

        // Arama alanı
        searchField = new JTextField();
        add(searchField, "growx");

        // Tablo alanı
        productTable = new JTable();
        add(new JScrollPane(productTable), "grow, push");

        productTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ara...");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg"));
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    }

    private JButton allPartsButton;
    private JButton inStockButton;
    private JButton outStockButton;
    private JComboBox<String> deviceTypeCombo;
    private JTextField searchField;
    private JTable productTable;
}
