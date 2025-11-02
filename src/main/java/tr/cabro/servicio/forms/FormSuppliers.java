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
import tr.cabro.servicio.application.tablemodal.SupplierTableModel;
import tr.cabro.servicio.forms.base.AbstractTableForm;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.SupplierService;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.List;

@SystemForm(name = "Tedarikçiler", description = "Tüm tedarikçileri listeler")
public class FormSuppliers extends AbstractTableForm<Supplier> {

    private SupplierService service;

    @Override
    protected void onNew() {
        final String id = "SupplierNew";
        SupplierEditPanel panel = new SupplierEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Tedarikçi Formu", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                panel.clearForm();

                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Supplier updated = panel.getDataIfValid();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                updated.setCreated_at(LocalDateTime.now());
                                boolean added = service.save(updated, false);

                                if (added) {
                                    Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla eklendi.");
                                } else {
                                    Toast.show(this, Toast.Type.WARNING, updated.getName() + " zaten mevcut.");
                                }

                                refreshTable();
                            }
                        })
                , id);
    }

    @Override
    protected void onEdit() {
        List<Supplier> selected = ((SupplierTableModel) table.getModel()).getSelectedSuppliers();

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
        SupplierEditPanel panel = new SupplierEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Tedarikçi Formu", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                panel.populateFormWith(supplier);

                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Supplier updated = panel.getDataIfValid();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                updated.setId(supplier.getId());
                                boolean added = service.save(updated, true);

                                if (added) {
                                    Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla güncellendi.");
                                } else {
                                    Toast.show(this, Toast.Type.WARNING, updated.getName() + " zaten mevcut.");
                                }

                                refreshTable();
                            }
                        })
                , id);
    }

    @Override
    protected void onDelete() {
        List<Supplier> cs = ((SupplierTableModel) table.getModel()).getSelectedSuppliers();

        if (cs.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen silmek için bir tedarikçi seçin.");
            return;
        }

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Seçilen " + cs.size() + " tedarikçiyi silmek istediğinizden emin misiniz?", "Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
            if (action == 0) {
                int count = 0;
                for (Supplier c : cs) {
                    if (service.delete(c.getId())) {
                        count++;
                    }
                }
                Servicio.getLogger().info("{}", count);
                Toast.show(this, Toast.Type.SUCCESS, "Başarılı şekilde " + count + " adet tedarikçi silindi.");
                refreshTable();
            }

        }));
    }

    @Override
    protected void refreshTable() {
        service = ServiceManager.getSupplierService();

        setTableModel(new SupplierTableModel(service.getAll()));

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));
        table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(table, 0));
        table.getColumnModel().getColumn(2).setCellRenderer(new ProfileTableRenderer(table));

        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(180);
        table.getColumnModel().getColumn(5).setPreferredWidth(80);
    }
}
