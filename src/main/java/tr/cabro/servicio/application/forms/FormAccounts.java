package tr.cabro.servicio.application.forms;

import java.util.ArrayList;
import tr.cabro.servicio.application.component.table.Lookups;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.renderer.AmountChipCellRenderer;
import tr.cabro.servicio.application.renderer.ChipCellRenderer;
import tr.cabro.servicio.application.renderer.RowParts;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.application.renderer.StatusDotCellRenderer;
import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;
import java.util.Map;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.table.AppPagination;
import raven.swingpack.JPagination;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.CollectionPanel;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.dto.CustomerBalanceDto;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.PaymentService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** Bakiyesi (borcu) olan müşterilerin listesi — bakiye {@code v_customer_balances}'tan gelir, servis + POS birlikte. */
@SystemForm(name = "Cari Hesaplar", description = "Bakiyesi olan müşteriler ve tahsilat")
public class FormAccounts extends AbstractTableForm {

    private final PaymentService paymentService;
    private final CustomerService customerService;
    private GenericTableModel<CustomerBalanceDto> tableModel;
    private Map<Long, Customer> customerCache = Collections.emptyMap();

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = 25;
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private PaginationBar paginationBar;

    public FormAccounts() {
        this.paymentService = ServiceManager.getPaymentService();
        this.customerService = ServiceManager.getCustomerService();
    }

    @Override
    protected String getNewButtonText() {
        return "Tahsilat Al";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/hand-coins.svg";
    }

    @Override
    protected QuickAction getNewQuickAction() {
        return QuickAction.COLLECT;
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Müşteri adı veya telefon ara…";
    }

    @Override
    protected List<JComponent> createHeaderActions() {
        return List.of(secondaryButton("Kasa Raporu", "icons/banknote.svg", QuickAction.CASH_REPORT::run));
    }

    @Override
    protected JComponent createPaginationComponent() {
        paginationBar = new PaginationBar(5, PAGE_SIZE_OPTIONS, pageSize,
                page -> { currentPage = page; refreshTable(); },
                newSize -> { pageSize = newSize; currentPage = 1; refreshTable(); });
        return paginationBar;
    }

    @Override
    protected void applyFilter() {
        currentSearchTerm = searchField.getText().trim();
        currentPage = 1;
        refreshTable();
    }

    // --- Görünüm sekmeleri: ilki (Borçlu) varsayılan ---

    private static final String VIEW_DEBT = "debt";
    private static final String VIEW_CREDIT = "credit";
    private static final String VIEW_ALL = "all";

    @Override
    protected void initViews() {
        addView(VIEW_DEBT, "Borçlu");
        addView(VIEW_CREDIT, "Alacaklı");
        addView(VIEW_ALL, "Hareketi olan tüm hesaplar");
    }

    @Override
    protected Map<String, ColumnFilterValue> viewFilters(String key) {
        if (VIEW_DEBT.equals(key)) return Map.of("view:debt", ColumnFilterValue.condition("vb.balance > 0.009"));
        // Eksi bakiye: iade ya da fazla ödeme sonrası dükkân müşteriye borçlu.
        if (VIEW_CREDIT.equals(key)) return Map.of("view:credit", ColumnFilterValue.condition("vb.balance < -0.009"));
        return Map.of("view:all", ColumnFilterValue.condition("(vb.total_debt <> 0 OR vb.total_paid <> 0)"));
    }

    @Override
    protected CompletableFuture<Long> countMatching(Map<String, ColumnFilterValue> filters) {
        return paymentService.searchAccountsPaged(currentSearchTerm, filters, 1, 1).thenApply(PageResult::getTotalItems);
    }

    @Override
    protected void onViewChanged(String key) {
        currentPage = 1;
        super.onViewChanged(key);
    }

