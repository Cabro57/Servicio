package tr.cabro.servicio.application.forms;

import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Sale;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.PaymentStatus;
import tr.cabro.servicio.model.enums.SaleType;
import tr.cabro.servicio.service.PaymentService;
import tr.cabro.servicio.service.SaleService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Satış geçmişi — tek atomik yazma olan {@link SaleService#checkout} dışında bu ekrandan kayıt oluşturulmaz. */
@SystemForm(name = "Satışlar", description = "Tamamlanmış satışların ve iadelerin listesi")
public class FormSales extends AbstractTableForm {

    private final SaleService saleService;
    private GenericTableModel<Sale> tableModel;

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = 25;
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private PaginationBar paginationBar;

    private static final String VIEW_ALL = "all";
    private static final String VIEW_TODAY = "today";
    private static final String VIEW_OPEN = "open";
    private static final String VIEW_RETURNS = "returns";

    /** Tahsil edilmemiş kısmı kalan satış fişleri (veresiye); tahsis toplamı fiş tutarının altında. */
    private static final String OPEN_CONDITION = "s.type = 'SALE' AND s.total_amount - COALESCE((SELECT SUM(pa.amount) "
            + "FROM payment_allocations pa WHERE pa.target_type = 'SALE' AND pa.target_id = s.id), 0) > 0.009";

    public FormSales() {
        this.saleService = ServiceManager.getSaleService();
    }

    @Override
    protected String getNewButtonText() {
        return "Yeni Satış";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/shopping-bag.svg";
    }

    @Override
    protected QuickAction getNewQuickAction() {
        return QuickAction.QUICK_SALE;
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Fiş no, müşteri adı veya telefon ara…";
    }

    @Override
    protected JComponent createPaginationComponent() {
        paginationBar = new PaginationBar(5, PAGE_SIZE_OPTIONS, pageSize,
                page -> { currentPage = page; refreshTable(); },
                newSize -> {
                    pageSize = newSize;
                    currentPage = 1;
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

    // --- Görünüm sekmeleri ---

    @Override
    protected void initViews() {
        addView(VIEW_ALL, "Tümü");
        addView(VIEW_TODAY, "Bugün");
        addView(VIEW_OPEN, "Açık hesap");
        addView(VIEW_RETURNS, "İadeler");
    }

    @Override
    protected Map<String, ColumnFilterValue> viewFilters(String key) {
        if (VIEW_TODAY.equals(key)) return Map.of("s.sale_date", day(LocalDate.now()));
        if (VIEW_OPEN.equals(key)) return Map.of("view:open", ColumnFilterValue.condition(OPEN_CONDITION));
        if (VIEW_RETURNS.equals(key)) return Map.of("view:returns", ColumnFilterValue.condition("s.type = 'RETURN'"));
        return Collections.emptyMap();
    }

    private static ColumnFilterValue day(LocalDate date) {
        ColumnFilterValue v = new ColumnFilterValue();
        v.setDateFrom(date);
        v.setDateTo(date);
        return v;
    }

    @Override
    protected CompletableFuture<Long> countMatching(Map<String, ColumnFilterValue> filters) {
        return saleService.searchFilteredPaged(currentSearchTerm, filters, 1, 1).thenApply(PageResult::getTotalItems);
    }

    @Override
    protected void onViewChanged(String key) {
        currentPage = 1;
        super.onViewChanged(key);
    }

    // --- Özet: bugün, bu ay, açık hesap ---

    @Override
    protected void refreshStats() {
        LocalDate today = LocalDate.now();
        ColumnFilterValue month = new ColumnFilterValue();
        month.setDateFrom(today.withDayOfMonth(1));
        month.setDateTo(today);
        Map<String, ColumnFilterValue> todayF = viewFilters(VIEW_TODAY);
        Map<String, ColumnFilterValue> monthF = Map.of("s.sale_date", month);
        Map<String, ColumnFilterValue> openF = viewFilters(VIEW_OPEN);

        CompletableFuture<Long> todayCount = saleService.searchFilteredPaged(null, todayF, 1, 1).thenApply(PageResult::getTotalItems);
        CompletableFuture<BigDecimal> todaySum = saleService.sumFiltered(todayF);
        CompletableFuture<BigDecimal> monthSum = saleService.sumFiltered(monthF);
        CompletableFuture<Long> openCount = saleService.searchFilteredPaged(null, openF, 1, 1).thenApply(PageResult::getTotalItems);

        CompletableFuture.allOf(todayCount, todaySum, monthSum, openCount).thenRun(() -> SwingUtilities.invokeLater(() -> {
            long t = todayCount.join();
            long open = openCount.join();
            summary.set(
                    t > 0 ? ListSummary.Part.strong("Bugün " + t + " fiş, " + Format.formatPrice(todaySum.join()))
                            : ListSummary.Part.of("Bugün henüz satış yok"),
                    ListSummary.Part.of("bu ay " + Format.formatPrice(monthSum.join())),
                    open > 0 ? ListSummary.Part.meaning(open + " açık hesap", "Servicio.warningColor") : null);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Satış özeti yüklenemedi", ex));
    }

    // --- Tablo ---

    @Override
    protected void setupTable() {
        List<ColumnDef<Sale>> columns = Arrays.asList(
                new ColumnDef<Sale>("Fiş", Sale.class, s -> s).alignment(SwingConstants.LEADING),
                new ColumnDef<Sale>("Tarih", String.class,
                        s -> s.getSaleDate() != null ? s.getSaleDate().format(DateFormats.dateTime()) : "-").alignment(SwingConstants.LEADING),
                new ColumnDef<Sale>("Müşteri", Sale.class, s -> s).alignment(SwingConstants.LEADING),
                new ColumnDef<Sale>("Toplam", BigDecimal.class, Sale::getTotalAmount).alignment(SwingConstants.TRAILING),
                new ColumnDef<Sale>("Kalan", BigDecimal.class, FormSales::remaining).alignment(SwingConstants.TRAILING),
                ColumnDef.<Sale>badge("Ödeme", PaymentStatus.class,
                        s -> PaymentService.resolveStatus(s.getTotalAmount(), s.getTotalPaid()))
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        tr.cabro.servicio.application.component.table.TableColumnConfigurator.applyColumnRenderers(table, columns);

        table.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Sale>(
                s -> (s.getType() == SaleType.RETURN ? "İADE-" : "SAT-") + s.getId(),
                s -> s.getType() == SaleType.RETURN ? "SAT-" + s.getParentSaleId() + " iadesi" : "Satış fişi",
                s -> s.getType() == SaleType.RETURN ? UIManager.getColor("Servicio.dangerColor") : null,
                s -> null));
        table.getColumnModel().getColumn(1).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground", 8));
        table.getColumnModel().getColumn(2).setCellRenderer(new MultiLineTableCellRenderer<Sale>(
                s -> s.getCustomer() != null ? s.getCustomer().getFullName() : "Perakende",
                s -> s.getCustomer() != null ? PhoneHelper.formatForDisplay(s.getCustomer().getPhoneNumber1()) : "Kayıtsız müşteri"));
        table.getColumnModel().getColumn(3).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEGATIVE));
        table.getColumnModel().getColumn(4).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.OWED));

        openRowsWith(tableModel, sale -> FormManager.showForm(new FormSale(sale)), -1);

        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(140);
    }

    /** Satış fişinde tahsil edilmemiş tutar; iadelerde kalan kavramı yok. */
    private static BigDecimal remaining(Sale s) {
        if (s.getType() == SaleType.RETURN || s.getTotalAmount() == null) return BigDecimal.ZERO;
        BigDecimal paid = s.getTotalPaid() != null ? s.getTotalPaid() : BigDecimal.ZERO;
        return s.getTotalAmount().subtract(paid).max(BigDecimal.ZERO);
    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz satış yok"; }

    @Override
    protected String getEmptyStateDescription() { return "POS ekranından tamamlanan satışlar burada listelenir."; }

    @Override
    protected void loadTableData() {
        saleService.searchFilteredPaged(currentSearchTerm, effectiveFilters(), currentPage, pageSize).thenAccept(result ->
                SwingUtilities.invokeLater(() -> {
                    tableModel.setData(result.getItems());
                    if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                    setResultCount(result.getTotalItems());
                    refreshLayout();
                })).exceptionally(ex -> ErrorHandler.handle(this, "Satış tablosu yenilenemedi", ex));
    }

    @Override
    protected void onNew() {
        FormManager.showForm(AllForms.getForm(FormPos.class));
    }
}
