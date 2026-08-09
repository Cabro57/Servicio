package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.renderer.*;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.table.PaginationBar;
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

    // --- 1. ÜST KISIM VE ARAMA AYARLARI ---

    @Override
    protected String getNewButtonText() {
        return "Yeni Müşteri Ekle";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/user-plus.svg";
    }

    @Override
    protected String getTableTitleText() {
        return "Tüm Müşteriler";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "İsim, telefon veya e-posta ara...";
    }

    // --- 2. İSTATİSTİK KARTLARI (DASHBOARD) ---

    @Override
    protected void initCards() {
        cardBox.addCardItem(new Ikon("icons/users.svg", 0.7f), "Toplam Müşteri");
        cardBox.addCardItem(new Ikon("icons/store.svg", 0.7f), "Kurumsal Müşteri");
        cardBox.addCardItem(new Ikon("icons/triangle-alert.svg", 0.7f), "Sorunlu Müşteri");
        cardBox.addCardItem(new Ikon("icons/badge-turkish-lira.svg", 0.7f), "Toplam Ciro");
    }

    // Artık bu metodu tek başına çağırmıyoruz, refreshTable içinde her şeyi senkron yapıyoruz
    @Override
    protected void refreshStats() {
        customerService.getAllTable().thenAccept(customers -> {

            long totalCustomer = customers.size();

            long totalKurumsal = customers.stream()
                    .filter(c -> c.getType() == CustomerType.KURUMSAL)
                    .count();

            long totalProblematic = customers.stream()
                    .filter(Customer::isProblematic)
                    .count();

            long totalCiro = customers.stream()
                    .map(Customer::getSpent)
                    .mapToLong(BigDecimal::longValue)
                    .sum();

            SwingUtilities.invokeLater(() -> {
                cardBox.setValueAt(0, String.valueOf(totalCustomer), " ", "", true);
                cardBox.setValueAt(1, String.valueOf(totalKurumsal), " ", "", true);
                cardBox.setValueAt(2, String.valueOf(totalProblematic), " ", "", true);
                cardBox.setValueAt(3, String.valueOf(totalCiro), " ", "", true);
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Müşteri istatistikleri yüklenemedi", ex));
    }

    // --- 3. TABLO YAPILANDIRMASI ---

    @Override
    protected void setupTable() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        List<ColumnDef<Customer>> columns = Arrays.asList(
                new ColumnDef<Customer>("ID", String.class, c -> String.format("C-%03d", c.getId())).alignment(SwingConstants.LEADING),
                new ColumnDef<Customer>("Müşteri Adı", Customer.class, c -> c).alignment(SwingConstants.LEADING),
                new ColumnDef<Customer>("İletişim", Customer.class, c -> c).alignment(SwingConstants.LEADING),
                ColumnDef.<Customer>badge("Tip", CustomerType.class, Customer::getType).enumFilter("c.customer_type", CustomerType.class),
                new ColumnDef<Customer>("Cihaz Sayısı", Integer.class, Customer::getDeviceCount).alignment(SwingConstants.CENTER),
                ColumnDef.<Customer>currency("Toplam Harcama", Customer::getSpent),
                new ColumnDef<Customer>("Kayıt Tarihi", String.class, c -> c.getCreatedAt() != null ? c.getCreatedAt().format(formatter) : "-")
                        .alignment(SwingConstants.LEADING).dateRangeFilter("c.created_at"),
                ColumnDef.<Customer>actionColumn("İşlem")
        );

        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        headerFilters = installHeaderFilters(columns);
    }

    @Override
    protected void refreshTable() {
        Map<String, ColumnFilterValue> filters = headerFilters != null ? headerFilters.getActiveFilters() : java.util.Collections.emptyMap();

        customerService.searchFilteredPaged(currentSearchTerm, filters, currentPage, pageSize).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(result.getItems());
                if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                refreshStats();
                refreshLayout();
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Müşteri tablosu yenilenemedi", ex));
    }

    private void configureTableColumns() {
        // Not: hizalama ColumnDef.alignment(...) üzerinden geliyor; Tip/Toplam Harcama/İşlem
        // kolonlarının renderer'ı TableColumnConfigurator.applyColumnRenderers(...) ile atandı.

        table.getColumnModel().getColumn(0).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground; font: +1"));

        table.getColumnModel().getColumn(1).setCellRenderer(new CustomerTableCellRenderer());

        table.getColumnModel().getColumn(2).setCellRenderer(
                new MultiLineTableCellRenderer<Customer>(
                        c -> PhoneHelper.formatForDisplay(c.getPhoneNumber1()),
                        Customer::getEmail
                )
        );

        table.getColumnModel().getColumn(4).setCellRenderer(StyledLabelCellRenderer.of(SwingConstants.CENTER, null));

        table.getColumnModel().getColumn(6).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground", 15));

        TableActionColumnSupport.install(table, 7, tableModel, new TableActionColumnSupport.Handlers<Customer>() {
            @Override
            public void onView(Customer c) {
                customerService.get(c.getId()).thenAccept(response -> {
                    response.ifPresent(customer -> SwingUtilities.invokeLater(() -> {
                        Form formInstance = new FormCustomer(customer);
                        Toast.show(FormCustomers.this, Toast.Type.INFO, customer.getFirstName() + " detaylarına bakılıyor...");
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
                int confirm = JOptionPane.showConfirmDialog(
                        FormCustomers.this,
                        selectedCustomer.getFullName() + " adlı müşteriyi silmek istediğinize emin misiniz?",
                        "Müşteri Sil",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    customerService.delete(selectedCustomer.getId()).thenAccept(v -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(FormCustomers.this, Toast.Type.SUCCESS, "Müşteri silindi.");
                            refreshTable();
                        });
                    });
                }
            }
        });

        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(130);
        table.getColumnModel().getColumn(6).setPreferredWidth(150);
        table.getColumnModel().getColumn(7).setMaxWidth(180);
        table.getColumnModel().getColumn(7).setMinWidth(120);
    }

    // DÜZELTME: Verileri ve istatistikleri aynı anda çeken asenkron yapı
//    @Override
//    protected void refreshTable() {
//        CompletableFuture<List<CustomersTableDto>> customersFuture = customerService.getAll();
//        CompletableFuture<List<WorkOrder>> servicesFuture = workOrderService.getAll();
//
//        CompletableFuture.allOf(customersFuture, servicesFuture).thenAccept(v -> {
//            List<CustomersTableDto> allCustomers = customersFuture.join();
//            List<WorkOrder> allWorkOrders = servicesFuture.join();
//
//            customerSpentMap.clear();
//            BigDecimal totalGlobalRevenue = BigDecimal.ZERO;
//
//            // Müşterilerin servis ödemelerini hesapla (Ciro)
//            for (WorkOrder s : allWorkOrders) {
//                if (s.getCustomerId() == null || s.getCustomerId() <= 0) continue;
//
//                BigDecimal servicePaid = BigDecimal.ZERO;
//                if (s.getPayments() != null) {
//                    for (WorkOrderPayment payment : s.getPayments()) {
//                        servicePaid = servicePaid.add(payment.getAmount());
//                    }
//                }
//
//                customerSpentMap.merge(s.getCustomerId(), servicePaid, BigDecimal::add);
//                totalGlobalRevenue = totalGlobalRevenue.add(servicePaid);
//            }
//
//            // Kart İstatistiklerini Hesapla
//            long totalCount = allCustomers.size();
//            long normalCount = allCustomers.stream()
//                    .filter(c -> c.getType() != null && c.getType() == CustomerType.NORMAL)
//                    .count();
//
//            long businessCount = allCustomers.stream()
//                    .filter(c -> c.getType() != null &&
//                            (c.getType() == CustomerType.SMALL_BUSINESS || c.getType() == CustomerType.DEALER))
//                    .count();
//
//            BigDecimal finalTotalRevenue = totalGlobalRevenue;
//
//            // Arayüzü (UI) Güvenli Şekilde Güncelle
//            SwingUtilities.invokeLater(() -> {
//                tableModel.setData(allCustomers);
//
//                cardBox.setValueAt(0, String.valueOf(totalCount), "Sistemdeki tüm kayıtlar", "", true);
//                cardBox.setValueAt(1, String.valueOf(normalCount), "Bireysel kullanıcılar", "", true);
//                cardBox.setValueAt(2, String.valueOf(businessCount), "İşletme ve ticari hesaplar", "", true);
//                cardBox.setValueAt(3, Format.formatPrice(finalTotalRevenue), "Tüm zamanların cirosu", "", true);
//            });
//
//        }).exceptionally(ex -> {
//            SwingUtilities.invokeLater(() -> {
//                Toast.show(this, Toast.Type.ERROR, "Veriler yüklenemedi: " + ex.getMessage());
//                Servicio.getLogger().error("Tablo yenileme hatası", ex);
//            });
//            return null;
//        });
//    }

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
                        Toast.show(this, Toast.Type.SUCCESS, updated.getFullName() + " başarıyla eklendi.");
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
                        Toast.show(this, Toast.Type.SUCCESS, updated.getFullName() + " başarıyla güncellendi.");
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