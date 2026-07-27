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
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FormSupplier extends Form {

    private Supplier supplier;
    private final SupplierService supplierService;
    private final PartService partService;

    private GenericTableModel<Part> tableModel;
    private JTable table;

    private JLabel lblSupplierName;
    private JLabel valPhone, valEmail, valAddress, valTaxNumber, valTaxOffice, valCreatedAt;

    public FormSupplier(Supplier supplier) {
        this.supplier = supplier;
        this.supplierService = ServiceManager.getSupplierService();
        this.partService = ServiceManager.getPartService();
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
        supplierService.get(supplier.getId()).thenAccept(updated ->
                updated.ifPresent(s -> {
                    this.supplier = s;
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

        lblSupplierName = new JLabel(supplier.toString());
        lblSupplierName.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");

        JLabel lblSubtitle = new JLabel("Tedarikçi Detayı");
        lblSubtitle.putClientProperty(FlatClientProperties.STYLE,
                "foreground: $Label.disabledForeground; font: -1");

        titleBox.add(lblSupplierName, "wrap");
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
        card.setLayout(new MigLayout("insets 20, gapy 15, fillx", "[25!][grow]", "[]15[][][][][][]"));

        JLabel title = new JLabel("Tedarikçi Bilgileri");
        title.setIcon(new Ikon("icons/store.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2; iconTextGap: 10");
        card.add(title, "span 2, wrap");

        valPhone = new JLabel("-");
        valEmail = new JLabel("-");
        valAddress = new JLabel("-");
        valTaxNumber = new JLabel("-");
        valTaxOffice = new JLabel("-");
        valCreatedAt = new JLabel("-");

        addInfoRow(card, "icons/phone.svg", "Telefon", valPhone);
        addInfoRow(card, "icons/mail.svg", "E-posta", valEmail);
        addInfoRow(card, "icons/map-pin.svg", "Adres", valAddress);
        addInfoRow(card, "icons/landmark.svg", "Vergi No", valTaxNumber);
        addInfoRow(card, "icons/landmark.svg", "Vergi Dairesi", valTaxOffice);
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

        JLabel title = new JLabel("Tedarik Ettiği Parçalar");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        right.add(title, "wrap, growx");

        setupTable();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        right.add(scroll, "grow, push");

        add(right, "cell 1 1, grow");
    }

    private void setupTable() {
        List<ColumnDef<Part>> columns = Arrays.asList(
                new ColumnDef<>("Ad", String.class, Part::getName),
                new ColumnDef<>("Kategori", String.class, p -> p.getCategory() != null ? p.getCategory().getName() : "-"),
                new ColumnDef<>("Stok", String.class, p -> String.valueOf(p.getStockQuantity())),
                new ColumnDef<>("Fiyat", String.class, p -> Format.formatPrice(p.getSalePrice())),
                new ColumnDef<>("İşlem", String.class, p -> "Detay")
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
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.CENTER,
                SwingConstants.TRAILING, SwingConstants.CENTER
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, alignments));

        table.getColumnModel().getColumn(4).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(4).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onView(int row) {
                int modelRow = table.convertRowIndexToModel(row);
                Part p = tableModel.getItemAt(modelRow);
                if (p != null) FormManager.showForm(new FormPart(p));
            }

            @Override
            public void onEdit(int row) {}

            @Override
            public void onDelete(int row) {}
        }));

        table.getColumnModel().getColumn(2).setMaxWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setMaxWidth(120);
        table.getColumnModel().getColumn(4).setMinWidth(90);
    }

    private void refreshData() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        lblSupplierName.setText(supplier.toString());
        valPhone.setText(nvl(Format.formatPhoneNumber(supplier.getPhone())));
        valEmail.setText(nvl(supplier.getEmail()));
        valAddress.setText(nvl(supplier.getAddress()));
        valTaxNumber.setText(nvl(supplier.getTaxNumber()));
        valTaxOffice.setText(nvl(supplier.getTaxOffice()));
        valCreatedAt.setText(supplier.getCreatedAt() != null ? supplier.getCreatedAt().format(df) : "-");

        partService.getBySupplierId(supplier.getId()).thenAccept(parts ->
                SwingUtilities.invokeLater(() -> tableModel.setData(parts))
        );
    }

    private JPanel createRoundedCard() {
        JPanel p = new JPanel();
        p.putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");
        return p;
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
