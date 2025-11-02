package tr.cabro.servicio.forms;

import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.simple.SimpleMessageModal;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.edit.PartEditPanel;
import tr.cabro.servicio.application.renderer.AlignedRenderer;
import tr.cabro.servicio.application.renderer.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.tablemodal.PartTableModel;
import tr.cabro.servicio.forms.base.AbstractTableForm;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.List;

@SystemForm(name = "Parçalar", description = "Yeni parçalar eklemek ve düzenlemek için kullanılabilir")
public class FormParts extends AbstractTableForm<Part> {

    private PartService service;

    @Override
    protected void refreshTable() {
        service = ServiceManager.getPartService();

        setTableModel(new PartTableModel(service.getAll()));

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.CENTER,
                SwingConstants.TRAILING,
                SwingConstants.TRAILING,
                SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));
        table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(table, 0));
        table.getColumnModel().getColumn(5).setCellRenderer(new TooltipCellRenderer());
        table.getColumnModel().getColumn(6).setCellRenderer(new AlignedRenderer(table, 6, SwingConstants.CENTER));

        table.getColumnModel().getColumn(0).setMaxWidth(50);   // SELECT (checkbox)
        table.getColumnModel().getColumn(1).setMinWidth(150);   // Barkod
        table.getColumnModel().getColumn(2).setPreferredWidth(100);  // Marka
        table.getColumnModel().getColumn(3).setPreferredWidth(120);  // Ürün Adı
        table.getColumnModel().getColumn(4).setPreferredWidth(70);  // Cihaz Türü
        table.getColumnModel().getColumn(5).setPreferredWidth(120);  // Uyumlu Modeller
        table.getColumnModel().getColumn(6).setPreferredWidth(50);  // Stok
        table.getColumnModel().getColumn(7).setPreferredWidth(70);  // Alış Fiyatı
        table.getColumnModel().getColumn(8).setPreferredWidth(70);  // Satış Fiyatı
        table.getColumnModel().getColumn(9).setPreferredWidth(70);  // Alış Tarihi

    }

    @Override
    protected void onNew() {
        final String id = "PartNew";
        PartEditPanel panel = new PartEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Parça Formu", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                panel.clearForm();

                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Part updated = panel.getDataIfValid();
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
        List<Part> selected = ((PartTableModel) table.getModel()).getSelectedProducts();

        if (selected.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen düzenlemek için bir parça seçin.");
            return;
        }
        if (selected.size() > 1) {
            Toast.show(this, Toast.Type.INFO, "Düzenlemek için sadece 1 parça seçin.");
            return;
        }

        final String id = "PartEdit";
        Part part = selected.get(0);
        PartEditPanel panel = new PartEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Parça Formu", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                panel.populateFormWith(part);

                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Part updated = panel.getDataIfValid();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                updated.setBarcode(part.getBarcode());
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
        List<Part> selects = ((PartTableModel) table.getModel()).getSelectedProducts();

        if (selects.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen silmek için bir parça seçin.");
            return;
        }

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Seçilen " + selects.size() + " parçayı silmek istediğinizden emin misiniz?", "Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
            if (action == 0) {
                int count = 0;
                for (Part part : selects) {
                    if (service.delete(part.getBarcode())) {
                        count++;
                    }
                }
                Servicio.getLogger().info("{}", count);
                Toast.show(this, Toast.Type.SUCCESS, "Başarılı şekilde " + count + " adet parça silindi.");
                refreshTable();
            }

        }));
    }
}
