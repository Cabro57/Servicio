package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.forms.FormWorkOrder;
import tr.cabro.servicio.application.forms.FormWorkOrders;
import tr.cabro.servicio.application.component.table.TableStyler;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.util.Format;
import raven.swingpack.JPagination;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

public class PendingPaymentsTable extends JPanel {

    private final WorkOrderService workOrderService;
    private GenericTableModel<WorkOrder> tableModel;
    private JTable table;

    // --- SAYFALAMA (DB-tabanlı) ---
    private final int ITEMS_PER_PAGE = 5; // Her sayfada kaç veri gösterilecek
    private JPagination pagination;

    public PendingPaymentsTable(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
        init();
    }

    private void init() {
        initComponent();
        loadPage(1);
    }

    // --- DB-TABANLI SAYFA YÜKLEME ---
    public void loadPage(int page) {
        workOrderService.getWithDebtPaged(page, ITEMS_PER_PAGE).thenAccept(result ->
                SwingUtilities.invokeLater(() -> applyPage(result))
        ).exceptionally(ex -> {
            Servicio.getLogger().error("Bekleyen tahsilatlar yüklenirken hata oluştu", ex);
            return null;
        });
    }

    private void applyPage(PageResult<WorkOrder> result) {
        if (pagination != null) {
            pagination.setPageRange(result.getPage(), result.getTotalPages());
        }
        if (tableModel != null) {
            tableModel.setData(result.getItems());
            revalidate();
            repaint();
        }
    }

    private void initComponent() {
        // MigLayout'un altına pagination için ekstra satır eklendi
        setLayout(new MigLayout("wrap, fill, insets 15, gapy 0", "[fill]", "[pref!]15[pref!][pref!]15[pref!]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        putClientProperty(FlatClientProperties.STYLE, "background: null");

        // --- 1. BAŞLIK BÖLÜMÜ ---
        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "center"));
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Bekleyen Tahsilatlar");
        title.setIcon(createIcon("icons/circle-alert.svg", new Color(220, 53, 69)));
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font; iconTextGap: 8");

        JButton btnSeeAll = new JButton("Tümünü Gör \u2192");
        btnSeeAll.putClientProperty(FlatClientProperties.STYLE, "font: $medium.font");
        btnSeeAll.putClientProperty("JButton.buttonType", "toolBarButton");
        btnSeeAll.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSeeAll.addActionListener(e -> {
            Form form = AllForms.getForm(FormWorkOrders.class);
            FormManager.showForm(form);
        });

        headerPanel.add(title);
        headerPanel.add(btnSeeAll);

        // --- 2. TABLO BÖLÜMÜ ---
        List<ColumnDef<WorkOrder>> columns = Arrays.asList(
                new ColumnDef<>("Müşteri", String.class, s -> s.getCustomer() != null ? s.getCustomer().getFullName() : "-"),
                new ColumnDef<>("Kayıt No", String.class, s -> "SRV-" + s.getId()),
                new ColumnDef<>("Tutar", String.class, s -> Format.formatPrice(s.getRemainingAmount()))
        );

        tableModel = new GenericTableModel<>(columns);
        table = new JTable(tableModel);

        table.setFocusable(false);
        table.setCursor(new Cursor(Cursor.HAND_CURSOR));

        TableStyler.applyDashboardStyle(table);

        // -- ÖZEL RENDERER İŞLEMLERİ --
        TableCellRenderer defaultHeaderRenderer = table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((t, value, isSelected, hasFocus, row, column) -> {
            JLabel label = (JLabel) defaultHeaderRenderer.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            if (column == 2) {
                label.setHorizontalAlignment(SwingConstants.TRAILING);
            } else {
                label.setHorizontalAlignment(SwingConstants.LEADING);
            }
            label.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
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
                if (!isSelected) {
                    label.setForeground(UIManager.getColor("Label.disabledForeground"));
                }
                return label;
            }
        });

        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setForeground(new Color(255, 107, 107)); // Soft kırmızı
                label.setHorizontalAlignment(SwingConstants.TRAILING);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
                return label;
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow >= 0 && e.getClickCount() == 1) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    WorkOrder selectedWorkOrder = tableModel.getItemAt(modelRow);
                    if (selectedWorkOrder != null) {
                        FormManager.showForm(new FormWorkOrder(selectedWorkOrder));
                    }
                }
            }
        });

        // --- 3. SAYFALAMA (PAGINATION) BİLEŞENİ ---
        // (Maksimum 5 görünür buton, başlangıç sayfası 1, toplam sayfa 1)
        pagination = new JPagination(5, 1, 1);
        pagination.setBackground(null);
        pagination.addChangeListener(e -> loadPage(pagination.getSelectedPage()));

        // Bileşenleri ana panele ekle
        add(headerPanel);
        add(table.getTableHeader());
        add(table);
        add(pagination, "align center"); // Sayfalamayı en alta, merkeze hizala
    }

    private Icon createIcon(String icon, Color color) {
        return new FlatSVGIcon(icon, 1f).setColorFilter(new FlatSVGIcon.ColorFilter(color1 -> color));
    }
}