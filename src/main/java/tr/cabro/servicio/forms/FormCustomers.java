package tr.cabro.servicio.forms;

import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.simple.SimpleMessageModal;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.tablemodal.CustomerTableModel;
import tr.cabro.servicio.forms.base.AbstractTableForm;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.List;

@SystemForm(name = "Müşteriler", description = "Müşteri listesini gösterir.")
public class FormCustomers extends AbstractTableForm<Customer> {

    private CustomerService customerService;

    @Override
    protected void onNew() {
        final String id = "CustomerNew";
        CustomerEditPanel panel = new CustomerEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Müşteri Formu", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                panel.clearForm();

                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Customer updated = panel.getDataIfValid();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                updated.setCreated_at(LocalDateTime.now());
                                boolean added = customerService.save(updated, false);

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
        List<Customer> selected = ((CustomerTableModel) table.getModel()).getSelectedCustomers();

        if (selected.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen düzenlemek için bir müşteri seçin.");
            return;
        }
        if (selected.size() > 1) {
            Toast.show(this, Toast.Type.INFO, "Düzenlemek için sadece 1 kişi seçin.");
            return;
        }

        final String id = "CustomerEdit";
        Customer customer = selected.get(0);
        CustomerEditPanel panel = new CustomerEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Müşteri Formu", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                panel.populateFormWith(customer);

                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Customer updated = panel.getDataIfValid();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                updated.setId(customer.getId());
                                boolean added = customerService.save(updated, true);

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
        List<Customer> cs = ((CustomerTableModel) table.getModel()).getSelectedCustomers();

        if (cs.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen silmek için bir müşteri seçin.");
            return;
        }

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Seçilen " + cs.size() + " müşteriyi silmek istediğinizden emin misiniz?", "Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
                    if (action == 0) {
                        int count = 0;
                        for (Customer c : cs) {
                            if (customerService.delete(c.getId())) {
                                count++;
                            }
                        }
                        Servicio.getLogger().info("{}", count);
                        Toast.show(this, Toast.Type.SUCCESS, "Başarılı şekilde " + count + " adet müşteri silindi.");
                        refreshTable();
                    }

        }));
    }

    @Override
    protected void refreshTable() {
        customerService = ServiceManager.getCustomerService();

        setTableModel(new CustomerTableModel(customerService.getAll()));

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));
        table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(table, 0));
        table.getColumnModel().getColumn(1).setCellRenderer(new AlignedRenderer(table, 1, SwingConstants.CENTER));
        table.getColumnModel().getColumn(2).setCellRenderer(new ProfileTableRenderer(table));
        table.getColumnModel().getColumn(7).setCellRenderer(new CustomerTypeTableRenderer());

        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setMaxWidth(40);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(180);
        table.getColumnModel().getColumn(6).setPreferredWidth(100);
        table.getColumnModel().getColumn(7).setPreferredWidth(80);
        table.getColumnModel().getColumn(8).setPreferredWidth(80);

    }

}
