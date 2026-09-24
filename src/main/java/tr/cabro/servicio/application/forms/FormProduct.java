package tr.cabro.servicio.application.forms;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.Badge;
import tr.cabro.servicio.application.component.detail.DetailHeader;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.component.detail.DetailListSection;
import tr.cabro.servicio.application.panels.edit.EditModals;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.Sale;
import tr.cabro.servicio.model.SaleItem;
import tr.cabro.servicio.model.contract.Visualizable;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.model.enums.PaymentStatus;
import tr.cabro.servicio.model.enums.SaleType;
import tr.cabro.servicio.service.PaymentService;
import tr.cabro.servicio.service.ProductService;
import tr.cabro.servicio.service.SaleService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Ürün (POS kataloğu) detayı: kimlik şeridinde stok ve fiyatlar; altta ürünün geçtiği satış ve
 * iade fişleri, sağda satış performansı ve açıklama.
 */
public class FormProduct extends Form {

    private Product product;
    private final ProductService productService;
    private final SaleService saleService;

    private DetailHeader header;
    private final Badge categoryBadge = new Badge(simple("Kategorisiz", BadgeColor.GRAY));
    private final Badge criticalBadge = new Badge(simple("Kritik stok", BadgeColor.RED)).setShowIcon(true);
    private DetailListSection<Sale> salesSection;
    private Map<Long, Integer> quantityBySale = Collections.emptyMap();
    private Map<Long, BigDecimal> amountBySale = Collections.emptyMap();

    private JLabel factStock, factMin, factSold, factRevenue, factLast;
    private JPanel descriptionCard;
    private JTextArea description;

    public FormProduct(Product product) {
        this.product = product;
        this.productService = ServiceManager.getProductService();
        this.saleService = ServiceManager.getSaleService();
        init();
    }

