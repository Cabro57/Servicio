package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.panels.QuickIntakePanel;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.component.table.TableActionColumnSupport;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.service.ReportManager;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@SystemForm(name = "Servis Kayıtları", description = "Tüm servis kayıtlarını oluşturmak için kullanılabilir")
public class FormWorkOrders extends AbstractTableForm {

    private final WorkOrderService service;
    private final ReportManager reportManager;
    private GenericTableModel<WorkOrder> tableModal;
    private TableHeaderFilterSupport<WorkOrder> headerFilters;

    // --- SAYFALAMA (DB-tabanlı) ---
    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = AppSettings.get().getTables().getWorkOrderPageSize();
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private PaginationBar paginationBar;

    public FormWorkOrders() {
        this.service = ServiceManager.getWorkOrderService();
        this.reportManager = ServiceManager.getReportManager();
    }

    // -------------------------------------------------------------------------
    // AbstractTableForm implementasyonu
    // -------------------------------------------------------------------------

    @Override
    protected String getNewButtonText()      { return "Yeni Kayıt Oluştur"; }

    @Override
    protected String getTableTitleText()     { return "Servis Kayıtları"; }

    @Override
    protected String getSearchPlaceholder()  { return "Müşteri, cihaz veya ID ara..."; }

    @Override
    protected JComponent createPaginationComponent() {
        paginationBar = new PaginationBar(5, PAGE_SIZE_OPTIONS, pageSize,
                page -> { currentPage = page; refreshTable(); },
                newSize -> {
                    pageSize = newSize;
                    currentPage = 1;
                    AppSettings.get().getTables().setWorkOrderPageSize(pageSize);
                    AppSettings.save();
                    refreshTable();
                });
        return paginationBar;
    }

    @Override
    protected void initCards() {
        cardBox.addCardItem(new Ikon("icons/sigma.svg",               0.7f), "Toplam Kayıt");
        cardBox.addCardItem(new Ikon("icons/activity.svg",            0.7f), "Aktif İşlemler");
        cardBox.addCardItem(new Ikon("icons/check-check.svg",         0.7f), "Tamamlanan");
        cardBox.addCardItem(new Ikon("icons/badge-turkish-lira.svg",  0.7f), "Toplam Ciro");
    }

