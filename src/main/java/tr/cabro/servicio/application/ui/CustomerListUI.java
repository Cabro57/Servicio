package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.renderer.AlignedRenderer;
import tr.cabro.servicio.application.renderer.CustomerTypeTableRenderer;
import tr.cabro.servicio.application.renderer.ServiceStatusTableRenderer;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.application.tablemodal.CustomerTableModel;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.application.compenents.table.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.compenents.table.ProfileTableRenderer;
import tr.cabro.servicio.application.compenents.table.TableHeaderAlignment;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;

public class CustomerListUI extends JDialog {

    private JButton new_button;
    private JButton edit_button;
    private JButton delete_button;
    private JTable customer_table;
    private JScrollPane table_scroll;
    private JPanel main_panel;
    private JTextField search_field;
    private JPanel table_panel;

    private final CustomerService customerService;
    private TableRowSorter<CustomerTableModel> sorter;

    public CustomerListUI() {
        customerService = ServiceManager.getCustomerService();

        init();
        add(main_panel);
    }

    private void init() {
        setTitle("Müşteri Listesi");

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.6);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);

        table_panel.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:18;"
                + "background:$Table.background");

        customer_table.getTableHeader().putClientProperty(FlatClientProperties.STYLE, ""
                + "height:30;"
                + "hoverBackground:null;"
                + "pressedBackground:null;"
                + "separatorColor:$TableHeader.background;"
                + "font:bold;");

        customer_table.putClientProperty(FlatClientProperties.STYLE, ""
                + "rowHeight:25;"
                + "showHorizontalLines:true;"
                + "intercellSpacing:0,1;"
                + "cellFocusColor:$TableHeader.hoverBackground;"
                + "selectionBackground:$TableHeader.hoverBackground;"
                + "selectionForeground:$Table.foreground;");

        table_scroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, ""
                + "trackArc:999;"
                + "trackInsets:3,3,3,3;"
                + "thumbInsets:3,3,3,3;"
                + "background:$Table.background;");

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

        new_button.addActionListener(e -> new_customer_cmd());
        edit_button.addActionListener(e -> edit_customer_cmd());
        delete_button.addActionListener(e -> delete_customer_cmd());

        refreshCustomerTable();
    }

    private void new_customer_cmd() {
        CustomerEditUI dialog = new CustomerEditUI(null);
        dialog.setModal(true);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            Customer c = dialog.getCustomerFromForm();
            c.setCreated_at(LocalDateTime.now());
            customerService.save(c, false);
            refreshCustomerTable();
        }
    }

    private void edit_customer_cmd() {
        List<Customer> cs = ((CustomerTableModel) customer_table.getModel()).getSelectedCustomers();

        if (cs.size() == 1) {
            CustomerEditUI dialog = new CustomerEditUI(cs.get(0));
            dialog.setModal(true);
            dialog.setVisible(true);

            if (dialog.isConfirmed()) {
                Customer c = dialog.getCustomerFromForm();
                customerService.save(c, true);
                refreshCustomerTable();
            }
        } else if (cs.size() > 1) {
            JOptionPane.showMessageDialog(this, "Düzenlemek için sadece 1 kişi seçin.");
        } else {
            JOptionPane.showMessageDialog(this, "Lütfen düzenlemek için bir müşteri seçin.");
        }
    }

    private void delete_customer_cmd() {
        List<Customer> cs = ((CustomerTableModel) customer_table.getModel()).getSelectedCustomers();

        if (cs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Lütfen silmek için bir müşteri seçin.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Seçilen " + cs.size() + " müşteriyi silmek istediğinizden emin misiniz?",
                "Silme Onayı", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            int count = 0;
            for (Customer c : cs) {
                if (customerService.delete(c.getID())) {
                    count++;
                }
            }
            Servicio.getInstance().getLogger().info(count+"");
            refreshCustomerTable();
        }
    }

    private void refreshCustomerTable() {
        CustomerTableModel model = new CustomerTableModel(customerService.getAll());
        customer_table.setModel(model);

        sorter = new TableRowSorter<>(model);
        customer_table.setRowSorter(sorter);

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING
        };

        customer_table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(customer_table, columnAlignments));
        customer_table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(customer_table, 0));
        customer_table.getColumnModel().getColumn(1).setCellRenderer(new AlignedRenderer(customer_table, 1, SwingConstants.CENTER));
        customer_table.getColumnModel().getColumn(2).setCellRenderer(new ProfileTableRenderer(customer_table));
        customer_table.getColumnModel().getColumn(7).setCellRenderer(new CustomerTypeTableRenderer());

        customer_table.getColumnModel().getColumn(0).setMaxWidth(50);
        customer_table.getColumnModel().getColumn(1).setMaxWidth(40);
        customer_table.getColumnModel().getColumn(2).setPreferredWidth(150);
        customer_table.getColumnModel().getColumn(3).setPreferredWidth(120);
        customer_table.getColumnModel().getColumn(4).setPreferredWidth(100);
        customer_table.getColumnModel().getColumn(5).setPreferredWidth(180);
        customer_table.getColumnModel().getColumn(6).setPreferredWidth(100);
        customer_table.getColumnModel().getColumn(7).setPreferredWidth(80);
        customer_table.getColumnModel().getColumn(8).setPreferredWidth(80);

        applySearchFieldListener();
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

}
