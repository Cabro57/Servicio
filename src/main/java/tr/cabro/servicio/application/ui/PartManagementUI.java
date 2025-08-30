package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.renderer.AlignedRenderer;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.application.tablemodal.PartTableModel;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.application.component.table.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.component.table.TableHeaderAlignment;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;

public class PartManagementUI extends JDialog {
    private JPanel main_panel;
    private JTextField search_field;
    private JButton new_button;
    private JButton edit_button;
    private JButton order_list_button;
    private JScrollPane table_scroll;
    private JTable product_table;
    private JButton delete_button;

    private final PartService service = ServiceManager.getPartService();

    public PartManagementUI() {
        init();

        add(main_panel);
    }


    private void init() {

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.6);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);

        setLocationRelativeTo(null);

        product_table.getTableHeader().putClientProperty(FlatClientProperties.STYLE, ""
                + "height:30;"
                + "hoverBackground:null;"
                + "pressedBackground:null;"
                + "separatorColor:$TableHeader.background;"
                + "font:bold;");

        product_table.putClientProperty(FlatClientProperties.STYLE, ""
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



        search_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Barkod veya Ürün Ara...");
        search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icon/search.svg"));
        search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search_field.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:15;"
                + "borderWidth:0;"
                + "focusWidth:0;"
                + "innerFocusWidth:0;"
                + "margin:5,20,5,20;"
                + "background:$Table.background");

        new_button.addActionListener(e -> new_part_cmd());

        edit_button.addActionListener(e -> edit_part_cmd());

        delete_button.addActionListener(e -> delete_part_cmd());

        order_list_button.addActionListener(e -> order_list_cmd());


        if (product_table != null) {
            refreshProductTable();
        }
    }

    private void new_part_cmd() {
        PartEditUI dialog = new PartEditUI();
        dialog.setModal(true);
        dialog.setVisible(true);

        refreshProductTable();
    }

    private void edit_part_cmd() {
        List<Part> ps = ((PartTableModel) product_table.getModel()).getSelectedProducts();

        if (ps.size() == 1) {
            PartEditUI dialog = new PartEditUI();
            dialog.loadPartByBarcode(ps.get(0).getBarcode());
            dialog.setModal(true);
            dialog.setVisible(true);
        }

        refreshProductTable();
    }

    private void delete_part_cmd() {
        List<Part> ps = ((PartTableModel) product_table.getModel()).getSelectedProducts();

        if (ps.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Lütfen silmek için bir müşteri seçin.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Seçilen " + ps.size() + " müşteriyi silmek istediğinizden emin misiniz?",
                "Silme Onayı", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            for (Part p : ps) {
                service.deletePart(p);
            }
            refreshProductTable();
        }
    }

    private void order_list_cmd() {
        List<Part> parts = service.getPartsBelowMinStock();
        if (parts.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Minimum stok altı ürün yok.", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
        } else {
            StringBuilder sb = new StringBuilder("Sipariş Listesi:\n");
            for (Part p : parts) {
                sb.append(p.getBarcode()).append(" - ")
                        .append(p.getBrand()).append(" ")
                        .append(p.getName()).append(" | Stok: ")
                        .append(p.getStock()).append("\n");
            }
            JTextArea area = new JTextArea(sb.toString());
            area.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(area);
            scrollPane.setPreferredSize(new Dimension(400, 300));
            JOptionPane.showMessageDialog(this, scrollPane, "Sipariş Listesi", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void refreshProductTable() {
        PartTableModel model = new PartTableModel(service.getAllParts());
        product_table.setModel(model);

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.CENTER,
                SwingConstants.TRAILING,
                SwingConstants.TRAILING,
                SwingConstants.LEADING
        };

        product_table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(product_table, columnAlignments));
        product_table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(product_table, 0));
        product_table.getColumnModel().getColumn(6).setCellRenderer(new AlignedRenderer(product_table, 6, SwingConstants.CENTER));

        product_table.getColumnModel().getColumn(0).setMaxWidth(50);   // SELECT (checkbox)
        product_table.getColumnModel().getColumn(1).setMinWidth(150);   // Barkod
        product_table.getColumnModel().getColumn(2).setPreferredWidth(100);  // Marka
        product_table.getColumnModel().getColumn(3).setPreferredWidth(120);  // Ürün Adı
        product_table.getColumnModel().getColumn(4).setPreferredWidth(70);  // Cihaz Türü
        product_table.getColumnModel().getColumn(5).setPreferredWidth(120);  // Uyumlu Modeller
        product_table.getColumnModel().getColumn(6).setPreferredWidth(50);  // Stok
        product_table.getColumnModel().getColumn(7).setPreferredWidth(70);  // Alış Fiyatı
        product_table.getColumnModel().getColumn(8).setPreferredWidth(70);  // Satış Fiyatı
        product_table.getColumnModel().getColumn(9).setPreferredWidth(70);  // Alış Tarihi

        TableRowSorter<PartTableModel> sorter = new TableRowSorter<>(model);
        product_table.setRowSorter(sorter);

        if (search_field.getDocument().getProperty("listenerAttached") == null) {
            search_field.getDocument().addDocumentListener(new DocumentListener() {
                private void applyFilter() {
                    String text = search_field.getText().trim();
                    if (text.isEmpty()) {
                        sorter.setRowFilter(null);
                    } else {
                        sorter.setRowFilter(new RowFilter<TableModel, Integer>() {
                            @Override
                            public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                                String barcode = entry.getStringValue(1).toLowerCase();
                                String name = entry.getStringValue(3).toLowerCase();
                                String search = text.toLowerCase();
                                return barcode.startsWith(search) || name.startsWith(search);
                            }
                        });
                    }
                }


                @Override
                public void insertUpdate(DocumentEvent e) {
                    applyFilter();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    applyFilter();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    applyFilter();
                }
            });
            search_field.getDocument().putProperty("listenerAttached", true);
        }
    }
}
