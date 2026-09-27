package tr.cabro.servicio.application.forms;

import tr.cabro.servicio.application.utils.Toasts;
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
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.model.enums.SupplierRole;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ProductService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Tedarikçi / toptancı detayı: kimlik şeridinde kalem çeşidi, stoktaki mal değeri ve sipariş
 * gereken kalem sayısı; altta firmadan alınan parçalar (tedarikçi) ve ürünler (toptancı) tek
 * listede, sekmelerle; sağda iletişim ve fatura bilgileri. Birincil işlem kritik kalemlerin
 * sipariş listesini panoya kopyalar (WhatsApp/e-postaya yapıştırılır).
 */
public class FormSupplier extends Form {

    private static final String VIEW_ALL = "all";
    private static final String VIEW_PARTS = "parts";
    private static final String VIEW_PRODUCTS = "products";
    private static final String VIEW_REORDER = "reorder";

    /** Liste satırı: parça ya da ürün, aynı kolonlarla. */
    private record Item(boolean product, Object source, String name, String category, String barcode,
                        int stock, Integer min, BigDecimal purchase, BigDecimal sale) {
        static Item of(Part p) {
            return new Item(false, p, p.getName(), p.getCategory() != null ? p.getCategory().getName() : null,
                    p.getBarcode(), p.getStockQuantity() != null ? p.getStockQuantity() : 0, p.getMinStockLevel(),
                    p.getPurchasePrice(), p.getSalePrice());
        }

        static Item of(Product p) {
            return new Item(true, p, p.getName(), p.getCategory() != null ? p.getCategory().getName() : null,
                    p.getBarcode(), p.getStockQuantity() != null ? p.getStockQuantity() : 0, p.getMinStockLevel(),
                    p.getPurchasePrice(), p.getSalePrice());
        }

        boolean critical() {
            return stock <= 0 || (min != null && stock < min);
        }
    }

    private Supplier supplier;
    private final SupplierService supplierService;
    private final PartService partService;
    private final ProductService productService;

    private DetailHeader header;
    private DetailListSection<Item> itemsSection;
    private ViewTabs views;
    private List<Item> items = Collections.emptyList();

    private JLabel factPhone, factEmail, factAddress, factTaxNo, factTaxOffice, factCreated;
    private JPanel noteCard;
    private JTextArea note;

    public FormSupplier(Supplier supplier) {
        this.supplier = supplier;
        this.supplierService = ServiceManager.getSupplierService();
        this.partService = ServiceManager.getPartService();
        this.productService = ServiceManager.getProductService();
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 16", "[grow, fill][340!, fill]", "[pref][grow, fill]"));

        header = new DetailHeader();
        header.addStat("variety", "Kalem çeşidi");
        header.addStat("value", "Stoktaki mal değeri");
        header.addStat("critical", "Sipariş gereken");
        header.addAction("Düzenle", "icons/pencil.svg", () -> EditModals.editSupplier(this, supplier, this::formRefresh));
        header.setPrimary("Sipariş Listesi", "icons/clipboard-list.svg", this::copyReorderList);
        add(header, "span 2, growx, wmin 0, wrap");

        itemsSection = new DetailListSection<>("Bu firmadan alınanlar", Arrays.asList(
                new ColumnDef<Item>("Kalem", Item.class, i -> i).alignment(SwingConstants.LEADING),
                new ColumnDef<Item>("SKU", String.class, Item::barcode).alignment(SwingConstants.LEADING),
                new ColumnDef<Item>("Stok", Item.class, i -> i).alignment(SwingConstants.CENTER),
                new ColumnDef<Item>("Alış", BigDecimal.class, Item::purchase).alignment(SwingConstants.TRAILING),
                new ColumnDef<Item>("Satış", BigDecimal.class, Item::sale).alignment(SwingConstants.TRAILING)
        ), "Bu firmaya bağlı kalem yok",
                "Parça formunda tedarikçi ya da ürün formunda toptancı olarak seçildiğinde burada görünür.", false);
        views = new ViewTabs();
        views.addView(VIEW_ALL, "Tümü");
        views.addView(VIEW_PARTS, "Parçalar");
        views.addView(VIEW_PRODUCTS, "Ürünler");
        views.addView(VIEW_REORDER, "Sipariş gerekli");
        views.setOnChange(k -> applyView());
        itemsSection.setTabs(views);

        JTable t = itemsSection.getTable();
        t.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Item>(
                Item::name,
                i -> (i.product() ? "Ürün" : "Parça") + "  ·  " + (i.category() != null ? i.category() : "Kategorisiz")));
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
                Item i = (Item) value;
                boolean belowMin = i.min() != null && i.stock() < i.min();
                setHorizontalAlignment(CENTER);
                setFont(table.getFont().deriveFont(Font.BOLD));
                setText(i.stock() <= 0 ? "Tükendi" : belowMin ? i.stock() + "  ·  min " + i.min() : String.valueOf(i.stock()));
                setForeground(i.critical() ? SemanticColor.danger() : table.getForeground());
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
        itemsSection.setOnOpen(i -> FormManager.showForm(i.product()
                ? new FormProduct((Product) i.source()) : new FormPart((Part) i.source())));
        add(itemsSection, "grow, wmin 0, hmin 0");

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