    @Override
    protected void refreshStats() {
        reportManager.getDashboardSummaryCards("2000-01-01", "2100-01-01").thenAccept(stats ->
                SwingUtilities.invokeLater(() -> {
                    int completed = stats.getTotalRecords() - stats.getActiveRecords();
                    cardBox.setValueAt(0, String.valueOf(stats.getTotalRecords()),   "Tüm zamanların toplam kaydı",         "", true);
                    cardBox.setValueAt(1, String.valueOf(stats.getActiveRecords()),  "Şu an atölyede bekleyen cihazlar",    "", true);
                    cardBox.setValueAt(2, String.valueOf(completed),                 "Teslim edilen veya iptal edilenler",  "", true);
                    cardBox.setValueAt(3, Format.formatPrice(stats.getTotalRevenue()), "Sistemdeki brüt toplam ciro",       "", true);
                })
        ).exceptionally(ex -> {
            Servicio.getLogger().error("Servis istatistikleri yüklenirken hata oluştu", ex);
            return null;
        });
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<WorkOrder>> columns = Arrays.asList(
                new ColumnDef<WorkOrder>("Kayıt No",        Long.class,          WorkOrder::getId).alignment(SwingConstants.CENTER),
                new ColumnDef<WorkOrder>("Müşteri Bilgisi", Customer.class,      WorkOrder::getCustomer).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Cihaz Bilgisi",   Device.class,        WorkOrder::getDevice).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Şikayet",         String.class,        WorkOrder::getReportedFault).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Tarih",           WorkOrder.class,     s -> s).alignment(SwingConstants.LEADING).dateRangeFilter("s.created_at"),
                ColumnDef.<WorkOrder>currency("Kalan Ücret", WorkOrder::getRemainingAmount),
                ColumnDef.<WorkOrder>badge("Durum", ServiceStatus.class, WorkOrder::getServiceStatus).enumFilter("s.service_status", ServiceStatus.class),
                ColumnDef.<WorkOrder>actionColumn("İşlem")
        );
        tableModal = new GenericTableModel<>(columns);
        setTableModel(tableModal);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        headerFilters = installHeaderFilters(columns);
    }

    @Override
    protected void initTableFilter(TableModel model) {
        sorter = new TableRowSorter<>(tableModal);

        sorter.setComparator(1, Comparator.comparing(c -> {
            if (c == null) return "";
            Customer cust = (Customer) c;
            return cust.getFullName() + " " + cust.getPhoneNumber1();
        }));
        sorter.setComparator(2, Comparator.comparing(s -> {
            if (s == null) return "";
            Device d = (Device) s;
            return d.getBrand() + " " + d.getModel();
        }));
        sorter.setComparator(4, Comparator.comparing(s -> {
            if (s == null) return LocalDateTime.MIN;
            LocalDateTime date = ((WorkOrder) s).getCreatedAt();
            return date != null ? date : LocalDateTime.MIN;
        }));

        sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(4, SortOrder.DESCENDING)));
    }

    @Override
    protected void refreshTable() {
        if (tableModal == null) return;

        Map<String, ColumnFilterValue> filters =
                headerFilters != null ? headerFilters.getActiveFilters() : Collections.emptyMap();

        service.searchFilteredPaged(currentSearchTerm, filters, currentPage, pageSize)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    tableModal.setData(result.getItems());
                    if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                    refreshStats();
                    refreshLayout();
                }))
                .exceptionally(ex -> ErrorHandler.handle(this, "Servis tablosu yenilenemedi", ex));
    }

    @Override
    protected void applyFilter() {
        currentSearchTerm = searchField.getText().trim();
        currentPage = 1;
        refreshTable();
    }

    // -------------------------------------------------------------------------
    // Yeni kayıt modalı
    // -------------------------------------------------------------------------

    @Override
    protected void onNew() {
        showIntakeModal(new WorkOrder(), "Servis Kaydı", false);
    }

    private void openEditModal(WorkOrder workOrder) {
        showIntakeModal(workOrder, "Kayıt Düzenle (SRV-" + workOrder.getId() + ")", true);
    }

    /**
     * Yeni ve düzenleme modalını tek bir yerde yönetir.
     * {@link QuickIntakePanel} artık sabit bir eylem kodu yerine callback alıyor;
     * bu metod callback'in ne yapacağını biliyor, panel bilmiyor.
     *
     * @param data     servis kaydı (yeni veya mevcut)
     * @param title    modal başlığı
     * @param isEdit   true ise güncelleme, false ise yeni kayıt modu
     */
    private void showIntakeModal(WorkOrder data, String title, boolean isEdit) {
        final String MODAL_ID = isEdit ? "service_edit_modal" : "quick_intake_modal";

        // Panel yeni müşteri eklemek istediğinde bu Runnable tetiklenir.
        // Panel'in kendisi modalın nasıl açıldığını bilmez; bu sorumluluğu bu form üstlenir.
        //
        // Lambda içinde 'panel' referansına ihtiyaç duyduğumuz için önce bir dizi wrapper kullanıyoruz.
        // Java'da lambda içindeki değişken effectively-final olmalı; tek elemanlı dizi bu kısıtlamayı aşar.
        QuickIntakePanel[] panelRef = new QuickIntakePanel[1];
        panelRef[0] = new QuickIntakePanel(data, () -> AppModal.pushModalDeferred(() -> {
            CustomerEditPanel newCustomerPanel = new CustomerEditPanel(new Customer());
            return new SimpleModalBorder(newCustomerPanel, "Yeni Müşteri", SimpleModalBorder.YES_NO_OPTION, (c1, a1) -> {
                if (a1 != SimpleModalBorder.YES_OPTION) return;
                Customer newCustomer = newCustomerPanel.getData();
                if (newCustomer == null) { c1.consume(); return; }
                c1.consume();
                newCustomer.setCreatedAt(LocalDateTime.now());
                ServiceManager.getCustomerService().save(newCustomer, false).thenAccept(saved ->
                        SwingUtilities.invokeLater(() -> {
                            panelRef[0].appendNewCustomer(saved);
                            AppModal.popModal(MODAL_ID);
                        })
                );
            });
        }, MODAL_ID));
        QuickIntakePanel panel = panelRef[0];

        // Düzenleme modunda sadece "Kaydet" ve "İptal" butonu gösterilir.
        // Yeni kayıt modunda üçüncü seçenek "Servisi Başlat" da eklenir.
        SimpleModalBorder.Option[] options = isEdit
                ? new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Değişiklikleri Kaydet", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("İptal",                  SimpleModalBorder.CANCEL_OPTION)
        }
                : new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Servisi Kaydet",  SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("Servisi Başlat",  SimpleModalBorder.NO_OPTION),
                new SimpleModalBorder.Option("İptal",           SimpleModalBorder.CANCEL_OPTION)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, title, options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) {
                panel.requestInitialFocus();
                return;
            }
            if (action == SimpleModalBorder.CANCEL_OPTION) return;
            if (action == SimpleModalBorder.CLOSE_OPTION) return;

            // Kaydet veya Başlat seçenekleri → formu topla ve kaydet
            boolean openDetail = (action == SimpleModalBorder.NO_OPTION);
            WorkOrder formData = panel.getData();
            if (formData == null) { controller.consume(); return; }

            service.save(formData, isEdit).thenCompose(saved ->
                    ServiceManager.getDeviceAccessCredentialService()
                            .save(saved.getId(), panel.getDeviceAccessType(), panel.getDeviceAccessSecret())
                            .thenApply(v -> saved)
            ).thenAccept(saved ->
                    SwingUtilities.invokeLater(() -> {
                        String msg = isEdit ? "Servis bilgileri güncellendi." : "Servis başarıyla kaydedildi.";
                        Toast.show(this, Toast.Type.SUCCESS, msg);
                        refreshTable();
                        if (openDetail) FormManager.showForm(new FormWorkOrder(saved));
                    })
            ).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(this, "Servis kaydı kaydedilemedi", ex);
            });
        }), MODAL_ID);
    }

    // -------------------------------------------------------------------------
    // Tablo sütun yapılandırması
    // -------------------------------------------------------------------------

    private void configureTableColumns() {
        // Not: hizalama artık ColumnDef.alignment(...) üzerinden geliyor; para/rozet/işlem
        // kolonlarının renderer'ı TableColumnConfigurator.applyColumnRenderers(...) ile atandı
        // (bkz. setupTable()). Burada sadece bu forma özel (bespoke) renderer'lar kalıyor.

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                label.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
                label.setHorizontalAlignment(SwingConstants.CENTER);
                if (value instanceof Long) {
                    Long id = (Long) value;
                    label.setText("SRV-" + id);
                }
                return label;
            }
        });

        table.getColumnModel().getColumn(1).setCellRenderer(
                new MultiLineTableCellRenderer<Customer>(
                        c -> c != null ? c.getFullName() : "Bilinmeyen Müşteri",
                        c -> c != null ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : ""
                )
        );
        table.getColumnModel().getColumn(2).setCellRenderer(
                new MultiLineTableCellRenderer<Device>(
                        d -> d != null ? d.getBrand() + " " + d.getModel() : "Bilinmeyen Cihaz",
                        d -> "SN: " + (d != null && d.getSerialNo() != null ? d.getSerialNo() : "Bilinmiyor")
                )
        );
        table.getColumnModel().getColumn(3).setCellRenderer(new TooltipCellRenderer());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", new Locale("tr", "TR"));
        table.getColumnModel().getColumn(4).setCellRenderer(
                new MultiLineTableCellRenderer<WorkOrder>(
                        s -> s.getCreatedAt() != null ? s.getCreatedAt().format(formatter) : "Tarih Yok",
                        s -> {
                            String dateStr = s.getDeliveryDate() != null ? s.getDeliveryDate().format(formatter) : "-";
                            if (s.getServiceStatus() == ServiceStatus.RETURN) {
                                return "İade: " + dateStr;
                            }
                            if (s.getServiceStatus() == ServiceStatus.DELIVERED) {
                                return "Teslim: " + dateStr;
                            }
                            LocalDateTime est = s.getCreatedAt() != null ? s.getCreatedAt().plusDays(3) : null;
                            return "Tahmini: " + (est != null ? est.format(formatter) : "-");
                        },
                        s -> null,
                        s -> {
                            if (s.getServiceStatus() == ServiceStatus.RETURN) return new Color(220, 53, 69);
                            if (s.getServiceStatus() == ServiceStatus.DELIVERED) return new Color(46, 204, 113);
                            return null;
                        }
                )
        );

        // Kalan ücret 0 ise para birimi yerine "Borç Yok" yeşil metniyle gösterilir.
        // NOT: DefaultTableCellRenderer.setForeground(...) çağrılan rengi "unselectedForeground"
        // alanında kalıcı olarak saklar (JTable render sırasında bu alanı bir dahaki satırda da
        // kullanır) — bu yüzden yeşili SADECE sıfır satırda değil, her çağrıda (else dalında da)
        // açıkça sıfırlamak gerekiyor; aksi halde bir kez yeşile boyanan renderer diğer tüm
        // satırlarda da yeşil kalır.
        table.getColumnModel().getColumn(5).setCellRenderer(new CurrencyTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof BigDecimal && ((BigDecimal) value).compareTo(BigDecimal.ZERO) == 0) {
                    setText("Borç Yok");
                    setForeground(new Color(46, 204, 113));
                } else {
                    setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                }
                return c;
            }
        });

        TableActionColumnSupport.install(table, 7, tableModal, new TableActionColumnSupport.Handlers<WorkOrder>() {
            @Override
            public void onEdit(WorkOrder wo) {
                if (wo != null) openEditModal(wo);
            }

            @Override
            public void onDelete(WorkOrder wo) {
                if (wo == null) return;

                int confirm = JOptionPane.showConfirmDialog(
                        FormWorkOrders.this,
                        "SRV-" + wo.getId() + " numaralı servis kaydını silmek istediğinize emin misiniz?\nBu işlem geri alınamaz.",
                        "Silme Onayı",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
                );
                if (confirm != JOptionPane.YES_OPTION) return;

                service.delete(wo.getId())
                        .thenAccept(v -> SwingUtilities.invokeLater(() -> {
                            Toast.show(FormWorkOrders.this, Toast.Type.SUCCESS, "Kayıt başarıyla silindi.");
                            refreshTable();
                        }))
                        .exceptionally(ex -> ErrorHandler.handle(FormWorkOrders.this, "Servis kaydı silinemedi", ex));
            }

            @Override
            public void onView(WorkOrder wo) {
                if (wo == null) {
                    Toast.show(FormWorkOrders.this, Toast.Type.WARNING, "İstenen servis bulunamadı.");
                    return;
                }
                service.get(wo.getId()).thenAccept(opt ->
                        SwingUtilities.invokeLater(() -> {
                            if (opt.isPresent()) FormManager.showForm(new FormWorkOrder(opt.get()));
                            else Toast.show(FormWorkOrders.this, Toast.Type.WARNING, "Böyle bir servis bulunamadı.");
                        })
                ).exceptionally(ex -> ErrorHandler.handle(FormWorkOrders.this, "Servis detayı açılamadı", ex));
            }
        });

        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(120);
    }
}