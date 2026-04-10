package tr.cabro.servicio.component;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.Getter;
import lombok.Setter;
import net.miginfocom.swing.MigLayout;
import raven.swingpack.JPagination;
import tr.cabro.servicio.application.component.ServicePopup;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.component.util.UIHelper;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class PaginationTable<E> extends JPanel {

    @Getter @Setter
    private List<E> data;

    private int LIMIT;

    private GenericTableModel<Service> serviceTableModel;

    public PaginationTable() {
        this(10);
    }

    public PaginationTable(int limit) {
        this.LIMIT = limit;
        init();
    }

    private void init() {
        initComponent();

        setupTable();

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

        serviceTableModel.setData((List<Service>) pageData);
    }

    private void showPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;

        int row = serviceTable.rowAtPoint(e.getPoint());
        if (row < 0) return;

        serviceTable.setRowSelectionInterval(row, row);

        int modelRow = serviceTable.convertRowIndexToModel(row);
        Service service = serviceTableModel.getItemAt(modelRow);

        JPopupMenu menu = new ServicePopup(service);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void setupTable() {
        // Sütun tanımları
        List<ColumnDef<Service>> columns = Arrays.asList(
                new ColumnDef<>("Müşteri", Customer.class, Service::getCustomer),
                new ColumnDef<>("Cihaz", String.class, Service::getDevice),
                new ColumnDef<>("Ücret", Double.class, Service::getRemainingAmount),
                new ColumnDef<>("Durum", String.class, Service::getServiceStatus),
                new ColumnDef<>("Kayıt Tarih", LocalDateTime.class, Service::getCreatedAt)
        );

        serviceTableModel = new GenericTableModel<>(columns);
        serviceTable.setModel(serviceTableModel);

        configureTable();
    }

    private void configureTable() {
        serviceTable.getColumnModel().getColumn(0).setCellRenderer(new CustomerTableCellRenderer());

        serviceTable.getColumnModel().getColumn(0).setCellRenderer(new CustomerTableCellRenderer());
        serviceTable.getColumnModel().getColumn(1).setCellRenderer(new DeviceTableCellRenderer());
        serviceTable.getColumnModel().getColumn(2).setCellRenderer(new CurrencyTableCellRenderer());
        serviceTable.getColumnModel().getColumn(3).setCellRenderer(new UniversalVisualizableRenderer(SwingConstants.LEFT, 16));
        serviceTable.getColumnModel().getColumn(4).setCellRenderer(new DateTimeTableCellRenderer());
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx,wrap", "[fill]", "[][fill,grow][]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");

        serviceTable = new JTable();

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
                "selectionForeground:$Table.foreground;" +
                "selectionArc: 25");
        scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "" +
                "trackArc:$ScrollBar.thumbArc;" +
                "trackInsets:3,3,3,3;" +
                "thumbInsets:3,3,3,3;" +
                "background:$Table.background;");

        add(scrollPane);

        // create pagination
        pagination = new JPagination(10, 1, 1);

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
