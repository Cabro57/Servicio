package tr.cabro.servicio.application.forms;

import tr.cabro.servicio.model.enums.CategoryScope;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.application.component.table.Lookups;
import tr.cabro.servicio.application.renderer.AmountChipCellRenderer;
import tr.cabro.servicio.application.renderer.ChipCellRenderer;
import java.util.ArrayList;
import tr.cabro.servicio.application.renderer.RowParts;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.application.renderer.StatusDotCellRenderer;
import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.model.dto.PageResult;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.table.AppPagination;
import raven.swingpack.JPagination;
import tr.cabro.servicio.application.component.table.TableActionColumnSupport;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.edit.ProductEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.service.ProductService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * POS satış (ürün) kataloğu listesi — bağımsız {@code products} tablosu üzerinde çalışır.
 * Bkz. {@link FormParts} (servis parçası muadili, ayrı tablo/model — V21 migration).
 */
@SystemForm(name = "Ürünler", description = "Satış (POS) ekranında listelenecek ürünleri eklemek ve düzenlemek için kullanılabilir")
public class FormProducts extends AbstractTableForm {

    private final ProductService productService;
    private GenericTableModel<Product> tableModel;

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = AppSettings.get().getTables().getPartPageSize();
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private PaginationBar paginationBar;

    public FormProducts() {
        this.productService = ServiceManager.getProductService();
    }

    @Override
    protected String getNewButtonText() {
        return "Yeni Ürün";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/package-plus.svg";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Ürün adı, SKU, marka veya kategori ara…";
    }

    // --- Görünüm sekmeleri: stok durumu ---

    private static final String VIEW_ALL = "all";
    private static final String VIEW_CRITICAL = "critical";
    private static final String VIEW_OUT = "out";

    @Override
    protected void initViews() {
        addView(VIEW_ALL, "Tümü");
        addView(VIEW_CRITICAL, "Kritik stok");
        addView(VIEW_OUT, "Tükendi");
    }

    /** "Kritik" ölçütü getStats() ile aynı: stok &lt; minimum (0 dahil). */
    @Override
    protected Map<String, ColumnFilterValue> viewFilters(String key) {
        if (VIEW_CRITICAL.equals(key)) return Map.of("view:critical",
                ColumnFilterValue.condition("p.stock_quantity < p.min_stock_level"));
        if (VIEW_OUT.equals(key)) return Map.of("view:out", ColumnFilterValue.condition("p.stock_quantity <= 0"));
        return Collections.emptyMap();
    }

    @Override
    protected CompletableFuture<Long> countMatching(Map<String, ColumnFilterValue> filters) {
        return productService.searchFilteredPaged(currentSearchTerm, filters, 1, 1).thenApply(PageResult::getTotalItems);
    }

    @Override
    protected void onViewChanged(String key) {
        currentPage = 1;
        super.onViewChanged(key);
    }

    @Override
    protected void refreshStats() {
        productService.getStats().thenAccept(stats -> SwingUtilities.invokeLater(() -> {
            long critical = stats.getCriticalStockCount();
            summary.set(
                    ListSummary.Part.strong(stats.getPartVarietyCount() + " ürün çeşidi"),
                    ListSummary.Part.of(stats.getTotalStock() + " adet stokta"),
                    critical > 0 ? ListSummary.Part.meaning(critical + " kritik stokta", "Servicio.dangerColor") : null,
                    ListSummary.Part.of("envanter değeri " + Format.formatPrice(stats.getTotalInventoryValue())));
        })).exceptionally(ex -> ErrorHandler.handle(this, "Ürün özeti yüklenemedi", ex));
    }

    @Override
    protected JComponent createPaginationComponent() {
        paginationBar = new PaginationBar(5, PAGE_SIZE_OPTIONS, pageSize,
                page -> { currentPage = page; refreshTable(); },
                newSize -> {
                    pageSize = newSize;
                    currentPage = 1;
                    AppSettings.get().getTables().setPartPageSize(pageSize);
                    AppSettings.save();
                    refreshTable();
                });
        return paginationBar;
    }

