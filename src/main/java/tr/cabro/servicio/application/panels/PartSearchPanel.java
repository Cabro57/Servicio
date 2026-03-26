package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.renderer.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.renderer.ProfileTableRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.tablemodal.SearchPartTableModel;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.util.List;

public class PartSearchPanel extends JPanel {

    private SearchPartTableModel partTableModel;
    private final DefaultComboBoxModel<String> deviceTypeComboBoxModel;
    private TableRowSorter<SearchPartTableModel> sorter = new TableRowSorter<>();

    private boolean inStockMode = false;
    private boolean outStockMode = false;

    public PartSearchPanel() {
        this.deviceTypeComboBoxModel = new DefaultComboBoxModel<>();

        init();
    }

    private void init() {
        initComponent();

        productTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        searchField.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:15;"
                + "borderWidth:0;"
                + "focusWidth:0;"
                + "innerFocusWidth:0;"
                + "margin:5,20,5,20;"
                + "background:$Table.background");
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ara...");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icons/search.svg", 0.4f));
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        deviceTypeCombo.addActionListener(e -> applyCombinedFilter());
        deviceTypeCombo.setModel(deviceTypeComboBoxModel);
        loadDeviceTypes();

        if (productTable != null) {
            refreshProductTable();
        }

        allPartsButton.addActionListener(e -> showAllParts());
        inStockButton.addActionListener(e -> filterInStock());
        outStockButton.addActionListener(e -> filterOutStock());
    }

    private void refreshProductTable() {
        partTableModel = new SearchPartTableModel(ServiceManager.getPartService().getAll());
        productTable.setModel(partTableModel);

        sorter = new TableRowSorter<>(partTableModel);
        productTable.setRowSorter(sorter);

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING
        };

        productTable.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(productTable, columnAlignments));
        productTable.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(productTable, 0));
        productTable.getColumnModel().getColumn(2).setCellRenderer(new ProfileTableRenderer(productTable));
        productTable.getColumnModel().getColumn(3).setCellRenderer(new TooltipCellRenderer());
        productTable.getColumnModel().getColumn(4).setCellRenderer(new TooltipCellRenderer());
        productTable.getColumnModel().getColumn(5).setCellRenderer(new TooltipCellRenderer());


        productTable.getColumnModel().getColumn(0).setMaxWidth(50);
        productTable.getColumnModel().getColumn(1).setPreferredWidth(220);
        productTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        productTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        productTable.getColumnModel().getColumn(4).setMaxWidth(50);
        productTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        productTable.getColumnModel().getColumn(6).setPreferredWidth(80);

        applySearchFieldListener();
        applyDoubleClickListener();
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

        sorter.setRowFilter(new RowFilter<SearchPartTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends SearchPartTableModel, ? extends Integer> entry) {
                SearchPartTableModel model = (SearchPartTableModel) productTable.getModel();
                int modelRow = entry.getIdentifier();
                Part p = model.getPart(modelRow);

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

                    SearchPartTableModel model = (SearchPartTableModel) productTable.getModel();

                }
            }
        });
    }

    public List<Part> getSelected() {
        return partTableModel.getSelectedPart();
    }

    private void loadDeviceTypes() {
        deviceTypeComboBoxModel.removeAllElements();
        deviceTypeComboBoxModel.addElement("(Tümü)"); // <--- Tümünü göster seçeneği
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
    }

    private JButton allPartsButton;
    private JButton inStockButton;
    private JButton outStockButton;
    private JComboBox<String> deviceTypeCombo;
    private JTextField searchField;
    private JTable productTable;
}
