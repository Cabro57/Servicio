package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.enums.DeviceTransactionType;
import tr.cabro.servicio.service.DeviceService;
import tr.cabro.servicio.service.DeviceTransactionService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FormDevice extends Form {

    private Device device;
    private final DeviceService deviceService;
    private final WorkOrderService workOrderService;
    private final DeviceTransactionService deviceTransactionService;

    private GenericTableModel<HistoryEntry> tableModel;
    private JTable table;

    private JLabel lblDeviceTitle;
    private JLabel valType, valBrand, valModel, valSerial, valAccessory, valCreatedAt;

    public FormDevice(Device device) {
        this.device = device;
        this.deviceService = ServiceManager.getDeviceService();
        this.workOrderService = ServiceManager.getWorkOrderService();
        this.deviceTransactionService = ServiceManager.getDeviceTransactionService();
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
        deviceService.get(device.getId()).thenAccept(updated ->
                updated.ifPresent(d -> {
                    this.device = d;
                    SwingUtilities.invokeLater(this::refreshData);
                })
        );
    }

    private void createHeader() {
        JPanel header = new JPanel(new MigLayout("insets 0, fillx, gap 15", "[][grow]", "[]"));
        header.setOpaque(false);

        JButton btnBack = new JButton(new Ikon("icons/arrow-left.svg", 1.2f));
        btnBack.putClientProperty(FlatClientProperties.STYLE,
                "arc: 15; background: lighten($Panel.background, 5%); borderWidth: 0; margin: 8,10,8,10;");
        btnBack.addActionListener(e -> FormManager.undo());
        header.add(btnBack, "aligny center");

        JPanel titleBox = new JPanel(new MigLayout("insets 0, gap 0", "[grow]", "[][]"));
        titleBox.setOpaque(false);

        lblDeviceTitle = new JLabel(device.getDisplayName());
        lblDeviceTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");

        JLabel lblSubtitle = new JLabel("Cihaz Detayı ve Servis Geçmişi");
        lblSubtitle.putClientProperty(FlatClientProperties.STYLE,
                "foreground: $Label.disabledForeground; font: -1");

        titleBox.add(lblDeviceTitle, "wrap");
        titleBox.add(lblSubtitle);
        header.add(titleBox, "grow");

        add(header, "span 2, growx, wrap");
    }

    private void createLeftColumn() {
        JPanel left = new JPanel(new MigLayout("insets 0, gapy 20, fillx", "[grow]", "[pref]"));
        left.setOpaque(false);
        left.add(createInfoCard(), "growx");
        add(left, "cell 0 1, aligny top");
    }

    private JPanel createInfoCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new MigLayout("insets 20, gapy 15, fillx", "[25!][grow]", "[]15[][][][][]"));

        JLabel title = new JLabel("Cihaz Bilgileri");
        title.setIcon(new Ikon("icons/tablet-smartphone.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2; iconTextGap: 10");
        card.add(title, "span 2, wrap");

        valType = new JLabel("-");
        valBrand = new JLabel("-");
        valModel = new JLabel("-");
        valSerial = new JLabel("-");
        valAccessory = new JLabel("-");
        valCreatedAt = new JLabel("-");

        addInfoRow(card, "icons/tablet-smartphone.svg", "Tür", valType);
        addInfoRow(card, "icons/tag.svg", "Marka", valBrand);
        addInfoRow(card, "icons/tag.svg", "Model", valModel);
        addInfoRow(card, "icons/barcode.svg", "Seri No", valSerial);
        addInfoRow(card, "icons/package-check.svg", "Aksesuar", valAccessory);
        addInfoRow(card, "icons/calendar.svg", "Kayıt Tarihi", valCreatedAt);

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

    private void createRightColumn() {
        JPanel right = createRoundedCard();
        right.setLayout(new MigLayout("insets 20, fill", "[grow]", "[pref]15[grow]"));

        JLabel title = new JLabel("Cihaz Geçmişi (Servis + Alım/Satım)");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        right.add(title, "wrap, growx");

        setupTable();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        right.add(scroll, "grow, push");

        add(right, "cell 1 1, grow");
    }

    /** Servis ziyareti (WorkOrder) ile alım/satım olayının (DeviceTransaction) birleşik satırı. */
    private static class HistoryEntry {
        enum Kind { SERVICE, PURCHASE, SALE }

        final Kind kind;
        final LocalDateTime date;
        final String person;
        final String detail;
        final WorkOrder workOrder; // yalnızca SERVICE için dolu

        HistoryEntry(Kind kind, LocalDateTime date, String person, String detail, WorkOrder workOrder) {
            this.kind = kind;
            this.date = date;
            this.person = person;
            this.detail = detail;
            this.workOrder = workOrder;
        }

        String kindLabel() {
            return switch (kind) {
                case SERVICE -> "Servis";
                case PURCHASE -> "Alım";
                case SALE -> "Satım";
            };
        }
    }

    private void setupTable() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        List<ColumnDef<HistoryEntry>> columns = Arrays.asList(
                new ColumnDef<>("Tür",     String.class, HistoryEntry::kindLabel),
                new ColumnDef<>("Kişi",    String.class, h -> h.person),
                new ColumnDef<>("Tarih",   String.class, h -> h.date != null ? h.date.format(df) : "-"),
                new ColumnDef<>("Detay",   String.class, h -> h.detail),
                new ColumnDef<>("İşlem",   String.class, h -> h.kind == HistoryEntry.Kind.SERVICE ? "Detay" : "-")
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

        table.getColumnModel().getColumn(4).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(4).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onView(int row) {
                int modelRow = table.convertRowIndexToModel(row);
                HistoryEntry entry = tableModel.getItemAt(modelRow);
                if (entry != null && entry.workOrder != null) FormManager.showForm(new FormWorkOrder(entry.workOrder));
            }

            @Override
            public void onEdit(int row) {}

            @Override
            public void onDelete(int row) {}
        }));

        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setMaxWidth(120);
        table.getColumnModel().getColumn(4).setMinWidth(90);
    }

    private void refreshData() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        lblDeviceTitle.setText(device.getDisplayName());
        valType.setText(device.getDeviceType() != null ? device.getDeviceType().getName() : "-");
        valBrand.setText(device.getBrand() != null ? device.getBrand().getName() : "-");
        valModel.setText(device.getModel() != null ? device.getModel() : "-");
        valSerial.setText(device.getSerialNo() != null ? device.getSerialNo() : "-");
        valAccessory.setText(device.getAccessory() != null && !device.getAccessory().isBlank() ? device.getAccessory() : "-");
        valCreatedAt.setText(device.getCreatedAt() != null ? device.getCreatedAt().format(df) : "-");

        workOrderService.getAllByDevice(device.getId()).thenAccept(workOrders ->
                deviceTransactionService.getHistoryByDeviceId(device.getId()).thenAccept(transactions -> {
                    List<HistoryEntry> entries = new ArrayList<>();
                    for (WorkOrder wo : workOrders) {
                        Customer customer = wo.getCustomer();
                        entries.add(new HistoryEntry(HistoryEntry.Kind.SERVICE, wo.getCreatedAt(),
                                customer != null ? customer.getFullName() : "Bilinmeyen Müşteri",
                                "SRV-" + wo.getId(), wo));
                    }
                    for (DeviceTransaction t : transactions) {
                        HistoryEntry.Kind kind = t.getType() == DeviceTransactionType.PURCHASE
                                ? HistoryEntry.Kind.PURCHASE : HistoryEntry.Kind.SALE;
                        entries.add(new HistoryEntry(kind, t.getTransactionDate(),
                                t.getCustomer() != null ? t.getCustomer().getFullName() : "-",
                                Format.formatPrice(t.getPrice()), null));
                    }
                    entries.sort(Comparator.comparing((HistoryEntry h) -> h.date,
                            Comparator.nullsLast(Comparator.naturalOrder())).reversed());

                    SwingUtilities.invokeLater(() -> tableModel.setData(entries));
                })
        );
    }

    private JPanel createRoundedCard() {
        JPanel p = new JPanel();
        p.putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");
        return p;
    }
}
