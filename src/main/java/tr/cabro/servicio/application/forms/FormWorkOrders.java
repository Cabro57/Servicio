package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.panels.QuickIntakePanel;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.service.ReportManager;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;
import raven.swingpack.JPagination;

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
import java.util.concurrent.CompletableFuture;

@SystemForm(name = "Servis Kayıtları", description = "Tüm servis kayıtlarını oluşturmak için kullanılabilir")
public class FormWorkOrders extends AbstractTableForm {

    private final WorkOrderService service;
    private final ReportManager reportManager;
    private GenericTableModel<WorkOrder> tableModal;
    private ServiceStatus currentStatusFilter;

    // --- SAYFALAMA (DB-tabanlı) ---
    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = Servicio.getSettings().getWorkOrderPageSize();
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private JPagination pagination;

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
    protected boolean hasFilterCombo()       { return true; }

    @Override
    protected JComboBox<Object> createFilterCombo() {
        DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();
        model.addElement("Tümü");
        for (ServiceStatus status : ServiceStatus.values()) model.addElement(status);

        filterCombo = new JComboBox<>(model);
        filterCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        filterCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ServiceStatus) setText(((ServiceStatus) value).getDisplayName());
                return this;
            }
        });
        filterCombo.addActionListener(e -> {
            Object selected = filterCombo.getSelectedItem();
            currentStatusFilter = (selected instanceof ServiceStatus) ? (ServiceStatus) selected : null;
            currentPage = 1;
            refreshTable();
        });
        return filterCombo;
    }

    @Override
    protected JComponent createPaginationComponent() {
        pagination = new JPagination(5, 1, 1);
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
            Servicio.getSettings().setWorkOrderPageSize(pageSize);
            Servicio.getSettings().save();
            refreshTable();
        });

        JPanel panel = new JPanel(new net.miginfocom.swing.MigLayout("insets 0, gapx 10", "[][]", "[]"));
        panel.setOpaque(false);
        panel.add(new JLabel("Sayfa başına:"));
        panel.add(pageSizeCombo);
        panel.add(pagination);
        return panel;
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
                new ColumnDef<>("Kayıt No",        Long.class,          WorkOrder::getId),
                new ColumnDef<>("Müşteri Bilgisi", Customer.class,      WorkOrder::getCustomer),
                new ColumnDef<>("Cihaz Bilgisi",   Device.class,        WorkOrder::getDevice),
                new ColumnDef<>("Şikayet",         String.class,        WorkOrder::getReportedFault),
                new ColumnDef<>("Tarih",           WorkOrder.class,     s -> s),
                new ColumnDef<>("Kalan Ücret",     BigDecimal.class,    WorkOrder::getRemainingAmount),
                new ColumnDef<>("Durum",           ServiceStatus.class, WorkOrder::getServiceStatus),
                new ColumnDef<>("İşlem",           String.class,        s -> "Detay")
        );
        tableModal = new GenericTableModel<>(columns);
        setTableModel(tableModal);
        configureTableColumns();
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

        CompletableFuture<PageResult<WorkOrder>> future;
        if (!currentSearchTerm.isEmpty()) {
            future = service.searchPaged(currentSearchTerm, currentPage, pageSize);
        } else if (currentStatusFilter != null) {
            future = service.getAllPaged(currentPage, pageSize, currentStatusFilter.name());
        } else {
            future = service.getAllPaged(currentPage, pageSize);
        }

        future.thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    tableModal.setData(result.getItems());
                    if (pagination != null) pagination.setPageRange(result.getPage(), result.getTotalPages());
                    refreshStats();
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() ->
                            Toast.show(this, Toast.Type.ERROR, "Veriler yüklenemedi: " + ex.getMessage()));
                    Servicio.getLogger().error("Tablo yenileme hatası", ex);
                    return null;
                });
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
        panelRef[0] = new QuickIntakePanel(data, () -> {
            CustomerEditPanel newCustomerPanel = new CustomerEditPanel(new Customer());
            AppModal.pushModal(
                    new SimpleModalBorder(newCustomerPanel, "Yeni Müşteri", SimpleModalBorder.YES_NO_OPTION, (c1, a1) -> {
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
                    }),
                    MODAL_ID
            );
        });
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

            service.save(formData, isEdit).thenAccept(saved ->
                    SwingUtilities.invokeLater(() -> {
                        String msg = isEdit ? "Servis bilgileri güncellendi." : "Servis başarıyla kaydedildi.";
                        Toast.show(this, Toast.Type.SUCCESS, msg);
                        refreshTable();
                        if (openDetail) FormManager.showForm(new FormWorkOrder(saved));
                    })
            ).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    controller.consume();
                    String cause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    Toast.show(this, Toast.Type.ERROR, "Hata: " + cause);
                });
                Servicio.getLogger().error("Kayıt hatası", ex);
                return null;
            });
        }), MODAL_ID);
    }

    // -------------------------------------------------------------------------
    // Tablo sütun yapılandırması
    // -------------------------------------------------------------------------

    private void configureTableColumns() {
        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.TRAILING,
                SwingConstants.CENTER,
                SwingConstants.CENTER
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

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
                            if (s.getServiceStatus() == ServiceStatus.DELIVERED || s.getServiceStatus() == ServiceStatus.RETURN) {
                                return "Bitiş: " + (s.getDeliveryDate() != null ? s.getDeliveryDate().format(formatter) : "-");
                            }
                            LocalDateTime est = s.getCreatedAt() != null ? s.getCreatedAt().plusDays(3) : null;
                            return "Tahmini: " + (est != null ? est.format(formatter) : "-");
                        },
                        s -> null,
                        s -> (s.getServiceStatus() == ServiceStatus.DELIVERED || s.getServiceStatus() == ServiceStatus.RETURN)
                                ? new Color(46, 204, 113) : null
                )
        );

        table.getColumnModel().getColumn(5).setCellRenderer(new CurrencyTableCellRenderer());

        table.getColumnModel().getColumn(6).setCellRenderer(new UniversalVisualizableRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(7).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                cancelEditingIfNeeded();
                WorkOrder wo = getWorkOrderAtRow(row);
                if (wo != null) openEditModal(wo);
            }

            @Override
            public void onDelete(int row) {
                cancelEditingIfNeeded();
                WorkOrder wo = getWorkOrderAtRow(row);
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
                        .exceptionally(ex -> {
                            SwingUtilities.invokeLater(() ->
                                    Toast.show(FormWorkOrders.this, Toast.Type.ERROR, "Silme başarısız: " + ex.getCause().getMessage()));
                            return null;
                        });
            }

            @Override
            public void onView(int row) {
                cancelEditingIfNeeded();
                WorkOrder wo = getWorkOrderAtRow(row);
                if (wo == null) {
                    Toast.show(FormWorkOrders.this, Toast.Type.WARNING, "İstenen servis bulunamadı.");
                    return;
                }
                service.get(wo.getId()).thenAccept(opt ->
                        SwingUtilities.invokeLater(() -> {
                            if (opt.isPresent()) FormManager.showForm(new FormWorkOrder(opt.get()));
                            else Toast.show(FormWorkOrders.this, Toast.Type.WARNING, "Böyle bir servis bulunamadı.");
                        })
                ).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() ->
                            Toast.show(FormWorkOrders.this, Toast.Type.ERROR, ex.getMessage()));
                    Servicio.getLogger().error("Servis detay hatası", ex);
                    return null;
                });
            }
        }));

        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(120);
    }

    // -------------------------------------------------------------------------
    // Yardımcı
    // -------------------------------------------------------------------------

    private void cancelEditingIfNeeded() {
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
    }

    private WorkOrder getWorkOrderAtRow(int viewRow) {
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModal.getItemAt(modelRow);
    }
}