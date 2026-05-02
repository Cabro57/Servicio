package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.ModalDialog;
import tr.cabro.servicio.application.util.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.simple.SimpleMessageModal;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.edit.SupplierEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.util.Ikon;
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

@SystemForm(name = "Tedarikçiler", description = "Tüm tedarikçileri listeler")
public class FormSuppliers extends AbstractTableForm {

    private final SupplierService supplierService;
    private GenericTableModel<Supplier> tableModel;

    public FormSuppliers() {
        this.supplierService = ServiceManager.getSupplierService();
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
                tr.cabro.servicio.application.util.Toast.show(FormSuppliers.this, tr.cabro.servicio.application.util.Toast.Type.ERROR, "İstatistikler çekilirken hata oluştu!");
            });
            return null;
        });

    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Supplier>> columns = Arrays.asList(
                new ColumnDef<>("ID", String.class, supplier -> "S-" + supplier.getId()),
                new ColumnDef<>("Firma İsmi", String.class, Supplier::getBusinessName),
                new ColumnDef<>("İlgili Kişi", String.class, Supplier::getName),
                new ColumnDef<>("İletişim", String.class, s -> Format.formatPhoneNumber(s.getPhone())),
                new ColumnDef<>("Adres", String.class, Supplier::getAddress),
                new ColumnDef<>("Kayıt Tarihi", String.class, s -> Format.formatDate(s.getCreatedAt())),
                new ColumnDef<>("İşlem", String.class, supplier -> "Detay")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);

        configureTableColumns();
    }

    private void configureTableColumns() {
        Integer[] columnAlignments = {
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.CENTER
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel) c).setHorizontalAlignment(SwingConstants.LEADING);
                ((JLabel) c).putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: +1");
                return c;
            }
        });

        table.getColumnModel().getColumn(3).setCellRenderer(new MultiLineTableCellRenderer<Supplier>(
                supplier -> Format.formatPhoneNumber(supplier.getPhone()),
                Supplier::getEmail

        ));

        table.getColumnModel().getColumn(6).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(6).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                System.out.println(tableModel.getItemAt(modelRow).getPhone());
                openEditModal(tableModel.getItemAt(modelRow));
            }

            @Override
            public void onDelete(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                Supplier selectedSupplier = tableModel.getItemAt(modelRow);

                ModalDialog.showModal(FormSuppliers.this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                        "Tedarikçiyi silmek istediğinizden emin misiniz?", "Silme Onayı",
                        SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
                    if (action == SimpleModalBorder.YES_OPTION) {
                        supplierService.delete(selectedSupplier.getId()).thenRun(() -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(FormSuppliers.this, Toast.Type.SUCCESS, "Tedarikçi silindi.");
                                refreshTable();
                            });
                        }).exceptionally(ex -> {
                            Servicio.getLogger().error("Silme hatası ID: {}", selectedSupplier.getId(), ex);
                            SwingUtilities.invokeLater(() -> Toast.show(FormSuppliers.this, Toast.Type.ERROR, "Silinemedi: " + ex.getCause().getMessage()));
                            return null;
                        });
                    }
                }));
            }

            @Override
            public void onView(int row) {

            }
        }));

        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(6).setMaxWidth(180);
        table.getColumnModel().getColumn(6).setMinWidth(120);
    }

    // Tabloyu Güncelleme (Asenkron)
    @Override
    protected void refreshTable() {
        supplierService.getAll().thenAccept(allSuppliers -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(allSuppliers);
                refreshStats(); // İstatistikleri veriler gelince güncelle
            });
        }).exceptionally(e -> {
            Servicio.getLogger().error("Tedarikçi listesi alınamadı: ", e);
            SwingUtilities.invokeLater(() -> {
                Toast.show(FormSuppliers.this, Toast.Type.ERROR, "Tedarikçi listesi alınamadı!");
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

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "Yeni Tedarikçi Ekle", options,
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
                                    Toast.show(this, Toast.Type.SUCCESS, saved.getName() + " başarıyla eklendi.");
                                    refreshTable();
                                });
                            }).exceptionally(ex -> {
                                Servicio.getLogger().error("Tedarikçi DB ekleme hatası", ex);
                                SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Kayıt Hatası: " + ex.getCause().getMessage()));
                                return null;
                            });

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

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "Tedarikçi Düzenle", options,
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
                                    Toast.show(this, Toast.Type.SUCCESS, saved.getName() + " başarıyla güncellendi.");
                                    refreshTable();
                                });
                            }).exceptionally(ex -> {
                                SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Güncelleme Hatası: " + ex.getCause().getMessage()));
                                return null;
                            });

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