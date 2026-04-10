package tr.cabro.servicio.application.forms;

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
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SystemForm(name = "Müşteriler", description = "Müşteri listesini gösterir.")
public class FormCustomers extends AbstractTableForm<Customer> {

    private final CustomerService customerService;
    private GenericTableModel<Customer> tableModel;

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

                                customerService.save(updated, false).thenAccept(saved -> {
                                    SwingUtilities.invokeLater(() -> {
                                        Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla eklendi.");
                                        refreshTable();
                                    });
                                }).exceptionally(ex -> {
                                    SwingUtilities.invokeLater(() -> {
                                        controller.consume();
                                        Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getMessage());
                                        Servicio.getLogger().error("Müşteri ekleme hatası", ex.getMessage());
                                    });
                                    return null;
                                });
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

                                updated.setId(customer.getId());
                                updated.setCreatedAt(customer.getCreatedAt());

                                customerService.save(updated, true).thenAccept(saved -> {
                                    SwingUtilities.invokeLater(() -> {
                                        Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla güncellendi.");
                                        refreshTable();
                                    });
                                }).exceptionally(ex -> {
                                    SwingUtilities.invokeLater(() -> {
                                        controller.consume();
                                        Toast.show(this, Toast.Type.ERROR, "Güncelleme Hatası: " + ex.getMessage());
                                        Servicio.getLogger().error("Müşteri güncelleme hatası", ex);
                                    });
                                    return null;
                                });
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

            List<Integer> idsToDelete = cs.stream().map(Customer::getId).collect(Collectors.toList());

            customerService.deleteMultiple(idsToDelete).thenAccept(v -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, cs.size() + " adet müşteri silindi.");
                    refreshTable();
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.ERROR, "Silme işlemi başarısız oldu.");
                });
                return null;
            });
        }));
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Customer>> columns = Arrays.asList(
                new ColumnDef<>("#", Integer.class, Customer::getId),
                new ColumnDef<>("Ad Soyad", String.class, c -> c.getName() + " " + c.getSurname()),
                new ColumnDef<>("Firma İsmi", String.class, Customer::getBusinessName),
                new ColumnDef<>("Kimlik No.", String.class, Customer::getIdNo),
                new ColumnDef<>("Adres", String.class, Customer::getAddress),
                new ColumnDef<>("Telefon 1", String.class, c -> Format.formatPhoneNumber(c.getPhoneNumber1())),
                new ColumnDef<>("Tip", CustomerType.class, Customer::getType),
                new ColumnDef<>("Kayıt Tarihi", String.class, c -> Format.formatDate(c.getCreatedAt()))
        );

        tableModel = new GenericTableModel<>(columns);
        table.setModel(tableModel);
        configureTable();
    }

    @Override
    protected void refreshTable() {
        customerService.getAll().thenAccept(allCustomers -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(allCustomers);
            });
        }).exceptionally(ex -> {
            // Arka planda bir veritabanı veya bağlantı hatası olursa:
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.ERROR, "Veriler yüklenemedi: " + ex.getMessage());
                Servicio.getLogger().error("Tablo yenileme hatası", ex);
            });
            return null;
        });
    }

    /** Tabloda seçili satırlardaki müşteri nesnelerini döndürür. */
    private List<Customer> getSelectedCustomers() {
        return tableModel.getSelectedItems(table.getSelectedRows());
    }

    private void configureTable() {
        Integer[] columnAlignments = {
                SwingConstants.CENTER, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));
        table.getColumnModel().getColumn(0).setCellRenderer(new AlignedRenderer(table, 0, SwingConstants.CENTER));
        table.getColumnModel().getColumn(0).setMaxWidth(40);

        table.getColumnModel().getColumn(1).setCellRenderer(new ProfileTableRenderer(table));
        table.getColumnModel().getColumn(1).setPreferredWidth(150);

        table.getColumnModel().getColumn(6).setCellRenderer(new UniversalVisualizableRenderer(SwingConstants.LEFT, 16));
    }
}