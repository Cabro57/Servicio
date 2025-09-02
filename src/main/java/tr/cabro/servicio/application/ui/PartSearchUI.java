package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.renderer.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.renderer.ProfileTableRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.settings.Settings;
import tr.cabro.servicio.application.tablemodal.SearchPartTableModel;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PartSearchUI extends JDialog {
    private JPanel main_panel;
    private JTextField search_field;
    private JButton add_button;
    private JButton cancel_button;
    private JTable product_table;
    private JPanel bottom_panel;
    private JScrollPane table_scroll;
    private JButton all_parts_button;
    private JButton out_stock_button;
    private JButton in_stock_button;
    private JComboBox<String> device_type_combo;

    private final PartService service;
    private TableRowSorter<SearchPartTableModel> sorter = new TableRowSorter<>();
    @Getter
    private final List<Part> selectedParts = new ArrayList<>();

    private SearchPartTableModel partTableModel;
    private final DefaultComboBoxModel<String> deviceTypeComboBoxModel;

    private boolean inStockMode = false;
    private boolean outStockMode = false;

    public PartSearchUI() {
        super((Frame) null, "Ürün Ara", true);
        this.service = ServiceManager.getPartService();
        this.deviceTypeComboBoxModel = new DefaultComboBoxModel<>();

        init();
        add(main_panel);
    }

    private void init() {
        setTitle("Ürün Ara");

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.45);
        int height = (int) (screen_size.height * 0.45);

        setSize(width, height);
        setLocationRelativeTo(null);

        product_table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        search_field.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:15;"
                + "borderWidth:0;"
                + "focusWidth:0;"
                + "innerFocusWidth:0;"
                + "margin:5,20,5,20;"
                + "background:$Table.background");
        search_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ara...");
        search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icon/search.svg"));
        search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        device_type_combo.addActionListener(e -> applyCombinedFilter());
        device_type_combo.setModel(deviceTypeComboBoxModel);
        loadDeviceTypes();

        if (product_table != null) {
            refreshProductTable();
        }

        add_button.addActionListener(e -> onAdd());
        cancel_button.addActionListener(e -> dispose());

        all_parts_button.addActionListener(e -> showAllParts());
        in_stock_button.addActionListener(e -> filterInStock());
        out_stock_button.addActionListener(e -> filterOutStock());
    }

    private void refreshProductTable() {
        partTableModel = new SearchPartTableModel(service.getAll());
        product_table.setModel(partTableModel);

        sorter = new TableRowSorter<>(partTableModel);
        product_table.setRowSorter(sorter);

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING
        };

        product_table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(product_table, columnAlignments));
        product_table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(product_table, 0));
        product_table.getColumnModel().getColumn(2).setCellRenderer(new ProfileTableRenderer(product_table));

        product_table.getColumnModel().getColumn(0).setMaxWidth(50);
        product_table.getColumnModel().getColumn(1).setPreferredWidth(220);
        product_table.getColumnModel().getColumn(2).setPreferredWidth(80);
        product_table.getColumnModel().getColumn(3).setPreferredWidth(150);
        product_table.getColumnModel().getColumn(4).setMaxWidth(50);
        product_table.getColumnModel().getColumn(5).setPreferredWidth(80);
        product_table.getColumnModel().getColumn(6).setPreferredWidth(80);

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
        String selectedType = (String) device_type_combo.getSelectedItem();
        boolean onlyInStock = inStockMode;
        boolean onlyOutStock = outStockMode;

        sorter.setRowFilter(new RowFilter<SearchPartTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends SearchPartTableModel, ? extends Integer> entry) {
                SearchPartTableModel model = (SearchPartTableModel) product_table.getModel();
                int modelRow = entry.getIdentifier();
                Part p = model.getPart(modelRow);

                // Arama filtresi
                String searchText = search_field.getText().trim();
                if (!searchText.isEmpty()) {
                    if (!p.getName().toLowerCase().contains(searchText.toLowerCase()) &&
                            !p.getBarcode().toLowerCase().contains(searchText.toLowerCase())) {
                        return false;
                    }
                }

                // Cihaz türü filtresi
                if (selectedType != null && !selectedType.equals("(Tümü)") && !selectedType.isEmpty()) {
                    if (!selectedType.equalsIgnoreCase(p.getDevice_type())) {
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
        if (search_field.getDocument().getProperty("listenerAttached") != Boolean.TRUE) {
            search_field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
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
            search_field.getDocument().putProperty("listenerAttached", Boolean.TRUE);
        }
    }

    private void applyDoubleClickListener() {
        product_table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && product_table.getSelectedRow() != -1) {
                    int viewRow = product_table.getSelectedRow();
                    int modelRow = product_table.convertRowIndexToModel(viewRow);

                    SearchPartTableModel model = (SearchPartTableModel) product_table.getModel();
                    selectedParts.clear();  // Sadece bir ürün için
                    selectedParts.add(model.getPart(modelRow));

                    dispose();
                }
            }
        });
    }

    private void onAdd() {
        selectedParts.clear();
        selectedParts.addAll(partTableModel.getSelectedPart()); // Modelde işaretli olan tüm parçaları al
        dispose();
    }

    private void loadDeviceTypes() {
        deviceTypeComboBoxModel.removeAllElements();
        deviceTypeComboBoxModel.addElement("(Tümü)"); // <--- Tümünü göster seçeneği
        Settings settings = Servicio.getSettings();
        List<String> types = settings.getDevice_types();
        for (String type : types) {
            deviceTypeComboBoxModel.addElement(type);
        }
    }


}
