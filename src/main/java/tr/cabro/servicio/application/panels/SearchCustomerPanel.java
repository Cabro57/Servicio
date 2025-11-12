package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Setter;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.renderer.CustomerTypeTableRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.tablemodal.SearchCustomerTableModel;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.util.function.Consumer;

public class SearchCustomerPanel extends JPanel {

    private final CustomerService service;
    @Setter
    private Consumer<Customer> onCustomerSelected;

    TableRowSorter<SearchCustomerTableModel> sorter;

    public SearchCustomerPanel() {
        service = ServiceManager.getCustomerService();

        init();
    }

    private void init() {
        initComponent();

        refreshCustomerTable();
    }

    private void refreshCustomerTable() {
        SearchCustomerTableModel model = new SearchCustomerTableModel(service.getAll());
        table.setModel(model);

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));
        table.getColumnModel().getColumn(0).setCellRenderer(new CustomerTypeTableRenderer());

        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(180);

        applySearchFieldListener();
        applyDoubleClickListener();
    }

    private void applySearchFieldListener() {
        if (search_field.getDocument().getProperty("listenerAttached") != Boolean.TRUE) {
            search_field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void applyFilter() {
                    if (sorter == null) return;
                    String text = search_field.getText().trim();
                    if (text.isEmpty()) {
                        sorter.setRowFilter(null);
                    } else {
                        sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
                    }
                }

                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    applyFilter();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    applyFilter();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    applyFilter();
                }
            });

            // Böylece sadece bir kez eklenir
            search_field.getDocument().putProperty("listenerAttached", Boolean.TRUE);
        }
    }

    private void applyDoubleClickListener() {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    int viewRow = table.getSelectedRow();
                    int modelRow = table.convertRowIndexToModel(viewRow);

                    SearchCustomerTableModel model = (SearchCustomerTableModel) table.getModel();
                    Customer customer = model.getCustomer(modelRow);

                    if (onCustomerSelected != null) {
                        onCustomerSelected.accept(customer);
                    }

                }
            }
        });
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 0 10 5 10, wrap 1, width 650", "[grow, fill]", "[]5[grow, fill]"));

        search_field = new JTextField();
        search_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ara...");
        search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icons/search.svg", 0.4f));
        search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search_field.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:15;"
                + "borderWidth:0;"
                + "focusWidth:0;"
                + "innerFocusWidth:0;"
                + "margin:5,20,5,20;"
                + "background:$Table.background");

        table = new JTable();
        JScrollPane table_scroll = new JScrollPane(table);

        add(search_field, "growx, height 25!");
        add(table_scroll, "grow, push");
    }

    public JTextField search_field;
    public JTable table;

}
