package tr.cabro.servicio.application.forms;

import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.application.component.table.Lookups;
import tr.cabro.servicio.application.renderer.AmountChipCellRenderer;
import tr.cabro.servicio.application.renderer.ChipCellRenderer;
import tr.cabro.servicio.application.renderer.RowParts;
import java.util.ArrayList;
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
import raven.modal.ModalDialog;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.application.component.table.TableActionColumnSupport;
import tr.cabro.servicio.application.panels.edit.PartEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.application.component.table.AppPagination;
import raven.swingpack.JPagination;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Servis parçaları listesi — bkz. {@link FormProducts} (POS satış kataloğu, bağımsız tablo/model). */
@SystemForm(name = "Parçalar", description = "Yeni parçalar eklemek ve düzenlemek için kullanılabilir")
public class FormParts extends AbstractTableForm {

    private final PartService partService;
    private GenericTableModel<Part> tableModel;

    // --- SAYFALAMA (DB-tabanlı) ---
    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = AppSettings.get().getTables().getPartPageSize();
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private PaginationBar paginationBar;

    public FormParts() {
        this.partService = ServiceManager.getPartService();
    }

    @Override
    protected String getNewButtonText() {
        return "Yeni Parça";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/package-plus.svg";
    }

    @Override
    protected QuickAction getNewQuickAction() {
        return QuickAction.NEW_PART;
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Parça adı, SKU, model, kategori veya tedarikçi ara…";
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
        return partService.searchFilteredPaged(currentSearchTerm, filters, 1, 1).thenApply(PageResult::getTotalItems);
    }

    @Override
    protected void onViewChanged(String key) {
        currentPage = 1;
        super.onViewChanged(key);
    }

