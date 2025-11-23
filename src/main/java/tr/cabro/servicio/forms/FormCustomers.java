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

    private final CustomerService customerService;

    public FormCustomers() {
        // Servisi constructor'da almak daha güvenlidir
        this.customerService = ServiceManager.getCustomerService();
        // Tabloyu ilk açılışta doldur
        // refreshTable(); // AbstractTableForm constructor'ında çağrılıyorsa buraya gerek yok
    }

    @Override
    protected void onNew() {
        final String id = "CustomerNew";
        CustomerEditPanel panel = new CustomerEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0), // "Tamam" yerine "Kaydet" daha anlamlı olabilir
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Yeni Müşteri Ekle", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                panel.clearForm();
                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                // 1. Panelden veriyi al ve validasyonu kontrol et
                                Customer updated = panel.getData();
                                if (updated == null) {
                                    controller.consume(); // Validasyon hatası varsa diyaloğu kapatma
                                    return;
                                }

                                // 2. İşlemi yap (TRY-CATCH İLE)
                                try {
                                    updated.setCreatedAt(LocalDateTime.now());

                                    // Artık boolean dönmüyor, hata varsa exception fırlatıyor
                                    customerService.save(updated, false);

                                    // Hata yoksa burası çalışır
                                    Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla eklendi.");
                                    refreshTable();
                                    // controller.consume() çağırmadığımız için diyalog otomatik kapanır.

                                } catch (Exception e) {
                                    // 3. Hata varsa yakala ve kullanıcıya göster
                                    controller.consume(); // Hata olduğu için diyaloğu açık tut

                                    // Service veya DAO'dan gelen gerçek hata mesajını gösteriyoruz
                                    Toast.show(this, Toast.Type.ERROR, "Hata: " + e.getMessage());
                                    Servicio.getLogger().error("Müşteri ekleme hatası", e.getMessage());
                                }
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
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Müşteri Düzenle", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                panel.populateFormWith(customer);
                            } else if (action == SimpleModalBorder.OK_OPTION) {

                                Customer updated = panel.getData();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                try {
                                    // ID'yi koru
                                    updated.setId(customer.getId());
                                    // Created_at tarihini koru (değişmemeli)
                                    updated.setCreatedAt(customer.getCreatedAt());

                                    // Service çağrısı
                                    customerService.save(updated, true);

                                    Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla güncellendi.");
                                    refreshTable();

                                } catch (Exception e) {
                                    controller.consume(); // Diyaloğu kapatma
                                    Toast.show(this, Toast.Type.ERROR, "Güncelleme Hatası: " + e.getMessage());
                                    Servicio.getLogger().error("Müşteri güncelleme hatası", e);
                                }
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
            if (action == 0) { // YES
                int successCount = 0;
                int errorCount = 0;

                for (Customer c : cs) {
                    try {
                        // Yeni yapıda delete metodu void döner, hata varsa exception fırlatır
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
        // Veritabanı bağlantı hatası ihtimaline karşı try-catch
        try {
            List<Customer> allCustomers = customerService.getAll();
            setTableModel(new CustomerTableModel(allCustomers));
            configureTable(); // Sütun ayarlarını ayrı bir metoda aldım, daha temiz durur
        } catch (Exception e) {
            Toast.show(this, Toast.Type.ERROR, "Veriler yüklenemedi: " + e.getMessage());
            Servicio.getLogger().error("Tablo yenileme hatası", e);
        }
    }

    private void configureTable() {
        Integer[] columnAlignments = {
                SwingConstants.CENTER, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));
        // Index kontrolleri eklenebilir (IndexOutOfBounds yememek için)
        if (table.getColumnCount() > 0) {
            table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(table, 0));
            table.getColumnModel().getColumn(0).setMaxWidth(50);

            table.getColumnModel().getColumn(1).setCellRenderer(new AlignedRenderer(table, 1, SwingConstants.CENTER));
            table.getColumnModel().getColumn(1).setMaxWidth(40);

            table.getColumnModel().getColumn(2).setCellRenderer(new ProfileTableRenderer(table));
            table.getColumnModel().getColumn(2).setPreferredWidth(150);

            // Diğer sütun genişlikleri...
            if (table.getColumnCount() > 7) {
                table.getColumnModel().getColumn(7).setCellRenderer(new CustomerTypeTableRenderer());
            }
        }
    }
}