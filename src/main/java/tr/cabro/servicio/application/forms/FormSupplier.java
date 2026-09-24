package tr.cabro.servicio.application.forms;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.detail.DetailHeader;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.component.detail.DetailListSection;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.panels.edit.EditModals;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tedarikçi detayı: kimlik şeridinde parça çeşidi, stoktaki mal değeri ve kritik parça sayısı;
 * altta tedarik ettiği parçalar (Tümü / Sipariş gerekli), sağda iletişim ve fatura bilgileri.
 * Birincil işlem kritik parçaların sipariş listesini panoya kopyalar (WhatsApp/e-postaya yapıştırılır).
 */
public class FormSupplier extends Form {

    private static final String VIEW_ALL = "all";
    private static final String VIEW_REORDER = "reorder";

    private Supplier supplier;
    private final SupplierService supplierService;
    private final PartService partService;

    private DetailHeader header;
    private DetailListSection<Part> partsSection;
    private ViewTabs views;
    private List<Part> parts = Collections.emptyList();

    private JLabel factPhone, factEmail, factAddress, factTaxNo, factTaxOffice, factCreated;
    private JPanel noteCard;
    private JTextArea note;

    public FormSupplier(Supplier supplier) {
        this.supplier = supplier;
        this.supplierService = ServiceManager.getSupplierService();
        this.partService = ServiceManager.getPartService();
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 16", "[grow, fill][340!, fill]", "[pref][grow, fill]"));

        header = new DetailHeader();
        header.addStat("variety", "Parça çeşidi");
        header.addStat("value", "Stoktaki mal değeri");
        header.addStat("critical", "Sipariş gereken");
        header.addAction("Düzenle", "icons/pencil.svg", () -> EditModals.editSupplier(this, supplier, this::formRefresh));
        header.setPrimary("Sipariş Listesi", "icons/clipboard-list.svg", this::copyReorderList);
        add(header, "span 2, growx, wmin 0, wrap");

        partsSection = new DetailListSection<>("Tedarik ettiği parçalar", Arrays.asList(
                new ColumnDef<Part>("Parça", Part.class, p -> p).alignment(SwingConstants.LEADING),
                new ColumnDef<Part>("SKU", String.class, Part::getBarcode).alignment(SwingConstants.LEADING),
                new ColumnDef<Part>("Stok", Part.class, p -> p).alignment(SwingConstants.CENTER),
                new ColumnDef<Part>("Alış", BigDecimal.class, Part::getPurchasePrice).alignment(SwingConstants.TRAILING),
                new ColumnDef<Part>("Satış", BigDecimal.class, Part::getSalePrice).alignment(SwingConstants.TRAILING)
        ), "Bu tedarikçiye bağlı parça yok", "Parça eklerken ya da düzenlerken tedarikçi olarak seçildiğinde burada görünür.", false);
        views = new ViewTabs();
        views.addView(VIEW_ALL, "Tümü");
        views.addView(VIEW_REORDER, "Sipariş gerekli");
        views.setOnChange(k -> applyView());
        partsSection.setTabs(views);

        JTable t = partsSection.getTable();
        t.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Part>(
                Part::getName,
                p -> p.getCategory() != null ? p.getCategory().getName() : "Kategorisiz"));
        t.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean s, boolean f, int row, int col) {
                super.getTableCellRendererComponent(table, value, s, false, row, col);
                setForeground(UIManager.getColor("Label.disabledForeground"));
                return this;
            }
        });
        t.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean s, boolean f, int row, int col) {
                super.getTableCellRendererComponent(table, value, s, false, row, col);
                Part p = (Part) value;
                int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
                boolean critical = isCritical(p);
                setHorizontalAlignment(CENTER);
                setFont(table.getFont().deriveFont(Font.BOLD));
                setText(stock <= 0 ? "Tükendi" : critical ? stock + "  ·  min " + p.getMinStockLevel() : String.valueOf(stock));
                setForeground(critical || stock <= 0 ? SemanticColor.danger() : table.getForeground());
                return this;
            }
        });
        t.getColumnModel().getColumn(3).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));
        t.getColumnModel().getColumn(4).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));
        t.getColumnModel().getColumn(0).setPreferredWidth(320);
        t.getColumnModel().getColumn(0).setMinWidth(180);
        t.getColumnModel().getColumn(1).setPreferredWidth(100);
        t.getColumnModel().getColumn(2).setPreferredWidth(110);
        t.getColumnModel().getColumn(2).setMaxWidth(130);
        partsSection.setOnOpen(p -> FormManager.showForm(new FormPart(p)));
        add(partsSection, "grow, wmin 0, hmin 0");

        add(DetailKit.scroll(buildSideColumn()), "grow, hmin 0");
        refreshData();
    }

    private JPanel buildSideColumn() {
        JPanel column = new JPanel(new MigLayout("insets 0, wrap, fillx, gapy 16", "[grow, fill]", ""));
        column.setOpaque(false);

        JPanel contact = DetailKit.card("İletişim", null);
        JPanel f1 = DetailKit.facts();
        factPhone = DetailKit.fact(f1, "Telefon");
        factEmail = DetailKit.fact(f1, "E-posta");
        factAddress = DetailKit.fact(f1, "Adres");
        contact.add(f1);
        column.add(contact);

        JPanel billing = DetailKit.card("Fatura bilgileri", null);
        JPanel f2 = DetailKit.facts();
        factTaxNo = DetailKit.fact(f2, "Vergi no");
        factTaxOffice = DetailKit.fact(f2, "Vergi dairesi");
        factCreated = DetailKit.fact(f2, "Kayıt tarihi");
        billing.add(f2);
        column.add(billing);

        noteCard = DetailKit.card("Not", null);
        note = new JTextArea();
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setBorder(null);
        noteCard.add(note, "wmin 0");
        column.add(noteCard);
        return column;
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

    private static boolean isCritical(Part p) {
        return p.getStockQuantity() != null && p.getMinStockLevel() != null && p.getStockQuantity() < p.getMinStockLevel();
    }

    private String firmName() {
        return supplier.getBusinessName() != null && !supplier.getBusinessName().isBlank()
                ? supplier.getBusinessName() : supplier.getName();
    }

    private void refreshData() {
        header.setTitle(firmName());
        boolean hasFirm = supplier.getBusinessName() != null && !supplier.getBusinessName().isBlank();
        header.setMeta(hasFirm && supplier.getName() != null ? "İlgili: " + supplier.getName() : null,
                supplier.getPhone() != null ? Format.formatPhoneNumber(supplier.getPhone()) : null,
                supplier.getEmail());

        DetailKit.setFact(factPhone, supplier.getPhone() != null ? Format.formatPhoneNumber(supplier.getPhone()) : null);
        DetailKit.setFact(factEmail, supplier.getEmail());
        DetailKit.setFact(factAddress, supplier.getAddress());
        DetailKit.setFact(factTaxNo, supplier.getTaxNumber());
        DetailKit.setFact(factTaxOffice, supplier.getTaxOffice());
        DetailKit.setFact(factCreated, supplier.getCreatedAt() != null ? supplier.getCreatedAt().format(DateFormats.shortDate()) : null);
        boolean hasNote = supplier.getNote() != null && !supplier.getNote().isBlank();
        noteCard.setVisible(hasNote);
        note.setText(hasNote ? supplier.getNote().trim() : "");

        partsSection.showLoading();
        partService.getBySupplierId(supplier.getId()).thenAccept(list -> SwingUtilities.invokeLater(() -> {
            parts = list;
            long critical = list.stream().filter(p -> isCritical(p) || (p.getStockQuantity() != null && p.getStockQuantity() <= 0)).count();
            BigDecimal value = list.stream()
                    .map(p -> (p.getPurchasePrice() != null ? p.getPurchasePrice() : BigDecimal.ZERO)
                            .multiply(BigDecimal.valueOf(Math.max(0, p.getStockQuantity() != null ? p.getStockQuantity() : 0))))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            header.setStat("variety", String.valueOf(list.size()), null);
            header.setStat("value", Format.formatPrice(value), null);
            header.setStat("critical", String.valueOf(critical), critical > 0 ? "Servicio.warningColor" : null);
            views.setCount(VIEW_ALL, (long) list.size());
            views.setCount(VIEW_REORDER, critical);
            applyView();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> partsSection.showError("Parçalar yüklenemedi."));
            return ErrorHandler.handle(this, "Tedarikçi parçaları yüklenemedi", ex);
        });
    }

    private List<Part> reorderParts() {
        return parts.stream().filter(p -> isCritical(p) || (p.getStockQuantity() != null && p.getStockQuantity() <= 0))
                .collect(Collectors.toList());
    }

    private void applyView() {
        partsSection.setData(VIEW_REORDER.equals(views.getSelected()) ? reorderParts() : parts);
    }

    /** Kritik parçaları "ad (SKU) — stok X, en az Y" satırları olarak panoya kopyalar. */
    private void copyReorderList() {
        List<Part> list = reorderParts();
        if (list.isEmpty()) {
            Toasts.show(this, Toast.Type.INFO, Messages.get("toast.supplier.reorderEmpty"));
            return;
        }
        StringBuilder sb = new StringBuilder("Sipariş listesi — ").append(firmName()).append("\n");
        for (Part p : list) {
            int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
            int min = p.getMinStockLevel() != null ? p.getMinStockLevel() : 0;
            int need = Math.max(1, min - stock);
            sb.append("• ").append(p.getName()).append(" (").append(p.getBarcode()).append(") — ")
                    .append(need).append(" adet (stok ").append(stock).append(", en az ").append(min).append(")\n");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString().trim()), null);
        Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.supplier.reorderCopied", list.size()));
    }
}
