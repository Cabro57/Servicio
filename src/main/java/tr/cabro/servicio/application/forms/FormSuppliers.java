package tr.cabro.servicio.forms;

import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.simple.SimpleMessageModal;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.edit.SupplierEditPanel;
import tr.cabro.servicio.application.renderer.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.renderer.ProfileTableRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.forms.base.AbstractTableForm;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@SystemForm(name = "Tedarikçiler", description = "Tüm tedarikçileri listeler")
public class FormSuppliers extends AbstractTableForm<Supplier> {

    private final SupplierService service;
    private GenericTableModel<Supplier> supplierTableModel;

    public FormSuppliers() {
        this.service = ServiceManager.getSupplierService();
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Supplier>> columns = Arrays.asList(
                new ColumnDef<>("Firma İsmi", String.class, Supplier::getBusinessName),
                new ColumnDef<>("Ad Soyad", String.class, Supplier::getName),
                new ColumnDef<>("Telefon", String.class, s -> Format.formatPhoneNumber(s.getPhone())),
                new ColumnDef<>("Adres", String.class, Supplier::getAddress),
                new ColumnDef<>("Kayıt Tarihi", String.class, s -> Format.formatDate(s.getCreated_at()))
        );
        supplierTableModel = new GenericTableModel<>(columns);
        setTableModel(supplierTableModel);
        configureTableColumns();
    }

    @Override
    protected void refreshTable() {
        try {
            List<Supplier> allSuppliers = service.getAll();

            supplierTableModel.setData(allSuppliers);


        } catch (Exception e) {
            Toast.show(this, Toast.Type.ERROR, "Tedarikçi listesi alınamadı: " + e.getMessage());
        }
    }

    private void configureTableColumns() {
        Integer[] columnAlignments = {
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        if (table.getColumnCount() > 1) {
            table.getColumnModel().getColumn(1).setCellRenderer(new ProfileTableRenderer(table));
            table.getColumnModel().getColumn(1).setPreferredWidth(150);
        }
    }

    /** Tabloda seçili satırlardaki tedarikçi nesnelerini döndürür. */
    private List<Supplier> getSelectedSuppliers() {
        return supplierTableModel.getSelectedItems(table.getSelectedRows());
    }

    @Override
    protected void onNew() {
        final String id = "SupplierNew";
        SupplierEditPanel panel = new SupplierEditPanel(new Supplier());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Yeni Tedarikçi Ekle", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                //panel.clearForm();
                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Supplier updated = panel.getData();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                try {
                                    updated.setCreated_at(LocalDateTime.now());
                                    service.save(updated, false);

                                    Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla eklendi.");
                                    refreshTable();

                                } catch (Exception e) {
                                    controller.consume();
                                    Toast.show(this, Toast.Type.ERROR, "Hata: " + e.getMessage());
                                    Servicio.getLogger().error("Tedarikçi ekleme hatası", e);
                                }
                            }
                        })
                , id);
    }

    @Override
    protected void onEdit() {
        List<Supplier> selected = getSelectedSuppliers();

        if (selected.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen düzenlemek için bir tedarikçi seçin.");
            return;
        }
        if (selected.size() > 1) {
            Toast.show(this, Toast.Type.INFO, "Düzenlemek için sadece 1 tedarikçi seçin.");
            return;
        }

        final String id = "SupplierEdit";
        Supplier supplier = selected.get(0);
        SupplierEditPanel panel = new SupplierEditPanel(supplier);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Tedarikçi Düzenle", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                //panel.populateFormWith(supplier);
                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Supplier updated = panel.getData();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                try {
                                    updated.setId(supplier.getId());
                                    updated.setCreated_at(supplier.getCreated_at());

                                    service.save(updated, true);

                                    Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla güncellendi.");
                                    refreshTable();

                                } catch (Exception e) {
                                    controller.consume();
                                    Toast.show(this, Toast.Type.ERROR, "Güncelleme Hatası: " + e.getMessage());
                                    Servicio.getLogger().error("Tedarikçi güncelleme hatası", e);
                                }
                            }
                        })
                , id);
    }

    @Override
    protected void onDelete() {
        List<Supplier> cs = getSelectedSuppliers();

        if (cs.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen silmek için bir tedarikçi seçin.");
            return;
        }

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Seçilen " + cs.size() + " tedarikçiyi silmek istediğinizden emin misiniz?", "Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
            if (action == 0) {
                int successCount = 0;
                int errorCount = 0;

                for (Supplier s : cs) {
                    try {
                        service.delete(s.getId());
                        successCount++;
                    } catch (Exception e) {
                        errorCount++;
                        Servicio.getLogger().error("Silme hatası ID: " + s.getId(), e);
                    }
                }

                if (successCount > 0) Toast.show(this, Toast.Type.SUCCESS, successCount + " adet tedarikçi silindi.");
                if (errorCount > 0) Toast.show(this, Toast.Type.WARNING, errorCount + " adet tedarikçi silinemedi.");

                refreshTable();
            }
        }));
    }
}