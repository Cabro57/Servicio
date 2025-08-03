package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;

import tr.cabro.servicio.application.compenents.table.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.CustomerTableRenderer;
import tr.cabro.servicio.application.renderer.ServiceStatusTableRenderer;
import tr.cabro.servicio.application.tablemodal.ServiceListTableModel;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;

public class ServiceListUI extends JDialog {
    private JPanel main_panel;
    private JTable table;
    private JTextField search_field;
    private JButton all_device_button;
    private JButton repair_button;
    private JButton ready_button;
    private JButton other_service_button;
    private JButton delivery_button;
    private JButton return_button;
    private JButton part_wait_button;
    private JPanel information_panel;
    private JPanel table_panel;
    private JScrollPane table_scroll;
    private JPanel buttons_panel;

    private final RepairService service;
    private TableRowSorter<ServiceListTableModel> sorter;

    public ServiceListUI() {
        super((Frame) null, "Servis Kayıtları", true);

        this.service = ServiceManager.getRepairService();

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.8);
        int height = (int) (screen_size.height * 0.8);
        setSize(width, height);
        setMinimumSize(new Dimension(width, height));
        setLocationRelativeTo(null);

        init();

        setContentPane(main_panel);
    }

    private void init() {



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
                + "rowHeight:50;"
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


        refreshTable();
    }

    private void refreshTable() {
        ServiceListTableModel model = new ServiceListTableModel(service.getAllServices());
        table.setModel(model);

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.TRAILING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));
        table.getColumnModel().getColumn(1).setCellRenderer(new CustomerTableRenderer());

        table.getColumnModel().getColumn(6).setCellRenderer(new ServiceStatusTableRenderer());

        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(80);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);
        table.getColumnModel().getColumn(7).setPreferredWidth(80);
    }
}
