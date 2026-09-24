package tr.cabro.servicio.application.forms;

import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.application.component.table.Lookups;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.AmountChipCellRenderer;
import tr.cabro.servicio.application.renderer.ChipCellRenderer;
import tr.cabro.servicio.application.renderer.StatusDotCellRenderer;
import java.util.ArrayList;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.application.renderer.RowParts;
import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.model.dto.PageResult;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.component.table.TableActionColumnSupport;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.application.panels.edit.SupplierEditPanel;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SystemForm(name = "Tedarikçiler", description = "Tüm tedarikçileri listeler")
public class FormSuppliers extends AbstractTableForm {

    private final SupplierService supplierService;
    private GenericTableModel<Supplier> tableModel;
    private TableHeaderFilterSupport<Supplier> headerFilters;

    // --- SAYFALAMA (DB-tabanlı) ---
    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = AppSettings.get().getTables().getSupplierPageSize();
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private PaginationBar paginationBar;

    public FormSuppliers() {
        this.supplierService = ServiceManager.getSupplierService();
    }

    @Override
    protected JComponent createPaginationComponent() {
        paginationBar = new PaginationBar(5, PAGE_SIZE_OPTIONS, pageSize,
                page -> { currentPage = page; refreshTable(); },
                newSize -> {
                    pageSize = newSize;
                    currentPage = 1;
                    AppSettings.get().getTables().setSupplierPageSize(pageSize);
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
    protected String getNewButtonText() {
        return "Yeni Tedarikçi";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/store.svg";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Firma, ilgili kişi, vergi no veya telefon ara…";
    }

    // --- Görünüm sekmeleri ---

    private static final String VIEW_ALL = "all";
    private static final String VIEW_REORDER = "reorder";
    private static final String VIEW_WITH_PARTS = "parts";

    /** Stoğu minimumun altına düşmüş parçası olan tedarikçiler: sipariş verilecek yerler. */
    private static final String REORDER_CONDITION = "id IN (SELECT supplier_id FROM parts WHERE is_deleted = 0 "
            + "AND supplier_id IS NOT NULL AND stock_quantity < min_stock_level)";
    private static final String WITH_PARTS_CONDITION = "id IN (SELECT supplier_id FROM parts WHERE is_deleted = 0 "
            + "AND supplier_id IS NOT NULL)";

    @Override
    protected void initViews() {
        addView(VIEW_ALL, "Tümü");
        addView(VIEW_REORDER, "Sipariş gerekli");
        addView(VIEW_WITH_PARTS, "Parça tedarik edenler");
    }

    @Override
    protected Map<String, ColumnFilterValue> viewFilters(String key) {
        if (VIEW_REORDER.equals(key)) return Map.of("view:reorder", ColumnFilterValue.condition(REORDER_CONDITION));
        if (VIEW_WITH_PARTS.equals(key)) return Map.of("view:parts", ColumnFilterValue.condition(WITH_PARTS_CONDITION));
        return Collections.emptyMap();
    }

    @Override
    protected CompletableFuture<Long> countMatching(Map<String, ColumnFilterValue> filters) {
        return supplierService.searchFilteredPaged(currentSearchTerm, filters, 1, 1).thenApply(PageResult::getTotalItems);
    }

    @Override
    protected void onViewChanged(String key) {
        currentPage = 1;
        super.onViewChanged(key);
    }

    @Override
    protected void refreshStats() {
        CompletableFuture<Long> total = supplierService.searchFilteredPaged(null, Collections.emptyMap(), 1, 1)
                .thenApply(PageResult::getTotalItems);
        CompletableFuture<Long> reorder = supplierService.searchFilteredPaged(null, viewFilters(VIEW_REORDER), 1, 1)
                .thenApply(PageResult::getTotalItems);
        CompletableFuture.allOf(total, reorder).thenRun(() -> SwingUtilities.invokeLater(() -> {
            long r = reorder.join();
            summary.set(
                    ListSummary.Part.strong(total.join() + " tedarikçi"),
                    r > 0 ? ListSummary.Part.meaning(r + " tedarikçiden sipariş gerekli", "Servicio.warningColor")
                            : ListSummary.Part.of("kritik stokta parça yok"));
        })).exceptionally(ex -> ErrorHandler.handle(this, "Tedarikçi özeti yüklenemedi", ex));
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Supplier>> columns = Arrays.asList(
                new ColumnDef<Supplier>("Firma", Supplier.class, s -> s).alignment(SwingConstants.LEADING),
                new ColumnDef<Supplier>("İletişim", Supplier.class, s -> s).alignment(SwingConstants.LEADING),
                new ColumnDef<Supplier>("Adres", String.class, s -> s.getAddress() != null && !s.getAddress().isBlank() ? s.getAddress() : "—").alignment(SwingConstants.LEADING),
                new ColumnDef<Supplier>("Kayıt", String.class, s -> Format.formatDate(s.getCreatedAt()))
                        .alignment(SwingConstants.LEADING).dateRangeFilter("created_at"),
                ColumnDef.<Supplier>actionColumn("")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        headerFilters = installHeaderFilters(columns);

        table.getColumnModel().getColumn(0).setPreferredWidth(280);
        table.getColumnModel().getColumn(0).setMinWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(240);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);

        addSort("NAME", "Ad (A-Z)");
        addSort("NEWEST", "En yeni");
    }

    private static String firmName(Supplier s) {
        return s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName() : s.getName();
    }

    private void configureTableColumns() {
        table.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Supplier>(
                FormSuppliers::firmName,
                s -> {
                    boolean hasFirm = s.getBusinessName() != null && !s.getBusinessName().isBlank();
                    String contact = hasFirm && s.getName() != null && !s.getName().isBlank() ? "İlgili: " + s.getName() : null;
                    String tax = s.getTaxNumber() != null && !s.getTaxNumber().isBlank() ? "VN " + s.getTaxNumber() : null;
                    if (contact != null && tax != null) return contact + "  ·  " + tax;
                    return contact != null ? contact : (tax != null ? tax : "");
                }));
        table.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<Supplier>(
                s -> s.getPhone() != null && !s.getPhone().isBlank() ? Format.formatPhoneNumber(s.getPhone()) : "Telefon yok",
                s -> s.getEmail() != null ? s.getEmail() : ""));
        table.getColumnModel().getColumn(2).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground", 8));
        table.getColumnModel().getColumn(3).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground; font: -1", 8));

        TableActionColumnSupport.install(table, 4, tableModel, new TableActionColumnSupport.Handlers<Supplier>() {
            @Override
            public void onEdit(Supplier supplier) {
                openEditModal(supplier);
            }

            @Override
            public void onDelete(Supplier selectedSupplier) {
                ModalDialog.showModal(FormSuppliers.this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                        Messages.get("confirm.delete.supplier"), Messages.get("confirm.delete.title"),
                        SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
                    if (action == SimpleModalBorder.YES_OPTION) {
                        supplierService.delete(selectedSupplier.getId()).thenRun(() -> {
                            SwingUtilities.invokeLater(() -> {
                                Toasts.show(FormSuppliers.this, Toast.Type.SUCCESS, Messages.get("toast.supplier.deleted"));
                                refreshTable();
                            });
                        }).exceptionally(ex -> ErrorHandler.handle(FormSuppliers.this, "Tedarikçi silinemedi", ex));
                    }
                }));
            }

            @Override
            public void onView(Supplier s) {
                FormManager.showForm(new FormSupplier(s));
            }
        });

    }

    // Tabloyu Güncelleme (Asenkron)
    @Override
    protected String getEmptyStateTitle() { return "Henüz tedarikçi yok"; }

    @Override
    protected String getEmptyStateDescription() { return "Parça aldığınız firmaları ekleyin, alımları onlara bağlayabilirsiniz."; }

    @Override
    protected void onSortChanged(String key) {
        currentPage = 1;
        super.onSortChanged(key);
    }

    @Override
    protected void loadTableData() {
        supplierService.searchFilteredPaged(currentSearchTerm, effectiveFilters(), currentPage, pageSize, getSortKey()).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(result.getItems());
                if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                setResultCount(result.getTotalItems());
                refreshLayout();
            });
        }).exceptionally(e -> {
            Servicio.getLogger().error("Tedarikçi listesi alınamadı: ", e);
            SwingUtilities.invokeLater(() -> {
                Toasts.show(FormSuppliers.this, Toast.Type.ERROR, Messages.get("toast.supplier.listLoadFailed"));
            });
            return null;
        });
    }

    // Yeni Ekleme Modalı (Asenkron)
    @Override
    protected void onNew() {
        final String id = "SupplierNew";
        SupplierEditPanel panel = new SupplierEditPanel(new Supplier());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Yeni Tedarikçi Ekle", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OK_OPTION) {
                        Supplier updated = panel.getData();
                        if (updated == null) {
                            controller.consume();
                            return;
                        }

                        try {
                            updated.setCreatedAt(LocalDateTime.now());

                            // 1. Senkron doğrulama burada çalışır. Hata varsa catch'e düşer, modal kapanmaz.
                            // 2. Doğrulama başarılıysa modal kapanır, arka planda veritabanına yazılır.
                            supplierService.save(updated, false).thenAccept(saved -> {
                                SwingUtilities.invokeLater(() -> {
                                    Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.added", saved.getName()));
                                    refreshTable();
                                });
                            }).exceptionally(ex -> ErrorHandler.handle(this, "Tedarikçi eklenemedi", ex));

                        } catch (ValidationException e) {
                            controller.consume(); // Form doğrulama hatası, modalı açık tut.
                            Toasts.show(this, Toast.Type.ERROR, e.getMessage());
                        } catch (Exception e) {
                            controller.consume();
                            Servicio.getLogger().error("Tedarikçi ekleme beklenmeyen hata", e);
                        }
                    }
                }), id);
    }

    // Düzenleme Modalı (Asenkron)
    protected void openEditModal(Supplier supplier) {
        final String id = "SupplierEdit";
        SupplierEditPanel panel = new SupplierEditPanel(supplier);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Tedarikçi Düzenle", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OK_OPTION) {
                        Supplier updated = panel.getData();
                        if (updated == null) {
                            controller.consume();
                            return;
                        }

                        try {
                            updated.setId(supplier.getId());
                            updated.setCreatedAt(supplier.getCreatedAt());

                            supplierService.save(updated, true).thenAccept(saved -> {
                                SwingUtilities.invokeLater(() -> {
                                    Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", saved.getName()));
                                    refreshTable();
                                });
                            }).exceptionally(ex -> ErrorHandler.handle(this, "Tedarikçi güncellenemedi", ex));

                        } catch (ValidationException e) {
                            controller.consume(); // Form doğrulama hatası
                            Toasts.show(this, Toast.Type.ERROR, e.getMessage());
                        } catch (Exception e) {
                            controller.consume();
                        }
                    }
                }), id);
    }
}