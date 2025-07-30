package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.compenents.table.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.compenents.table.ProfileTableRenderer;
import tr.cabro.servicio.application.compenents.table.TableHeaderAlignment;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.application.tablemodal.SupplierTableModel;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;

public class SupplierListUI extends JDialog {
    private JTextField search_field;
    private JButton add_button;
    private JButton edit_button;
    private JButton delete_button;
    private JTable table;
    private JPanel main_panel;
    private JScrollPane table_scroll;
    private JPanel table_panel;

    private SupplierTableModel tableModel;
    private TableRowSorter<SupplierTableModel> sorter;

    public SupplierListUI() {
        super(Servicio.getInstance().getFrame(), "Tedarikçi Listesi", true);
        init();

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.6);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);

        setContentPane(main_panel);
    }


    private void init() {
        loadSuppliers();

        table_panel.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:18;"
                + "background:$Table.background");

        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE, ""
                + "height:30;"
                + "hoverBackground:null;"
                + "pressedBackground:null;"
                + "separatorColor:$TableHeader.background;"
                + "font:bold;");

        table.putClientProperty(FlatClientProperties.STYLE, ""
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

        search_field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
        });

        add_button.addActionListener(e -> onAdd());
        edit_button.addActionListener(e -> onEdit());
        delete_button.addActionListener(e -> onDelete());
    }

    private void onAdd() {
        new SupplierEditUI(null, this::loadSuppliers).setVisible(true);
    }

    private void onEdit() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Lütfen bir tedarikçi seçin!");
            return;
        }
        Supplier selected = tableModel.getSupplier(selectedRow);
        new SupplierEditUI(selected, this::loadSuppliers).setVisible(true);
    }

    private void onDelete() {
        List<Supplier> selected = tableModel.getSelectedSuppliers();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Lütfen silinecek tedarikçileri seçin!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Seçili tedarikçileri silmek istediğinize emin misiniz?",
                "Onay",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            SupplierService supplierService = ServiceManager.getSupplierService();
            for (Supplier s : selected) {
                supplierService.delete(s.getId());
            }
            loadSuppliers();
        }
    }

    private void filter() {
        String text = search_field.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text)); // büyük/küçük harf duyarsız
        }
    }

    private void loadSuppliers() {
        SupplierService supplierService = ServiceManager.getSupplierService();
        List<Supplier> suppliers = supplierService.getAll();
        tableModel = new SupplierTableModel(suppliers);
        table.setModel(tableModel);

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(table, 0));
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table));
        table.getColumnModel().getColumn(2).setCellRenderer(new ProfileTableRenderer(table));

        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
    }
}
