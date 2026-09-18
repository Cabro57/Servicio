package tr.cabro.servicio.application.forms;

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
    private JPagination pagination;

    public FormAccounts() {
        this.paymentService = ServiceManager.getPaymentService();
        this.customerService = ServiceManager.getCustomerService();
    }

    @Override
    protected String getNewButtonText() {
        return "Yeni Satış (POS)";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/credit-card.svg";
    }

    @Override
    protected String getTableTitleText() {
        return "Bakiyesi Olan Müşteriler";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Müşteri ara...";
    }

    @Override
    protected JComponent createPaginationComponent() {
        pagination = new AppPagination(5, 1, 1);
        pagination.addChangeListener(e -> {
            currentPage = pagination.getSelectedPage();
            refreshTable();
        });

        JComboBox<Integer> pageSizeCombo = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeCombo.setSelectedItem(pageSize);
        pageSizeCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        pageSizeCombo.addActionListener(e -> {
            pageSize = (Integer) pageSizeCombo.getSelectedItem();
            currentPage = 1;
            refreshTable();
        });

        JPanel panel = new JPanel(new MigLayout("insets 0, gapx 10", "[][]", "[]"));
        panel.setOpaque(false);
        panel.add(new JLabel("Sayfa başına:"));
        panel.add(pageSizeCombo);
        panel.add(pagination);
        return panel;
    }

    @Override
    protected void applyFilter() {
        currentSearchTerm = searchField.getText().trim();
        currentPage = 1;
        refreshTable();
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<CustomerBalanceDto>> columns = Arrays.asList(
                new ColumnDef<>("Müşteri", String.class, b -> customerName(b.getCustomerId())),
                new ColumnDef<>("Toplam Borç", BigDecimal.class, CustomerBalanceDto::getTotalDebt),
                new ColumnDef<>("Toplam Tahsilat", BigDecimal.class, CustomerBalanceDto::getTotalPaid),
                new ColumnDef<>("Bakiye", BigDecimal.class, CustomerBalanceDto::getBalance),
                new ColumnDef<CustomerBalanceDto>("", String.class, b -> "").editable(true)
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);

        table.getColumnModel().getColumn(1).setCellRenderer(new CurrencyTableCellRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new CurrencyTableCellRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new CurrencyTableCellRenderer());

        DynamicActionColumnSupport.install(table, 4, tableModel, List.of(
                DynamicActionColumnSupport.button("icons/hand-coins.svg", new Color(46, 204, 113), "Tahsilat Al",
                        b -> {
                            Customer c = customerCache.get(b.getCustomerId());
                            if (c != null) CollectionPanel.open(this, c, this::refreshTable);
                        }),
                DynamicActionColumnSupport.button("icons/eye.svg", new Color(13, 110, 253), "Müşteri Kartı",
                        b -> {
                            Customer c = customerCache.get(b.getCustomerId());
                            if (c != null) FormManager.showForm(new FormCustomer(c));
                        })
        ));
        table.getColumnModel().getColumn(4).setMaxWidth(90);
    }

    private String customerName(Long customerId) {
        Customer c = customerCache.get(customerId);
        return c != null ? c.getFullName() : "#" + customerId;
    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz cari hesap hareketi yok"; }

    @Override
    protected String getEmptyStateDescription() { return "Veresiye satış veya tahsilat yaptığınızda burada görünür."; }

    @Override
    protected void loadTableData() {
        CompletableFuture<PageResult<CustomerBalanceDto>> future =
                paymentService.getCustomersWithBalancePaged(currentSearchTerm, currentPage, pageSize);

        future.thenCompose(result -> {
            List<Long> customerIds = result.getItems().stream().map(CustomerBalanceDto::getCustomerId).collect(Collectors.toList());
            return customerService.getAll(customerIds).thenApply(customers -> {
                customerCache = customers.stream().collect(Collectors.toMap(Customer::getId, c -> c));
                return result;
            });
        }).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            tableModel.setData(result.getItems());
            if (pagination != null) pagination.setPageRange(result.getPage(), result.getTotalPages());
            refreshLayout();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(this::resetKeyboardActions);
            return ErrorHandler.handle(this, "Cari hesap tablosu yenilenemedi", ex);
        });
    }

    @Override
    protected void onNew() {
        FormManager.showForm(tr.cabro.servicio.application.system.AllForms.getForm(FormPos.class));
    }
}