    @Override
    protected void refreshStats() {
        Map<String, ColumnFilterValue> debt = viewFilters(VIEW_DEBT);
        Map<String, ColumnFilterValue> credit = viewFilters(VIEW_CREDIT);
        CompletableFuture<Long> debtCount = paymentService.searchAccountsPaged(null, debt, 1, 1).thenApply(PageResult::getTotalItems);
        CompletableFuture<BigDecimal> debtSum = paymentService.sumAccountBalances(debt);
        CompletableFuture<Long> creditCount = paymentService.searchAccountsPaged(null, credit, 1, 1).thenApply(PageResult::getTotalItems);
        CompletableFuture<BigDecimal> creditSum = paymentService.sumAccountBalances(credit);

        CompletableFuture.allOf(debtCount, debtSum, creditCount, creditSum).thenRun(() -> SwingUtilities.invokeLater(() -> {
            long d = debtCount.join();
            long c = creditCount.join();
            summary.set(
                    d > 0 ? ListSummary.Part.strong(d + " borçlu müşteri") : ListSummary.Part.strong("Borçlu müşteri yok"),
                    d > 0 ? ListSummary.Part.meaning("toplam alacak " + Format.formatPrice(debtSum.join()), "Servicio.warningColor") : null,
                    c > 0 ? ListSummary.Part.of(c + " müşteriye borçluyuz") : null,
                    c > 0 ? ListSummary.Part.meaning(Format.formatPrice(creditSum.join().negate()), "Servicio.dangerColor") : null);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Cari hesap özeti yüklenemedi", ex));
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<CustomerBalanceDto>> columns = Arrays.asList(
                new ColumnDef<CustomerBalanceDto>("Müşteri", CustomerBalanceDto.class, b -> b).alignment(SwingConstants.LEADING),
                new ColumnDef<CustomerBalanceDto>("Toplam Borçlanma", BigDecimal.class, CustomerBalanceDto::getTotalDebt).alignment(SwingConstants.TRAILING),
                new ColumnDef<CustomerBalanceDto>("Toplam Tahsilat", BigDecimal.class, CustomerBalanceDto::getTotalPaid).alignment(SwingConstants.TRAILING),
                new ColumnDef<CustomerBalanceDto>("Bakiye", BigDecimal.class, CustomerBalanceDto::getBalance).alignment(SwingConstants.TRAILING),
                new ColumnDef<CustomerBalanceDto>("Durum", CustomerBalanceDto.class, b -> b).alignment(SwingConstants.CENTER),
                new ColumnDef<CustomerBalanceDto>("", String.class, b -> "").editable(true)
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);

        // Müşteri: bakiye durumunun rengiyle nokta; altında telefon.
        table.getColumnModel().getColumn(0).setCellRenderer(new StatusDotCellRenderer<CustomerBalanceDto>(
                b -> customerName(b.getCustomerId()),
                b -> {
                    Customer c = customerCache.get(b.getCustomerId());
                    return c != null ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : "";
                },
                b -> stateOf(b).color));
        // Borçlanma ve tahsilat zaten sayılmış tutarlar: nötr; anlamı bakiye taşır.
        table.getColumnModel().getColumn(1).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));
        table.getColumnModel().getColumn(2).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));
        table.getColumnModel().getColumn(3).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.BALANCE));
        table.getColumnModel().getColumn(4).setCellRenderer(new ChipCellRenderer<CustomerBalanceDto>(b ->
                ChipCellRenderer.badge(stateOf(b).label, stateOf(b).color)));

        addSort("NEWEST", "Bakiye (büyükten)");
        addSort("DEBT", "Borç (çoktan aza)");
        addSort("CREDIT", "Alacak (çoktan aza)");
        addSort("NAME", "Müşteri adı (A-Z)");

        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        table.getColumnModel().getColumn(0).setMinWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);

        DynamicActionColumnSupport.install(table, 5, tableModel, List.of(
                DynamicActionColumnSupport.button("icons/hand-coins.svg", SemanticColor.success(), "Tahsilat al",
                        b -> {
                            Customer c = customerCache.get(b.getCustomerId());
                            if (c != null) CollectionPanel.open(this, c, this::refreshTable);
                        })
        ));
        openRowsWith(tableModel, b -> {
            Customer c = customerCache.get(b.getCustomerId());
            if (c != null) FormManager.showForm(new FormCustomer(c));
        }, 5);

    }

    private String customerName(Long customerId) {
        Customer c = customerCache.get(customerId);
        return c != null ? c.getFullName() : "#" + customerId;
    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz cari hesap hareketi yok"; }

    @Override
    protected String getEmptyStateDescription() { return "Veresiye satış veya tahsilat yaptığınızda burada görünür."; }

    /** Bakiye durumu: etiket, renk ve tutarın altındaki açıklama tek yerden. */
    private static final class State {
        final String label;
        final String caption;
        final BadgeColor color;

        State(String label, String caption, BadgeColor color) {
            this.label = label;
            this.caption = caption;
            this.color = color;
        }
    }

    private static State stateOf(CustomerBalanceDto b) {
        if (b.getBalance() == null || b.getBalance().signum() == 0) return new State("Kapalı", null, BadgeColor.GRAY);
        return b.getBalance().signum() > 0 ? new State("Borçlu", "bize borçlu", BadgeColor.YELLOW)
                : new State("Alacaklı", "biz borçluyuz", BadgeColor.RED);
    }

    @Override
    protected void onSortChanged(String key) {
        currentPage = 1;
        super.onSortChanged(key);
    }

    @Override
    protected void loadTableData() {
        CompletableFuture<PageResult<CustomerBalanceDto>> future =
                paymentService.searchAccountsPaged(currentSearchTerm, effectiveFilters(), currentPage, pageSize, getSortKey());

        future.thenCompose(result -> {
            List<Long> customerIds = result.getItems().stream().map(CustomerBalanceDto::getCustomerId).collect(Collectors.toList());
            return customerService.getAll(customerIds).thenApply(customers -> {
                customerCache = customers.stream().collect(Collectors.toMap(Customer::getId, c -> c));
                return result;
            });
        }).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            tableModel.setData(result.getItems());
            if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
            setResultCount(result.getTotalItems());
            refreshLayout();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(this::resetKeyboardActions);
            return ErrorHandler.handle(this, "Cari hesap tablosu yenilenemedi", ex);
        });
    }

    @Override
    protected void onNew() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            CustomerBalanceDto b = tableModel.getItemAt(table.convertRowIndexToModel(row));
            Customer c = b != null ? customerCache.get(b.getCustomerId()) : null;
            if (c != null) {
                CollectionPanel.open(this, c, this::refreshTable);
                return;
            }
        }
        // Seçili satır yoksa pencere müşteri seçiciyle açılır.
        CollectionPanel.open(this, null, this::refreshTable);
    }
}
