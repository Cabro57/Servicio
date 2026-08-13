package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.table.PaginationBar;
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
        return "Yeni Tedarikçi Ekle";
    }

    @Override
    protected String getTableTitleText() {
        return "Tüm Tedarikçiler";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Firma, ilgili kişi veya e-posta ara...";
    }

    @Override
    protected void initCards() {
        cardBox.addCardItem(new Ikon("icons/package-check.svg", 0.7f), "Toplam Tedarikçi");
    }

    @Override
    protected void refreshStats() {
        supplierService.getAll().thenAccept(suppliers -> {
            long supplierCount = suppliers.size();
            SwingUtilities.invokeLater(() -> {
                cardBox.setValueAt(0, String.valueOf(supplierCount), "Tüm kayıtlılar", "", true);
            });
        }).exceptionally(ex -> {
            Servicio.getLogger().error("İstatistikler çekilirken hata oluştu!", ex);
            SwingUtilities.invokeLater(() -> {
                Toast.show(FormSuppliers.this, Toast.Type.ERROR, Messages.get("toast.stats.loadFailed"));
            });
            return null;
        });

    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Supplier>> columns = Arrays.asList(
                new ColumnDef<Supplier>("ID", String.class, supplier -> "S-" + supplier.getId()).alignment(SwingConstants.LEADING),
                new ColumnDef<Supplier>("Firma İsmi", String.class, Supplier::getBusinessName).alignment(SwingConstants.LEADING),
                new ColumnDef<Supplier>("İlgili Kişi", String.class, Supplier::getName).alignment(SwingConstants.LEADING),
                new ColumnDef<Supplier>("İletişim", String.class, s -> Format.formatPhoneNumber(s.getPhone())).alignment(SwingConstants.LEADING),
                new ColumnDef<Supplier>("Adres", String.class, Supplier::getAddress).alignment(SwingConstants.LEADING),
                new ColumnDef<Supplier>("Kayıt Tarihi", String.class, s -> Format.formatDate(s.getCreatedAt()))
                        .alignment(SwingConstants.LEADING).dateRangeFilter("created_at"),
                ColumnDef.<Supplier>actionColumn("İşlem")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        headerFilters = installHeaderFilters(columns);
    }

    private void configureTableColumns() {
        table.getColumnModel().getColumn(0).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground; font: +1"));

        table.getColumnModel().getColumn(3).setCellRenderer(new MultiLineTableCellRenderer<Supplier>(
                supplier -> Format.formatPhoneNumber(supplier.getPhone()),
                Supplier::getEmail

        ));

        TableActionColumnSupport.install(table, 6, tableModel, new TableActionColumnSupport.Handlers<Supplier>() {
            @Override
            public void onEdit(Supplier supplier) {
                System.out.println(supplier.getPhone());
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
                                Toast.show(FormSuppliers.this, Toast.Type.SUCCESS, Messages.get("toast.supplier.deleted"));
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

        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(6).setMaxWidth(180);
        table.getColumnModel().getColumn(6).setMinWidth(120);
    }

    // Tabloyu Güncelleme (Asenkron)
    @Override
    protected void refreshTable() {
        Map<String, ColumnFilterValue> filters = headerFilters != null ? headerFilters.getActiveFilters() : java.util.Collections.emptyMap();

        supplierService.searchFilteredPaged(currentSearchTerm, filters, currentPage, pageSize).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(result.getItems());
                if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                refreshStats(); // İstatistikleri veriler gelince güncelle
                refreshLayout();
            });
        }).exceptionally(e -> {
            Servicio.getLogger().error("Tedarikçi listesi alınamadı: ", e);
            SwingUtilities.invokeLater(() -> {
                Toast.show(FormSuppliers.this, Toast.Type.ERROR, Messages.get("toast.supplier.listLoadFailed"));
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
                                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.added", saved.getName()));
                                    refreshTable();
                                });
                            }).exceptionally(ex -> ErrorHandler.handle(this, "Tedarikçi eklenemedi", ex));

                        } catch (ValidationException e) {
                            controller.consume(); // Form doğrulama hatası, modalı açık tut.
                            Toast.show(this, Toast.Type.ERROR, e.getMessage());
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
                                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", saved.getName()));
                                    refreshTable();
                                });
                            }).exceptionally(ex -> ErrorHandler.handle(this, "Tedarikçi güncellenemedi", ex));

                        } catch (ValidationException e) {
                            controller.consume(); // Form doğrulama hatası
                            Toast.show(this, Toast.Type.ERROR, e.getMessage());
                        } catch (Exception e) {
                            controller.consume();
                        }
                    }
                }), id);
    }
}