package tr.cabro.servicio.forms;

import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.simple.SimpleMessageModal;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.forms.base.AbstractTableForm;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@SystemForm(name = "Müşteriler", description = "Müşteri listesini gösterir.")
public class FormCustomers extends AbstractTableForm<Customer> {

    private final CustomerService customerService;
    private GenericTableModel<Customer> customerTableModel;

    public FormCustomers() {
        this.customerService = ServiceManager.getCustomerService();
    }

    @Override
    protected void onNew() {
        final String id = "CustomerNew";
        CustomerEditPanel panel = new CustomerEditPanel(new Customer());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Yeni Müşteri Ekle", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                //panel.clearForm();
                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Customer updated = panel.getData();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                try {
                                    updated.setCreatedAt(LocalDateTime.now());
                                    customerService.save(updated, false);

                                    Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla eklendi.");
                                    refreshTable();

                                } catch (Exception e) {
                                    controller.consume();
                                    Toast.show(this, Toast.Type.ERROR, "Hata: " + e.getMessage());
                                    Servicio.getLogger().error("Müşteri ekleme hatası", e.getMessage());
                                }
                            }
                        })
                , id);
    }

    @Override
    protected void onEdit() {
        List<Customer> selected = getSelectedCustomers();

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
        CustomerEditPanel panel = new CustomerEditPanel(customer);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Müşteri Düzenle", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                //panel.populateFormWith(customer);
                            } else if (action == SimpleModalBorder.OK_OPTION) {

                                Customer updated = panel.getData();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                try {
                                    updated.setId(customer.getId());
                                    updated.setCreatedAt(customer.getCreatedAt());

                                    customerService.save(updated, true);

                                    Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla güncellendi.");
                                    refreshTable();

                                } catch (Exception e) {
                                    controller.consume();
                                    Toast.show(this, Toast.Type.ERROR, "Güncelleme Hatası: " + e.getMessage());
                                    Servicio.getLogger().error("Müşteri güncelleme hatası", e);
                                }
                            }
                        })
                , id);
    }

    @Override
    protected void onDelete() {
        List<Customer> cs = getSelectedCustomers();

        if (cs.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen silmek için bir müşteri seçin.");
            return;
        }

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Seçilen " + cs.size() + " müşteriyi silmek istediğinizden emin misiniz?", "Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
            if (action == 0) { // YES
                int successCount = 0;
                int errorCount = 0;

                for (Customer c : cs) {
                    try {
                        customerService.delete(c.getId());
                        successCount++;
                    } catch (Exception e) {
                        errorCount++;
                        Servicio.getLogger().error("Silme hatası ID: " + c.getId(), e);
                    }
                }

                if (successCount > 0) {
                    Toast.show(this, Toast.Type.SUCCESS, successCount + " adet müşteri silindi.");
                }

                if (errorCount > 0) {
                    Toast.show(this, Toast.Type.WARNING, errorCount + " adet müşteri silinemedi (Aktif işlem olabilir).");
                }

                refreshTable();
            }
        }));
    }

    @Override
    protected void refreshTable() {
        try {
            List<Customer> allCustomers = customerService.getAll();

            if (customerTableModel == null) {
                List<ColumnDef<Customer>> columns = Arrays.asList(
                        new ColumnDef<>("#", Integer.class, Customer::getId),
                        new ColumnDef<>("Ad Soyad", String.class, c -> c.getName() + " " + c.getSurname()),
                        new ColumnDef<>("Firma İsmi", String.class, Customer::getBusinessName),
                        new ColumnDef<>("Kimlik No.", String.class, Customer::getIdNo),
                        new ColumnDef<>("Adres", String.class, Customer::getAddress),
                        new ColumnDef<>("Telefon 1", String.class, c -> Format.formatPhoneNumber(c.getPhoneNumber1())),
                        new ColumnDef<>("Tip", String.class, Customer::getType),
                        new ColumnDef<>("Kayıt Tarihi", String.class, c -> Format.formatDate(c.getCreatedAt()))
                );
                customerTableModel = new GenericTableModel<>(columns);
                setTableModel(customerTableModel);
                configureTable();
            } else {
                customerTableModel.setData(allCustomers);
            }

        } catch (Exception e) {
            Toast.show(this, Toast.Type.ERROR, "Veriler yüklenemedi: " + e.getMessage());
            Servicio.getLogger().error("Tablo yenileme hatası", e);
        }
    }

    /** Tabloda seçili satırlardaki müşteri nesnelerini döndürür. */
    private List<Customer> getSelectedCustomers() {
        return customerTableModel.getSelectedItems(table.getSelectedRows());
    }

    private void configureTable() {
        Integer[] columnAlignments = {
                SwingConstants.CENTER, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));
        if (table.getColumnCount() > 0) {
            table.getColumnModel().getColumn(0).setCellRenderer(new AlignedRenderer(table, 0, SwingConstants.CENTER));
            table.getColumnModel().getColumn(0).setMaxWidth(40);

            table.getColumnModel().getColumn(1).setCellRenderer(new ProfileTableRenderer(table));
            table.getColumnModel().getColumn(1).setPreferredWidth(150);

            if (table.getColumnCount() > 6) {
                table.getColumnModel().getColumn(6).setCellRenderer(new CustomerTypeTableRenderer());
            }
        }
    }
}