    @Override
    protected void refreshStats() {
        partService.getStats().thenAccept(stats -> SwingUtilities.invokeLater(() -> {
            long critical = stats.getCriticalStockCount();
            summary.set(
                    ListSummary.Part.strong(stats.getPartVarietyCount() + " parça çeşidi"),
                    ListSummary.Part.of(stats.getTotalStock() + " adet stokta"),
                    critical > 0 ? ListSummary.Part.meaning(critical + " kritik stokta", "Servicio.dangerColor") : null,
                    ListSummary.Part.of("envanter değeri " + Format.formatPrice(stats.getTotalInventoryValue())));
        })).exceptionally(ex -> ErrorHandler.handle(this, "Parça özeti yüklenemedi", ex));
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
        List<ColumnDef<Part>> columns = Arrays.asList(
                new ColumnDef<Part>("Parça", Part.class, p -> p).alignment(SwingConstants.LEADING)
                        .lookupFilter("p.category_id", Lookups.partCategories()),
                new ColumnDef<Part>("SKU", String.class, Part::getBarcode).alignment(SwingConstants.LEADING),
                new ColumnDef<Part>("Tedarikçi", String.class, p -> p.getSupplier() != null ? (p.getSupplier().getBusinessName() != null && !p.getSupplier().getBusinessName().isBlank() ? p.getSupplier().getBusinessName() : p.getSupplier().getName()) : "—")
                        .alignment(SwingConstants.LEADING).lookupFilter("p.supplier_id", Lookups.suppliers()),
                new ColumnDef<Part>("Stok", Part.class, p -> p).alignment(SwingConstants.CENTER),
                new ColumnDef<Part>("Satış Fiyatı", BigDecimal.class, Part::getSalePrice).alignment(SwingConstants.TRAILING),
                ColumnDef.<Part>actionColumn("")
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
    private static BadgeColor stockColor(Part p) {
        int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
        return stock <= 0 ? BadgeColor.RED : isCriticalStock(p) ? BadgeColor.YELLOW : BadgeColor.GREEN;
    }

    private static boolean isCriticalStock(Part item) {
        return item != null && item.getStockQuantity() != null && item.getMinStockLevel() != null
                && item.getStockQuantity() < item.getMinStockLevel();
    }

    private void configureTableColumns() {
        // Parça: stok durumunun rengiyle nokta; altında kategori.
        table.getColumnModel().getColumn(0).setCellRenderer(new StatusDotCellRenderer<Part>(
                Part::getName,
                p -> {
                    List<String> bits = new ArrayList<>();
                    bits.add(p.getCategory() != null ? p.getCategory().getName() : "Kategorisiz");
                    if (p.getModelCompatibility() != null && !p.getModelCompatibility().isBlank()) bits.add(p.getModelCompatibility());
                    return String.join("  ·  ", bits);
                },
                p -> stockColor(p)));
        table.getColumnModel().getColumn(1).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground; font: -1", 8));
        table.getColumnModel().getColumn(2).setCellRenderer(new TooltipCellRenderer());
        // Stok: çip metni durumu da yazar (Tükendi / Kritik · N / N adet), yalnızca renge bırakılmaz.
        table.getColumnModel().getColumn(3).setCellRenderer(new ChipCellRenderer<Part>(p -> {
            int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
            if (stock <= 0) return ChipCellRenderer.badge("Tükendi", BadgeColor.RED);
            if (isCriticalStock(p)) return ChipCellRenderer.badge("Kritik · " + stock, BadgeColor.YELLOW);
            return ChipCellRenderer.badge(stock + " adet", BadgeColor.GREEN);
        }));
        table.getColumnModel().getColumn(4).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));

        TableActionColumnSupport.install(table, 5, tableModel, new TableActionColumnSupport.Handlers<Part>() {
            @Override
            public void onEdit(Part item) {
                openEditModal(item);
            }

            @Override
            public void onDelete(Part selected) {
                ModalDialog.showModal(FormParts.this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                        Messages.get("confirm.delete.part"), Messages.get("confirm.delete.title"),
                        SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {

                    if (action == SimpleModalBorder.YES_OPTION) {
                        partService.delete(selected.getId()).thenAccept(v -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(FormParts.this, Toast.Type.SUCCESS, Messages.get("toast.part.deleted"));
                                refreshTable();
                            });
                        }).exceptionally(ex -> ErrorHandler.handle(FormParts.this, "Parça silinemedi", ex));
                    }
                }));
            }

            @Override
            public void onView(Part item) {
                if (item != null) FormManager.showForm(new FormPart(item));
            }
        });

    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz parça yok"; }

    @Override
    protected String getEmptyStateDescription() { return "Stokunuzdaki yedek parçaları eklediğinizde burada görünür."; }

    @Override
    protected void onSortChanged(String key) {
        currentPage = 1;
        super.onSortChanged(key);
    }

    @Override
    protected void loadTableData() {
        partService.searchFilteredPaged(currentSearchTerm, effectiveFilters(), currentPage, pageSize, getSortKey()).thenAccept(result ->
                SwingUtilities.invokeLater(() -> {
                    tableModel.setData(result.getItems());
                    if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                    setResultCount(result.getTotalItems());
                    refreshLayout();
                })).exceptionally(ex -> ErrorHandler.handle(this, "Parça tablosu yenilenemedi", ex));
    }

    @Override
    protected void onNew() {
        final String id = "PartNew";
        PartEditPanel panel = new PartEditPanel(new Part());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Yeni Parça Ekle", options,
            (controller, action) -> {
                if (action == SimpleModalBorder.OK_OPTION) {
                    Part updated = panel.getData();
                    if (updated == null) {
                        controller.consume();
                        return;
                    }

                    updated.setCreatedAt(LocalDateTime.now());
                    partService.save(updated, false).thenAccept(part -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.added", updated.getName()));
                            refreshTable();
                        });
                    }).exceptionally(ex -> {
                        SwingUtilities.invokeLater(controller::consume);
                        return ErrorHandler.handle(this, "Parça eklenemedi", ex);
                    });
                }
            })
        , id);
    }

    private void openEditModal(Part part) {
        final String id = "PartEdit";
        PartEditPanel panel = new PartEditPanel(part);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Parça Düzenle", options,
            (controller, action) -> {
                 if (action == SimpleModalBorder.OK_OPTION) {
                    Part updated = panel.getData();
                    if (updated == null) {
                        controller.consume();
                        return;
                    }

                    partService.save(updated, true).thenAccept(upgrade -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", updated.getName()));
                            refreshTable();
                        });
                    }).exceptionally(ex -> {
                        SwingUtilities.invokeLater(controller::consume);
                        return ErrorHandler.handle(this, "Parça güncellenemedi", ex);
                    });
                }
            })
        , id);
    }
}
