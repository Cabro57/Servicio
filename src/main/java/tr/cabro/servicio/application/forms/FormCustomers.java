package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.renderer.*;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.util.Format;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.component.table.TableActionColumnSupport;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SystemForm(name = "Müşteriler", description = "Müşteri veritabanını ve iletişim bilgilerini yönetin.")
public class FormCustomers extends AbstractTableForm {

    private final CustomerService customerService;
    private GenericTableModel<Customer> tableModel;
    private TableHeaderFilterSupport<Customer> headerFilters;

    // --- SAYFALAMA (DB-tabanlı) ---
    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = AppSettings.get().getTables().getCustomerPageSize();
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private PaginationBar paginationBar;

    public FormCustomers() {
        this.customerService = ServiceManager.getCustomerService();
    }

    @Override
    protected JComponent createPaginationComponent() {
        paginationBar = new PaginationBar(5, PAGE_SIZE_OPTIONS, pageSize,
                page -> { currentPage = page; refreshTable(); },
                newSize -> {
                    pageSize = newSize;
                    currentPage = 1;
                    AppSettings.get().getTables().setCustomerPageSize(pageSize);
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

    // --- 1. BAŞLIK, ARAMA VE GÖRÜNÜMLER ---

    @Override
    protected String getNewButtonText() {
        return "Yeni Müşteri";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/user-plus.svg";
    }

    @Override
    protected QuickAction getNewQuickAction() {
        return QuickAction.NEW_CUSTOMER;
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Ad, telefon, TC veya firma ara…";
    }

    private static final String VIEW_ALL = "all";
    private static final String VIEW_DEBT = "debt";
    private static final String VIEW_CORPORATE = "corporate";
    private static final String VIEW_PROBLEM = "problem";

    /** Bakiyesi (servis + satış − ödeme) artı olan müşteriler; kuruş yuvarlamasına tolerans. */
    private static final String DEBT_CONDITION =
            "c.id IN (SELECT customer_id FROM v_customer_balances WHERE balance > 0.009)";

    @Override
    protected void initViews() {
        addView(VIEW_ALL, "Tümü");
        addView(VIEW_DEBT, "Borçlu");
        addView(VIEW_CORPORATE, "Kurumsal");
        addView(VIEW_PROBLEM, "Sorunlu");
    }

    @Override
    protected Map<String, ColumnFilterValue> viewFilters(String key) {
        if (VIEW_DEBT.equals(key)) return Map.of("view:debt", ColumnFilterValue.condition(DEBT_CONDITION));
        if (VIEW_CORPORATE.equals(key)) return Map.of("c.customer_type", ColumnFilterValue.enumOf(CustomerType.KURUMSAL.name()));
        if (VIEW_PROBLEM.equals(key)) return Map.of("view:problem", ColumnFilterValue.condition("c.is_problematic = 1"));
        return Collections.emptyMap();
    }

    @Override
    protected CompletableFuture<Long> countMatching(Map<String, ColumnFilterValue> filters) {
        return customerService.searchFilteredPaged(currentSearchTerm, filters, 1, 1).thenApply(PageResult::getTotalItems);
    }

    @Override
    protected void onViewChanged(String key) {
        currentPage = 1;
        super.onViewChanged(key);
    }

    // --- 2. ÖZET CÜMLESİ ---

    @Override
    protected void refreshStats() {
        CompletableFuture<Long> total = customerService.searchFilteredPaged(null, Collections.emptyMap(), 1, 1)
                .thenApply(PageResult::getTotalItems);
        CompletableFuture<Long> debtors = customerService.searchFilteredPaged(null, viewFilters(VIEW_DEBT), 1, 1)
                .thenApply(PageResult::getTotalItems);
        CompletableFuture<BigDecimal> receivable = ServiceManager.getPaymentService().getTotalReceivables();

        CompletableFuture.allOf(total, debtors, receivable).thenRun(() -> SwingUtilities.invokeLater(() -> {
            long d = debtors.join();
            summary.set(
                    ListSummary.Part.strong(total.join() + " müşteri"),
                    d > 0 ? ListSummary.Part.meaning(d + " borçlu", "Servicio.warningColor") : ListSummary.Part.of("borçlu müşteri yok"),
                    d > 0 ? ListSummary.Part.of("toplam alacak " + Format.formatPrice(receivable.join())) : null);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Müşteri özeti yüklenemedi", ex));
    }

    // --- 3. TABLO YAPILANDIRMASI ---

    @Override
    protected void setupTable() {
        List<ColumnDef<Customer>> columns = Arrays.asList(
                new ColumnDef<Customer>("ID", String.class, c -> String.format("C-%03d", c.getId())).alignment(SwingConstants.LEADING),
                new ColumnDef<Customer>("Müşteri Adı", Customer.class, c -> c).alignment(SwingConstants.LEADING),
                new ColumnDef<Customer>("İletişim", Customer.class, c -> c).alignment(SwingConstants.LEADING),
                ColumnDef.<Customer>badge("Tip", CustomerType.class, Customer::getType).enumFilter("c.customer_type", CustomerType.class),
                new ColumnDef<Customer>("Cihaz Sayısı", Integer.class, Customer::getDeviceCount).alignment(SwingConstants.CENTER),
                ColumnDef.<Customer>currency("Toplam Harcama", Customer::getSpent),
                new ColumnDef<Customer>("Kayıt Tarihi", String.class, c -> c.getCreatedAt() != null ? c.getCreatedAt().format(DateFormats.dateTime()) : "-")
                        .alignment(SwingConstants.LEADING).dateRangeFilter("c.created_at"),
                ColumnDef.<Customer>actionColumn("")
        );
        // Toplam harcama bir anlam (borç/alacak) taşımaz: kalın, nötr.

        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        headerFilters = installHeaderFilters(columns);
    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz müşteri yok"; }

    @Override
    protected String getEmptyStateDescription() { return "Servis kaydı açabilmek için önce müşteri eklemelisiniz."; }

    @Override
    protected void loadTableData() {
        customerService.searchFilteredPaged(currentSearchTerm, effectiveFilters(), currentPage, pageSize).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(result.getItems());
                if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                setResultCount(result.getTotalItems());
                refreshLayout();
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Müşteri tablosu yenilenemedi", ex));
    }

    private void configureTableColumns() {
        // Not: hizalama ColumnDef.alignment(...) üzerinden geliyor; Tip/Toplam Harcama/İşlem
        // kolonlarının renderer'ı TableColumnConfigurator.applyColumnRenderers(...) ile atandı.

        table.getColumnModel().getColumn(0).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground", 12));

        // Ad kalın, altında firma/sorun bilgisi; tür ayrımını Tip rozeti taşır (satır başı ikon yok).
        table.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<Customer>(
                Customer::getFullName,
                c -> {
                    String firm = c.getBusinessName() != null && !c.getBusinessName().isBlank() ? c.getBusinessName() : null;
                    if (c.isProblematic()) return firm != null ? firm + "  ·  sorunlu müşteri" : "Sorunlu müşteri";
                    return firm != null ? firm : "";
                },
                c -> null,
                c -> c.isProblematic() ? UIManager.getColor("Servicio.dangerColor") : null));

        table.getColumnModel().getColumn(2).setCellRenderer(
                new MultiLineTableCellRenderer<Customer>(
                        c -> PhoneHelper.formatForDisplay(c.getPhoneNumber1()),
                        c -> c.getEmail() != null ? c.getEmail() : ""
                ).plainTop()
        );

        table.getColumnModel().getColumn(4).setCellRenderer(StyledLabelCellRenderer.of(SwingConstants.CENTER, null));
        table.getColumnModel().getColumn(5).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));

        table.getColumnModel().getColumn(6).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground; font: -1", 8));

        TableActionColumnSupport.install(table, 7, tableModel, new TableActionColumnSupport.Handlers<Customer>() {
            @Override
            public void onView(Customer c) {
                customerService.get(c.getId()).thenAccept(response -> {
                    response.ifPresent(customer -> SwingUtilities.invokeLater(() -> {
                        Form formInstance = new FormCustomer(customer);
                        FormManager.showForm(formInstance);
                    }));
                }).exceptionally(ex -> ErrorHandler.handle(FormCustomers.this, "Müşteri detayı açılamadı", ex));
            }

            @Override
            public void onEdit(Customer c) {
                openEditModal(c);
            }

            @Override
            public void onDelete(Customer selectedCustomer) {
                DialogHelper.confirmDelete(FormCustomers.this, "confirm.delete.customer", () ->
                        customerService.delete(selectedCustomer.getId()).thenAccept(v -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(FormCustomers.this, Toast.Type.SUCCESS, Messages.get("toast.customer.deleted"));
                                refreshTable();
                            });
                        }),
                        selectedCustomer.getFullName());
            }
        });

        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(130);
        table.getColumnModel().getColumn(6).setPreferredWidth(150);
        table.getColumnModel().getColumn(7).setMaxWidth(96);
        table.getColumnModel().getColumn(7).setMinWidth(110);
    }

    // --- 4. MODAL / PENCERE İŞLEMLERİ ---

    @Override
    protected void onNew() {
        final String id = "CustomerNew";
        CustomerEditPanel panel = new CustomerEditPanel(new Customer());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Yeni Müşteri Ekle", options, (controller, action) -> {
            if (action == SimpleModalBorder.OK_OPTION) {
                Customer updated = panel.getData();
                if (updated == null) {
                    controller.consume();
                    return;
                }

                customerService.save(updated, false).thenAccept(saved -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.added", updated.getFullName()));
                        refreshTable();
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(controller::consume);
                    return ErrorHandler.handle(this, "Müşteri eklenemedi", ex);
                });
            }
        }), id);
    }

    private void openEditModal(Customer customer) {
        final String id = "CustomerEdit";
        CustomerEditPanel panel = new CustomerEditPanel(customer);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Müşteri Düzenle", options, (controller, action) -> {
            if (action == SimpleModalBorder.OK_OPTION) {
                Customer updated = panel.getData();
                if (updated == null) {
                    controller.consume();
                    return;
                }

                updated.setId(customer.getId());
                updated.setCreatedAt(customer.getCreatedAt());

                customerService.save(updated, true).thenAccept(saved -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", updated.getFullName()));
                        refreshTable();
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(controller::consume);
                    return ErrorHandler.handle(this, "Müşteri güncellenemedi", ex);
                });
            }
        }), id);
    }
}