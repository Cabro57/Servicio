package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FormPart extends Form {

    private Part part;
    private final PartService partService;
    private final WorkOrderService workOrderService;

    private GenericTableModel<WorkOrder> tableModel;
    private JTable table;

    private JLabel lblPartName;
    private JLabel valSku, valCategory, valPrice, valLastStock;
    private JLabel valCurrentStock, valUsageCount, valRevenue;

    public FormPart(Part part) {
        this.part = part;
        this.partService = ServiceManager.getPartService();
        this.workOrderService = ServiceManager.getWorkOrderService();
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 20", "[::320][grow]", "[pref][grow]"));
        createHeader();
        createLeftColumn();
        createRightColumn();
        refreshData();
    }

    @Override
    public void formRefresh() {
        partService.getById(part.getId()).thenAccept(updated ->
            updated.ifPresent(p -> {
                this.part = p;
                SwingUtilities.invokeLater(this::refreshData);
            })
        );
    }

    private void createHeader() {
        JPanel header = new JPanel(new MigLayout("insets 0, fillx, gap 15", "[][grow][]", "[]"));
        header.setOpaque(false);

        JButton btnBack = new JButton(new Ikon("icons/arrow-left.svg", 1.2f));
        btnBack.putClientProperty(FlatClientProperties.STYLE,
                "arc: 15; background: lighten($Panel.background, 5%); borderWidth: 0; margin: 8,10,8,10;");
        btnBack.addActionListener(e -> FormManager.undo());
        header.add(btnBack, "aligny center");

        JPanel titleBox = new JPanel(new MigLayout("insets 0, gap 0", "[grow]", "[][]"));
        titleBox.setOpaque(false);

        lblPartName = new JLabel(part.getName());
        lblPartName.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");

        JLabel lblSubtitle = new JLabel("Parça Detayı ve Kullanım Geçmişi");
        lblSubtitle.putClientProperty(FlatClientProperties.STYLE,
                "foreground: $Label.disabledForeground; font: -1");

        titleBox.add(lblPartName, "wrap");
        titleBox.add(lblSubtitle);
        header.add(titleBox, "grow");

        JButton btnAddStock = new JButton("+ Stok Girişi");
        btnAddStock.putClientProperty(FlatClientProperties.STYLE,
                "background: #2ecc71; foreground: #ffffff; arc: 10; font: bold");
        btnAddStock.addActionListener(e -> openAddStockDialog());
        header.add(btnAddStock, "aligny center");

        add(header, "span 2, growx, wrap");
    }

    private void createLeftColumn() {
        JPanel left = new JPanel(new MigLayout("insets 0, gapy 20, fillx", "[grow]", "[pref][pref]"));
        left.setOpaque(false);
        left.add(createInfoCard(), "growx, wrap");
        left.add(createSummaryCard(), "growx");
        add(left, "cell 0 1, aligny top");
    }

    private JPanel createInfoCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new MigLayout("insets 20, gapy 15, fillx", "[25!][grow]", "[]15[][][][]"));

        JLabel title = new JLabel("Parça Bilgileri");
        title.setIcon(new Ikon("icons/circuit-board.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2; iconTextGap: 10");
        card.add(title, "span 2, wrap");

        valSku       = new JLabel("-");
        valCategory  = new JLabel("-");
        valPrice     = new JLabel("-");
        valLastStock = new JLabel("-");

        addInfoRow(card, "icons/barcode.svg",       "SKU / Barkod",    valSku);
        addInfoRow(card, "icons/tag.svg",            "Kategori",        valCategory);
        addInfoRow(card, "icons/turkish-lira.svg",   "Birim Fiyat",     valPrice);
        addInfoRow(card, "icons/calendar.svg",       "Son Stok Girişi", valLastStock);

        return card;
    }

    private void addInfoRow(JPanel parent, String iconPath, String label, JLabel valueLabel) {
        JLabel icon = new JLabel(new Ikon(iconPath, 0.75f));
        icon.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        JLabel lblLabel = new JLabel(label);
        lblLabel.putClientProperty(FlatClientProperties.STYLE,
                "foreground: $Label.disabledForeground; font: -1");

        valueLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold");

        parent.add(icon,       "aligny top, span 1 2");
        parent.add(lblLabel,   "wrap");
        parent.add(valueLabel, "gapbottom 10, wrap");
    }

    private JPanel createSummaryCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new MigLayout("insets 20, gapy 15, fillx", "[grow]", "[]10[grow]"));

        JLabel title = new JLabel("Stok ve Kullanım Özeti");
        title.setIcon(new Ikon("icons/sigma.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2; iconTextGap: 10");
        card.add(title, "wrap");

        JPanel grid = new JPanel(new MigLayout("insets 0, gap 10, fill", "[grow][grow]", "[grow][grow]"));
        grid.setOpaque(false);

        valCurrentStock = new JLabel("0");
        valCurrentStock.setHorizontalAlignment(SwingConstants.CENTER);
        valCurrentStock.putClientProperty(FlatClientProperties.STYLE,
                "font: bold +10; foreground: #e67e22");

        valUsageCount = new JLabel("0");
        valUsageCount.setHorizontalAlignment(SwingConstants.CENTER);
        valUsageCount.putClientProperty(FlatClientProperties.STYLE,
                "font: bold +10; foreground: $Component.accentColor");

        valRevenue = new JLabel("0,00 ₺");
        valRevenue.setHorizontalAlignment(SwingConstants.CENTER);
        valRevenue.putClientProperty(FlatClientProperties.STYLE, "font: bold +4");

        grid.add(createMiniStatBox(valCurrentStock, "Mevcut Stok"),   "grow");
        grid.add(createMiniStatBox(valUsageCount,   "Kullanım Sayısı"), "grow, wrap");

        JPanel revenueBox = new JPanel(new MigLayout("insets 15, fill", "[grow]", "[grow][pref]"));
        revenueBox.putClientProperty(FlatClientProperties.STYLE,
                "arc: 12; background: lighten($Panel.background, 3%);");
        JLabel revTitle = new JLabel("Toplam Gelir (Satış)");
        revTitle.setHorizontalAlignment(SwingConstants.CENTER);
        revTitle.putClientProperty(FlatClientProperties.STYLE,
                "foreground: $Label.disabledForeground; font: -1");
        revenueBox.add(valRevenue, "grow, center, wrap");
        revenueBox.add(revTitle,   "grow, center");
        grid.add(revenueBox, "span 2, grow");

        card.add(grid, "grow");
        return card;
    }

    private JPanel createMiniStatBox(JLabel valueLabel, String title) {
        JPanel box = new JPanel(new MigLayout("insets 15, fill", "[grow]", "[grow][pref]"));
        box.putClientProperty(FlatClientProperties.STYLE,
                "arc: 12; background: lighten($Panel.background, 3%);");

        JLabel lblTitle = new JLabel(title);
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
        lblTitle.putClientProperty(FlatClientProperties.STYLE,
                "foreground: $Label.disabledForeground; font: -1");

        box.add(valueLabel, "grow, center, wrap");
        box.add(lblTitle,   "grow, center");
        return box;
    }

    private void createRightColumn() {
        JPanel right = createRoundedCard();
        right.setLayout(new MigLayout("insets 20, fill", "[grow]", "[pref]15[grow]"));

        JLabel title = new JLabel("Kullanıldığı Servis Kayıtları");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        right.add(title, "wrap, growx");

        setupTable();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        right.add(scroll, "grow, push");

        add(right, "cell 1 1, grow");
    }

    private void setupTable() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        List<ColumnDef<WorkOrder>> columns = Arrays.asList(
                new ColumnDef<>("Kayıt No", String.class,        s -> "SRV-" + s.getId()),
                new ColumnDef<>("Cihaz",    Device.class,        WorkOrder::getDevice),
                new ColumnDef<>("Tarih",    String.class,        s -> s.getCreatedAt() != null ? s.getCreatedAt().format(df) : "-"),
                new ColumnDef<>("Durum",    ServiceStatus.class, WorkOrder::getServiceStatus),
                new ColumnDef<>("İşlem",    String.class,        s -> "Detay")
        );

        tableModel = new GenericTableModel<>(columns);
        table = new JTable(tableModel);
        configureTable();
    }

    private void configureTable() {
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:40; separatorColor:$TableHeader.background; font:bold +1;");
        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:50; showHorizontalLines:true; intercellSpacing:0,1; selectionBackground:$TableHeader.hoverBackground;");

        Integer[] alignments = {
                SwingConstants.CENTER, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.CENTER, SwingConstants.CENTER
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, alignments));

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
                ((JLabel) c).putClientProperty(FlatClientProperties.STYLE,
                        "font: bold; foreground: $Label.disabledForeground");
                return c;
            }
        });

        table.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<Device>(
                d -> d != null ? d.getBrand() + " " + d.getModel() : "Belirtilmedi",
                d -> d != null && d.getSerialNo() != null ? "SN: " + d.getSerialNo() : "Bilinmiyor",
                d -> null,
                d -> new Color(130, 130, 130)
        ));

        table.getColumnModel().getColumn(3).setCellRenderer(new UniversalVisualizableRenderer());

        table.getColumnModel().getColumn(4).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(4).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onView(int row) {
                int modelRow = table.convertRowIndexToModel(row);
                WorkOrder s = tableModel.getItemAt(modelRow);
                if (s != null) FormManager.showForm(new FormWorkOrder(s));
            }

            @Override
            public void onEdit(int row) {}

            @Override
            public void onDelete(int row) {}
        }));

        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setMaxWidth(120);
        table.getColumnModel().getColumn(4).setMinWidth(90);
    }

    private void refreshData() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        lblPartName.setText(part.getName());
        valSku.setText(nvl(part.getBarcode()));
        valCategory.setText(nvl(part.getCategory()));
        valPrice.setText(part.getSalePrice() != null ? Format.formatPrice(part.getSalePrice()) : "-");
        valLastStock.setText(part.getUpdatedAt() != null ? part.getUpdatedAt().format(df) : "-");
        valCurrentStock.setText(String.valueOf(part.getStockQuantity()));

        workOrderService.getAllByPart(part.getId()).thenAccept(workOrders ->
                SwingUtilities.invokeLater(() -> {
                    tableModel.setData(workOrders);
                    calculateStats(workOrders);
                })
        );
    }

    private void calculateStats(List<WorkOrder> workOrders) {
        int usageCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (WorkOrder wo : workOrders) {
            if (wo.getItems() != null) {
                for (WorkOrderItem item : wo.getItems()) {
                    if (part.getId().equals(item.getPartId())) {
                        usageCount += item.getQuantity();
                        if (part.getSalePrice() != null) {
                            totalRevenue = totalRevenue.add(
                                    part.getSalePrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                        }
                    }
                }
            }
        }

        final int finalUsage = usageCount;
        final BigDecimal finalRevenue = totalRevenue;
        valUsageCount.setText(String.valueOf(finalUsage));
        valRevenue.setText(Format.formatPrice(finalRevenue));
    }

    private void openAddStockDialog() {
        String input = JOptionPane.showInputDialog(this,
                "Eklenecek stok miktarını girin:", "Stok Girişi", JOptionPane.QUESTION_MESSAGE);
        if (input == null || input.trim().isEmpty()) return;

        int amount;
        try {
            amount = Integer.parseInt(input.trim());
        } catch (NumberFormatException ex) {
            Toast.show(this, Toast.Type.ERROR, "Geçerli bir sayı girin!");
            return;
        }

        if (amount <= 0) {
            Toast.show(this, Toast.Type.WARNING, "Miktar 0'dan büyük olmalıdır!");
            return;
        }

        final int finalAmount = amount;
        partService.addStock(part.getId(), finalAmount, part.getWarehouseId())
                .thenCompose(v -> partService.getById(part.getId()))
                .thenAccept(opt -> SwingUtilities.invokeLater(() -> {
                    opt.ifPresent(updated -> {
                        this.part = updated;
                        refreshData();
                    });
                    Toast.show(this, Toast.Type.SUCCESS, finalAmount + " adet stok eklendi.");
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        String cause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        Toast.show(this, Toast.Type.ERROR, "Stok eklenemedi: " + cause);
                    });
                    return null;
                });
    }

    private JPanel createRoundedCard() {
        JPanel p = new JPanel();
        p.putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");
        return p;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}