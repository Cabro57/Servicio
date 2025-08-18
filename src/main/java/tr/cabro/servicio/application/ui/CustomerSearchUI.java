package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import tr.cabro.servicio.application.component.table.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.CustomerTypeTableRenderer;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.application.tablemodal.SearchCustomerTableModel;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;

public class CustomerSearchUI extends JDialog {
    private JTextField search_field;
    private JTable table;
    private JScrollPane table_scroll;
    private JPanel main_panel;

    @Getter
    private Customer selectedCustomer = null;

    CustomerService customerService;
    TableRowSorter<SearchCustomerTableModel> sorter;

    public CustomerSearchUI(String search_text) {
        customerService = ServiceManager.getCustomerService();

        init();

        search_field.setText(search_text);

        setContentPane(main_panel);
    }

    private void init() {
        setTitle("Müşteri Ara");

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.45);
        int height = (int) (screen_size.height * 0.45);

        setSize(width, height);
        setLocationRelativeTo(null);

        search_field.putClientProperty(FlatClientProperties.STYLE, "" +
                "margin:5,5,5,5;" +
                "background:null;" +
                "focusColor:null;" +
                "font:12;" +
                "arc:8");

        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE, ""
                + "height:35;"
                + "hoverBackground:null;"
                + "pressedBackground:null;"
                + "separatorColor:$TableHeader.background;"
                + "font:bold;"
                + "background:null;");

        table.putClientProperty(FlatClientProperties.STYLE, ""
                + "rowHeight:35;"
                + "showHorizontalLines:true;"
                + "intercellSpacing:0,1;"
                + "cellFocusColor:$TableHeader.hoverBackground;"
                + "selectionBackground:$TableHeader.hoverBackground;"
                + "selectionForeground:$Table.foreground;"
                + "background:null;");

        search_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ara...");
        search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icon/search.svg"));
        search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search_field.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:15;"
                + "borderWidth:0;"
                + "focusWidth:0;"
                + "innerFocusWidth:0;"
                + "margin:5,20,5,20;"
                + "background:$Table.background");



        if (table != null) {
            refreshCustomerTable();
        }
    }

    private void refreshCustomerTable() {
        SearchCustomerTableModel model = new SearchCustomerTableModel(customerService.getAll());
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
                    selectedCustomer = model.getCustomer(modelRow);

                    dispose();  // Dialog'u kapat
                }
            }
        });
    }
}