    @Override
    protected void applyFilter() {
        currentSearchTerm = searchField.getText().trim();
        currentPage = 1;
        refreshTable();
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Product>> columns = Arrays.asList(
                new ColumnDef<Product>("Ürün", Product.class, p -> p).alignment(SwingConstants.LEADING)
                        .lookupFilter("p.category_id", Lookups.categories(CategoryScope.PRODUCT)),
                new ColumnDef<Product>("SKU", String.class, Product::getBarcode).alignment(SwingConstants.LEADING),
                new ColumnDef<Product>("Marka", String.class, p -> p.getBrand() != null && !p.getBrand().isBlank() ? p.getBrand() : "—").alignment(SwingConstants.LEADING),
                new ColumnDef<Product>("Stok", Product.class, p -> p).alignment(SwingConstants.CENTER),
                new ColumnDef<Product>("Satış Fiyatı", BigDecimal.class, Product::getSalePrice).alignment(SwingConstants.TRAILING),
                ColumnDef.<Product>actionColumn("")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        installHeaderFilters(columns);

        table.getColumnModel().getColumn(0).setPreferredWidth(320);
        table.getColumnModel().getColumn(0).setMinWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(130);

        addSort("NEWEST", "En yeni");
        addSort("NAME", "Ad (A-Z)");
        addSort("STOCK", "Stok (azdan çoğa)");
        addSort("PRICE", "Satış fiyatı");
    }

    /**
     * "Kritik stok" sekmesi/özetiyle aynı ölçüt: {@code stock_quantity < min_stock_level}.
     * Ölçüt ayrışırsa özet bir sayı, tablo başka bir sayı gösterir.
     */
    private static BadgeColor stockColor(Product p) {
        int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
        return stock <= 0 ? BadgeColor.RED : isCriticalStock(p) ? BadgeColor.YELLOW : BadgeColor.GREEN;
    }

    private static boolean isCriticalStock(Product item) {
        return item != null && item.getStockQuantity() != null && item.getMinStockLevel() != null
                && item.getStockQuantity() < item.getMinStockLevel();
    }

    private void configureTableColumns() {
        // Ürün: stok durumunun rengiyle nokta; altında kategori.
        table.getColumnModel().getColumn(0).setCellRenderer(new StatusDotCellRenderer<Product>(
                Product::getName,
                p -> {
                    List<String> bits = new ArrayList<>();
                    bits.add(p.getCategory() != null ? p.getCategory().getName() : "Kategorisiz");
                    
                    return String.join("  ·  ", bits);
                },
                p -> stockColor(p)));
        table.getColumnModel().getColumn(1).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground; font: -1", 8));
        table.getColumnModel().getColumn(2).setCellRenderer(StyledLabelCellRenderer.of(SwingConstants.LEADING, null, 8));
        // Stok: çip metni durumu da yazar (Tükendi / Kritik · N / N adet), yalnızca renge bırakılmaz.
        table.getColumnModel().getColumn(3).setCellRenderer(new ChipCellRenderer<Product>(p -> {
            int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
            if (stock <= 0) return ChipCellRenderer.badge("Tükendi", BadgeColor.RED);
            if (isCriticalStock(p)) return ChipCellRenderer.badge("Kritik · " + stock, BadgeColor.YELLOW);
            return ChipCellRenderer.badge(stock + " adet", BadgeColor.GREEN);
        }));
        table.getColumnModel().getColumn(4).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));

        TableActionColumnSupport.install(table, 5, tableModel, new TableActionColumnSupport.Handlers<Product>() {
            @Override
            public void onEdit(Product item) {
                openEditModal(item);
            }

            @Override
            public void onDelete(Product selected) {
                ModalDialog.showModal(FormProducts.this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                        Messages.get("confirm.delete.part"), Messages.get("confirm.delete.title"),
                        SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {

                    if (action == SimpleModalBorder.YES_OPTION) {
                        productService.delete(selected.getId()).thenAccept(v -> {
                            SwingUtilities.invokeLater(() -> {
                                Toasts.show(FormProducts.this, Toast.Type.SUCCESS, Messages.get("toast.part.deleted"));
                                refreshTable();
                            });
                        }).exceptionally(ex -> ErrorHandler.handle(FormProducts.this, "Ürün silinemedi", ex));
                    }
                }));
            }

            @Override
            public void onView(Product item) {
                if (item != null) FormManager.showForm(new FormProduct(item));
            }
        });

    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz ürün yok"; }

    @Override
    protected String getEmptyStateDescription() { return "Satış ekranında (POS) satacağınız ürünleri eklediğinizde burada görünür."; }

    @Override
    protected void onSortChanged(String key) {
        currentPage = 1;
        super.onSortChanged(key);
    }

    @Override
    protected void loadTableData() {
        productService.searchFilteredPaged(currentSearchTerm, effectiveFilters(), currentPage, pageSize, getSortKey()).thenAccept(result ->
                SwingUtilities.invokeLater(() -> {
                    tableModel.setData(result.getItems());
                    if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                    setResultCount(result.getTotalItems());
                    refreshLayout();
                })).exceptionally(ex -> ErrorHandler.handle(this, "Ürün tablosu yenilenemedi", ex));
    }

    @Override
    protected void onNew() {
        final String id = "ProductNew";
        ProductEditPanel panel = new ProductEditPanel(new Product());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Yeni Ürün Ekle", options,
            (controller, action) -> {
                if (action == SimpleModalBorder.OK_OPTION) {
                    Product updated = panel.getData();
                    if (updated == null) {
                        controller.consume();
                        return;
                    }

                    updated.setCreatedAt(LocalDateTime.now());
                    productService.save(updated, false).thenAccept(product -> {
                        SwingUtilities.invokeLater(() -> {
                            Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.added", updated.getName()));
                            refreshTable();
                        });
                    }).exceptionally(ex -> {
                        SwingUtilities.invokeLater(controller::consume);
                        return ErrorHandler.handle(this, "Ürün eklenemedi", ex);
                    });
                }
            })
        , id);
    }

    private void openEditModal(Product product) {
        final String id = "ProductEdit";
        ProductEditPanel panel = new ProductEditPanel(product);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Ürün Düzenle", options,
            (controller, action) -> {
                 if (action == SimpleModalBorder.OK_OPTION) {
                    Product updated = panel.getData();
                    if (updated == null) {
                        controller.consume();
                        return;
                    }

                    productService.save(updated, true).thenAccept(upgrade -> {
                        SwingUtilities.invokeLater(() -> {
                            Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", updated.getName()));
                            refreshTable();
                        });
                    }).exceptionally(ex -> {
                        SwingUtilities.invokeLater(controller::consume);
                        return ErrorHandler.handle(this, "Ürün güncellenemedi", ex);
                    });
                }
            })
        , id);
    }
}
