package tr.cabro.servicio.component;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.Getter;
import lombok.Setter;
import net.miginfocom.swing.MigLayout;
import raven.swingpack.JPagination;
import tr.cabro.servicio.application.component.ServicePopup;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.tablemodal.OpenServiceTableModel;
import tr.cabro.servicio.component.util.UIHelper;
import tr.cabro.servicio.model.Service;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.List;

public class PaginationTable<E> extends JPanel {

    @Getter @Setter
    private List<E> data;

    private int LIMIT;

    public PaginationTable() {
        this(10);
    }

    public PaginationTable(int limit) {
        this.LIMIT = limit;
        init();
    }

    private void init() {
        initComponent();

        serviceTable.getColumnModel().getColumn(0).setCellRenderer(new CustomerTableCellRenderer());
        serviceTable.getColumnModel().getColumn(1).setCellRenderer(new DeviceTableCellRenderer());
        serviceTable.getColumnModel().getColumn(2).setCellRenderer(new CurrencyTableCellRenderer());
        serviceTable.getColumnModel().getColumn(3).setCellRenderer(new ServiceStatusTableCellRenderer());
        serviceTable.getColumnModel().getColumn(4).setCellRenderer(new DateTimeTableCellRenderer());

        serviceTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }
        });

        pagination.addChangeListener(e -> {
            showData(pagination.getSelectedPage());
        });
    }

    public void showData() {
        showData(pagination.getSelectedPage());
    }

    private void showData(int page) {
        int pageSize = LIMIT;

        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, data.size());
        List<E> pageData = data.subList(fromIndex, toIndex);

        lbTotalPage.setText(DecimalFormat.getInstance().format(data.size()));
        pagination.getModel().setPageRange(page, (int) Math.ceil((double) data.size() / pageSize));

        OpenServiceTableModel model = (OpenServiceTableModel) serviceTable.getModel();
        model.setData((List<Service>) pageData);
    }

    private void showPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;

        int row = serviceTable.rowAtPoint(e.getPoint());
        int col = serviceTable.columnAtPoint(e.getPoint());

        if (row < 0) return;

        serviceTable.setRowSelectionInterval(row, row); // sağ tık yapılan satırı seç

        OpenServiceTableModel model = (OpenServiceTableModel) serviceTable.getModel();
        int modelRow = serviceTable.convertRowIndexToModel(row);
        Service service = model.getService(modelRow);

        JPopupMenu menu = new ServicePopup(service);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx,wrap", "[fill]", "[][fill,grow][]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");

        // create table and model
        OpenServiceTableModel model = new OpenServiceTableModel();
        serviceTable = new JTable(model);

        // table scroll
        JScrollPane scrollPane = new JScrollPane(serviceTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        UIHelper.fixNestedScroll(scrollPane);

        // style
        serviceTable.getTableHeader().putClientProperty(FlatClientProperties.STYLE, "" +
                "height:35;" +
                "hoverBackground:null;" +
                "pressedBackground:null;" +
                "separatorColor:$TableHeader.background;" +
                "font: bold +2;");
        serviceTable.putClientProperty(FlatClientProperties.STYLE, "" +
                "rowHeight:35;" +
                "showHorizontalLines:true;" +
                "intercellSpacing:0,1;" +
                "cellFocusColor:$TableHeader.hoverBackground;" +
                "selectionBackground:$TableHeader.hoverBackground;" +
                "selectionInactiveBackground:$TableHeader.hoverBackground;" +
                "selectionForeground:$Table.foreground;");
        scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "" +
                "trackArc:$ScrollBar.thumbArc;" +
                "trackInsets:3,3,3,3;" +
                "thumbInsets:3,3,3,3;" +
                "background:$Table.background;");

        add(scrollPane);

        // create pagination
        pagination = new JPagination(11, 1, 1);

        JPanel panelPage = new JPanel(new MigLayout("insets 5 15 5 15", "[][]push[]"));
        lbTotalPage = new JLabel("0");
        pagination.putClientProperty(FlatClientProperties.STYLE, "" +
                "background:null;");
        panelPage.putClientProperty(FlatClientProperties.STYLE, "" +
                "background:null;");
        panelPage.add(new JLabel("Toplam:"));
        panelPage.add(lbTotalPage);
        panelPage.add(pagination);

        add(panelPage);
    }


    private JTable serviceTable;
    private JPagination pagination;
    private JLabel lbTotalPage;


}