    private String firmName() {
        return supplier.getBusinessName() != null && !supplier.getBusinessName().isBlank()
                ? supplier.getBusinessName() : supplier.getName();
    }

    private void refreshData() {
        header.setTitle(firmName());
        boolean hasFirm = supplier.getBusinessName() != null && !supplier.getBusinessName().isBlank();
        SupplierRole role = supplier.getRole() != null ? supplier.getRole() : SupplierRole.SUPPLIER;
        header.setMeta(role.getLabel(),
                hasFirm && supplier.getName() != null ? "İlgili: " + supplier.getName() : null,
                supplier.getPhone() != null ? Format.formatPhoneNumber(supplier.getPhone()) : null);

        DetailKit.setFact(factPhone, supplier.getPhone() != null ? Format.formatPhoneNumber(supplier.getPhone()) : null);
        DetailKit.setFact(factEmail, supplier.getEmail());
        DetailKit.setFact(factAddress, supplier.getAddress());
        DetailKit.setFact(factTaxNo, supplier.getTaxNumber());
        DetailKit.setFact(factTaxOffice, supplier.getTaxOffice());
        DetailKit.setFact(factCreated, supplier.getCreatedAt() != null ? supplier.getCreatedAt().format(DateFormats.shortDate()) : null);
        boolean hasNote = supplier.getNote() != null && !supplier.getNote().isBlank();
        noteCard.setVisible(hasNote);
        note.setText(hasNote ? supplier.getNote().trim() : "");

        itemsSection.showLoading();
        CompletableFuture<List<Part>> parts = partService.getBySupplierId(supplier.getId());
        CompletableFuture<List<Product>> products = productService.getBySupplierId(supplier.getId());
        parts.thenCombine(products, (pl, rl) -> {
            List<Item> all = new ArrayList<>();
            pl.forEach(p -> all.add(Item.of(p)));
            rl.forEach(p -> all.add(Item.of(p)));
            all.sort(Comparator.comparing(i -> i.name() != null ? i.name().toLowerCase() : ""));
            return all;
        }).thenAccept(list -> SwingUtilities.invokeLater(() -> {
            items = list;
            long critical = list.stream().filter(Item::critical).count();
            BigDecimal value = list.stream()
                    .map(i -> (i.purchase() != null ? i.purchase() : BigDecimal.ZERO).multiply(BigDecimal.valueOf(Math.max(0, i.stock()))))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            header.setStat("variety", String.valueOf(list.size()), null);
            header.setStat("value", Format.formatPrice(value), null);
            header.setStat("critical", String.valueOf(critical), critical > 0 ? "Servicio.warningColor" : null);
            views.setCount(VIEW_ALL, (long) list.size());
            views.setCount(VIEW_PARTS, list.stream().filter(i -> !i.product()).count());
            views.setCount(VIEW_PRODUCTS, list.stream().filter(Item::product).count());
            views.setCount(VIEW_REORDER, critical);
            applyView();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> itemsSection.showError("Kalemler yüklenemedi."));
            return ErrorHandler.handle(this, "Firma kalemleri yüklenemedi", ex);
        });
    }

    private List<Item> reorderItems() {
        return items.stream().filter(Item::critical).collect(Collectors.toList());
    }

    private void applyView() {
        String view = views.getSelected();
        List<Item> shown;
        if (VIEW_REORDER.equals(view)) shown = reorderItems();
        else if (VIEW_PARTS.equals(view)) shown = items.stream().filter(i -> !i.product()).toList();
        else if (VIEW_PRODUCTS.equals(view)) shown = items.stream().filter(Item::product).toList();
        else shown = items;
        itemsSection.setData(shown);
    }

    /** Kritik kalemleri "ad (SKU) — N adet (stok X, en az Y)" satırları olarak panoya kopyalar. */
    private void copyReorderList() {
        List<Item> list = reorderItems();
        if (list.isEmpty()) {
            Toasts.show(this, Toast.Type.INFO, Messages.get("toast.supplier.reorderEmpty"));
            return;
        }
        StringBuilder sb = new StringBuilder("Sipariş listesi — ").append(firmName()).append("\n");
        for (Item i : list) {
            int min = i.min() != null ? i.min() : 0;
            int need = Math.max(1, min - i.stock());
            sb.append("• ").append(i.name()).append(" (").append(i.barcode()).append(") — ")
                    .append(need).append(" adet (stok ").append(i.stock()).append(", en az ").append(min).append(")\n");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString().trim()), null);
        Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.supplier.reorderCopied", list.size()));
    }
}
