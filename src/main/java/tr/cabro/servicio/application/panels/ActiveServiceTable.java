package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.system.AllForms;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.application.forms.FormWorkOrder;
import tr.cabro.servicio.application.forms.FormWorkOrders;
import tr.cabro.servicio.application.renderer.UniversalVisualizableRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.enums.ServiceStatus;
import raven.swingpack.JPagination;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActiveServiceTable extends JPanel {

    private GenericTableModel<WorkOrder> tableModel;
    private JTable table;

    // --- SAYFALAMA DEĞİŞKENLERİ ---
    private List<WorkOrder> allData = new ArrayList<>();
    private final int ITEMS_PER_PAGE = 5; // Her sayfada kaç veri gösterilecek
    private JPagination pagination;

    public ActiveServiceTable() {
        init();
    }

    private void init() {
        initComponent();
    }

    // --- YENİ VERİ YÜKLEME METODU ---
    public void setData(List<WorkOrder> workOrder) {
        this.allData = workOrder != null ? workOrder : new ArrayList<>();

        int totalPages = (int) Math.ceil((double) allData.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        if (pagination != null) {
            pagination.setPageRange(1, totalPages);
        }

        // İlk sayfayı tabloya bas
        updateTablePage(1);
    }

    // --- SAYFA GÜNCELLEME MOTORU ---
    private void updateTablePage(int page) {
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allData.size());

        if (startIndex <= endIndex && tableModel != null) {
            tableModel.setData(allData.subList(startIndex, endIndex));
            revalidate();
            repaint();
        }
    }

    private void initComponent() {
        // MigLayout satırlarına pagination için "15[pref!]" alanı eklendi
        setLayout(new MigLayout("wrap, fillx, insets 15, gapy 0", "[fill]", "[pref!]15[pref!][pref!]15[pref!]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");

        // --- 1. BAŞLIK BÖLÜMÜ ---
        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "center"));
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Aktif Servisler");
        title.setIcon(createIcon("icons/activity.svg", new Color(13, 110, 253))); // Şık Mavi
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font; iconTextGap: 8");

        JButton btnSeeAll = new JButton("Tümünü Gör \u2192");
        btnSeeAll.putClientProperty(FlatClientProperties.STYLE, "font: $light.font");
        btnSeeAll.putClientProperty( "JButton.buttonType", "toolBarButton" );
        btnSeeAll.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSeeAll.addActionListener(e -> {
            Form form = AllForms.getForm(FormWorkOrders.class);
            FormManager.showForm(form);
        });

        headerPanel.add(title);
        headerPanel.add(btnSeeAll);

        // --- 2. TABLO BÖLÜMÜ ---
        List<ColumnDef<WorkOrder>> columns = Arrays.asList(
                new ColumnDef<>("Müşteri", Customer.class, s -> s.getCustomer() != null ? s.getCustomer().getFullName() : "-"),
                new ColumnDef<>("Cihaz", Device.class, WorkOrder::getDevice),
                new ColumnDef<>("Durum", ServiceStatus.class, WorkOrder::getServiceStatus)
        );

        tableModel = new GenericTableModel<>(columns);
        table = new JTable(tableModel);

        table.setFocusable(false);
        table.setCursor(new Cursor(Cursor.HAND_CURSOR));

        applyTableStyles(table);

        // -- ÖZEL RENDERER İŞLEMLERİ --
        TableCellRenderer defaultHeaderRenderer = table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((t, value, isSelected, hasFocus, row, column) -> {
            JLabel label = (JLabel) defaultHeaderRenderer.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(SwingConstants.LEADING);
            return label;
        });

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                return label;
            }
        });

        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Device) {
                    Device device = (Device) value;
                    label.setText(device.getDisplayName());
                    if (!isSelected) {
                        label.setForeground(UIManager.getColor("Label.disabledForeground"));
                    }
                }

                return label;
            }
        });

        table.getColumnModel().getColumn(2).setCellRenderer(new UniversalVisualizableRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow >= 0 && e.getClickCount() == 1) { // Sadece Tek Tık
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    WorkOrder selectedWorkOrder = tableModel.getItemAt(modelRow);
                    if (selectedWorkOrder != null) {
                        FormManager.showForm(new FormWorkOrder(selectedWorkOrder));
                    }
                }
            }
        });

        // --- 3. SAYFALAMA (PAGINATION) BİLEŞENİ ---
        pagination = new JPagination(5, 1, 1);
        pagination.addChangeListener(e -> {
            int page = pagination.getSelectedPage();
            updateTablePage(page);
        });

        // Bileşenleri ana panele ekle
        add(headerPanel);
        add(table.getTableHeader());
        add(table);
        add(pagination, "align center"); // Sayfalamayı en alta merkeze ekle
    }

    private void applyTableStyles(JTable table) {
        table.setRowHeight(40);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setOpaque(false);
        ((JComponent)table.getDefaultRenderer(Object.class)).setOpaque(false);
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:40; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font:bold -1;");
        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:37; showHorizontalLines:true; intercellSpacing:0,1; " +
                        "cellFocusColor:null; selectionBackground:$TableHeader.hoverBackground; " +
                        "selectionForeground:$Table.foreground;");
    }

    private Icon createIcon(String icon, Color color) {
        return new FlatSVGIcon(icon, 1).setColorFilter(new FlatSVGIcon.ColorFilter(color1 -> color));
    }
}