    private static Visualizable simple(String name, BadgeColor color) {
        return new Visualizable() {
            @Override public String getDisplayName() { return name; }
            @Override public String getIconPath() { return "icons/triangle-alert.svg"; }
            @Override public BadgeColor getBadgeColor() { return color; }
        };
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 16", "[grow, fill][340!, fill]", "[pref][grow, fill]"));

        header = new DetailHeader();
        header.addBadge(categoryBadge);
        header.addBadge(criticalBadge);
        header.addStat("stock", "Stok");
        header.addStat("buy", "Alış");
        header.addStat("sell", "Satış");
        header.addStat("margin", "Adet başı kâr");
        header.addAction("Düzenle", "icons/pencil.svg", () -> EditModals.editProduct(this, product, this::formRefresh));
        header.setPrimary("Satışa Ekle", "icons/shopping-bag.svg",
                () -> FormManager.showForm(AllForms.getForm(FormPos.class)));
        add(header, "span 2, growx, wmin 0, wrap");

        salesSection = new DetailListSection<>("Satış geçmişi", Arrays.asList(
                new ColumnDef<Sale>("Fiş", Sale.class, s -> s).alignment(SwingConstants.LEADING),
                new ColumnDef<Sale>("Tarih", String.class, s -> s.getSaleDate() != null ? s.getSaleDate().format(DateFormats.dateTime()) : "-")
                        .alignment(SwingConstants.LEADING),
                new ColumnDef<Sale>("Müşteri", String.class, s -> s.getCustomer() != null ? s.getCustomer().getFullName() : "Perakende")
                        .alignment(SwingConstants.LEADING),
                new ColumnDef<Sale>("Adet", Integer.class, s -> quantityBySale.getOrDefault(s.getId(), 0)).alignment(SwingConstants.CENTER),
                new ColumnDef<Sale>("Tutar", BigDecimal.class, s -> amountBySale.getOrDefault(s.getId(), BigDecimal.ZERO))
                        .alignment(SwingConstants.TRAILING),
                ColumnDef.<Sale>badge("Ödeme", PaymentStatus.class, s -> PaymentService.resolveStatus(s.getTotalAmount(), s.getTotalPaid()))
        ), "Henüz satılmadı", "Bu ürün POS ekranından satıldığında fişler burada listelenir.", false);
        JTable t = salesSection.getTable();
        t.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Sale>(
                s -> (s.getType() == SaleType.RETURN ? "İADE-" : "SAT-") + s.getId(),
                s -> s.getType() == SaleType.RETURN ? "SAT-" + s.getParentSaleId() + " iadesi" : "Satış fişi",
                s -> s.getType() == SaleType.RETURN ? UIManager.getColor("Servicio.dangerColor") : null,
                s -> null));
        t.getColumnModel().getColumn(4).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEGATIVE));
        t.getColumnModel().getColumn(3).setMaxWidth(70);
        salesSection.setOnOpen(s -> FormManager.showForm(new FormSale(s)));
        add(salesSection, "grow, wmin 0, hmin 0");

        add(DetailKit.scroll(buildSideColumn()), "grow, hmin 0");
        refreshData();
    }

    private JPanel buildSideColumn() {
        JPanel column = new JPanel(new MigLayout("insets 0, wrap, fillx, gapy 16", "[grow, fill]", ""));
        column.setOpaque(false);

        JPanel perf = DetailKit.card("Stok ve satış", null);
        JPanel facts = DetailKit.facts();
        factStock = DetailKit.fact(facts, "Mevcut stok");
        factMin = DetailKit.fact(facts, "Minimum seviye");
        factSold = DetailKit.fact(facts, "Net satılan");
        factRevenue = DetailKit.fact(facts, "Net ciro");
        factLast = DetailKit.fact(facts, "Son satış");
        perf.add(facts);
        column.add(perf);

        descriptionCard = DetailKit.card("Açıklama", null);
        description = new JTextArea();
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setBorder(null);
        descriptionCard.add(description, "wmin 0");
        column.add(descriptionCard);
        return column;
    }

    @Override
    public void formRefresh() {
        productService.getById(product.getId()).thenAccept(updated ->
                updated.ifPresent(p -> {
                    this.product = p;
                    SwingUtilities.invokeLater(this::refreshData);
                })
        );
    }

    private void refreshData() {
        int stock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        boolean critical = product.getMinStockLevel() != null && stock < product.getMinStockLevel();
        header.setTitle(product.getName());
        String category = product.getCategory() != null ? product.getCategory().getName() : null;
        categoryBadge.setVisualizable(simple(category != null ? category : "Kategorisiz", BadgeColor.GRAY));
        criticalBadge.setVisible(critical || stock <= 0);
        criticalBadge.setVisualizable(simple(stock <= 0 ? "Tükendi" : "Kritik stok", BadgeColor.RED));
        header.setMeta("SKU " + product.getBarcode(),
                product.getBrand() != null && !product.getBrand().isBlank() && !"-".equals(product.getBrand()) ? product.getBrand() : null,
                "POS'ta " + QuickAction.QUICK_SALE.getShortcutText() + " ile satılır");

        header.setStat("stock", stock <= 0 ? "Tükendi" : stock + " adet", critical || stock <= 0 ? "Servicio.dangerColor" : null);
        header.setStat("buy", price(product.getPurchasePrice()), null);
        header.setStat("sell", price(product.getSalePrice()), null);
        BigDecimal margin = product.getSalePrice() != null && product.getPurchasePrice() != null
                ? product.getSalePrice().subtract(product.getPurchasePrice()) : null;
        header.setStat("margin", margin != null ? Format.formatPrice(margin) : "—",
                margin == null ? null : margin.signum() > 0 ? "Servicio.successColor" : margin.signum() < 0 ? "Servicio.dangerColor" : null);

        DetailKit.setFact(factStock, stock + " adet");
        DetailKit.setFact(factMin, product.getMinStockLevel() != null ? product.getMinStockLevel() + " adet" : null);

        boolean hasDesc = product.getDescription() != null && !product.getDescription().isBlank();
        descriptionCard.setVisible(hasDesc);
        description.setText(hasDesc ? product.getDescription().trim() : "");

        salesSection.showLoading();
        saleService.getProductSales(product.getId()).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            Map<Long, Integer> qty = new HashMap<>();
            Map<Long, BigDecimal> amount = new HashMap<>();
            int netQty = 0;
            BigDecimal net = BigDecimal.ZERO;
            for (SaleItem item : result.items) {
                qty.merge(item.getSaleId(), Math.abs(item.getQuantity()), Integer::sum);
                BigDecimal line = item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO;
                amount.merge(item.getSaleId(), line, BigDecimal::add);
                netQty += item.getQuantity();
                net = net.add(line);
            }
            quantityBySale = qty;
            amountBySale = amount;
            salesSection.setData(result.sales);
            DetailKit.setFact(factSold, netQty + " adet, " + result.sales.size() + " fiş");
            DetailKit.setFact(factRevenue, Format.formatPrice(net));
            DetailKit.setFact(factLast, result.sales.isEmpty() || result.sales.get(0).getSaleDate() == null ? null
                    : result.sales.get(0).getSaleDate().format(DateFormats.dateTime()));
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> salesSection.showError("Satış geçmişi yüklenemedi."));
            return ErrorHandler.handle(this, "Ürün satış geçmişi yüklenemedi", ex);
        });
    }

    private static String price(BigDecimal v) {
        return v != null ? Format.formatPrice(v) : "—";
    }
}
