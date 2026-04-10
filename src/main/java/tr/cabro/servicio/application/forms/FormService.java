package tr.cabro.servicio.forms;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.simple.SimpleMessageModal;
import raven.modal.system.AllForms;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.listeners.ServiceEditListener;
import tr.cabro.servicio.application.panels.ProcessSelectedPanel;
import tr.cabro.servicio.application.panels.service.*;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FormService extends Form {

    private Service service;
    private final RepairService repairService;

    public FormService(Service service) {
        this.service = service;
        repairService = ServiceManager.getRepairService();
        formInit();
        setService(service);
    }


    // Silme AllForms sınıfı kullanıyor
    public FormService() {
        this.repairService = ServiceManager.getRepairService();
        this.service = new Service(); // Boş bir servis nesnesi oluştur
        // formInit() metodunu burada çağırmana gerek yok, AllForms sınıfı zaten onu çağırıyor.
    }


    @Override
    public void formInit() {
        initComponent();

        ServiceEditListener serviceEditListener = new ServiceEditListener() {
            @Override
            public void onPartChange(double price) {
                // Parça panelinde fiyat değiştiğinde fiyat paneline gönder
                price_info.setMaterialCost(price);
            }

            @Override
            public void onProcessAdded(String name, double price) {
                price_info.addLaborCost(price);
                fault_process_info.appendAction(name);
            }

            @Override
            public void onProcessAdded(tr.cabro.servicio.model.Process process) {
                fault_process_info.appendAction(process.getName());
                price_info.addLaborCost(process.getPrice());
            }

            @Override
            public void onStatusChanged(String status) {
                // Şimdilik boş
            }

            @Override
            public void onDataChanged() {
                // Herhangi bir panelde klavyeye basıldığında veya seçim yapıldığında
                // Güncelle butonunu aktif et.
                if (service != null && service.getId() > 0) {
                    update_button.setEnabled(true);
                }
            }

            @Override
            public void requestRefresh() {
                // Yenileme talebi gelirse mevcut servisi tekrar çek
                if (service != null && service.getId() > 0) {
                    setService(service);
                }
            }

            @Override
            public void onPartAdded(AddedPart part) {

            }
        };

        customer_info.setServicePanelListener(serviceEditListener);
        device_info.setServicePanelListener(serviceEditListener);
        price_info.setServicePanelListener(serviceEditListener);
        warranty_info.setServicePanelListener(serviceEditListener);
        fault_process_info.setServicePanelListener(serviceEditListener);
        part_notes_info.setServicePanelListener(serviceEditListener);
        status_info.setServicePanelListener(serviceEditListener);

        save_button.addActionListener(e -> saveService());
        update_button.addActionListener(e -> updateService());
        delete_button.addActionListener(e -> deleteService());
        deliver_button.addActionListener(e -> deliverService());

        fault_process_info.action_taken_button.addActionListener(e -> onActionTaken());
    }

    @Override
    public void formRefresh() {
        setService(new Service()); // clearForm yerine direkt yeni servis veriyoruz
    }

    public void setService(@NonNull Service service) {
        this.service = service;

        // 1. YENİ SERVİS SENARYOSU
        if (service.getId() <= 0) {
            this.service.setAddedParts(new ArrayList<>());
            this.service.setTotalPartsCost(0.0);

            SwingUtilities.invokeLater(() -> {
                bindAllPanels();
                save_button.setEnabled(true);
                update_button.setEnabled(false);
                deliver_button.setEnabled(false);
                updateTitle();
            });
            return;
        }

        // 2. MEVCUT SERVİS SENARYOSU (Asenkron Hydration)
        repairService.getServiceParts(service.getId()).thenAccept(parts -> {
            this.service.setAddedParts(parts);

            repairService.getTotalPartsCostForService(service.getId()).thenAccept(totalCost -> {
                this.service.setTotalPartsCost(totalCost);

                SwingUtilities.invokeLater(() -> {
                    bindAllPanels();
                    save_button.setEnabled(false);
                    update_button.setEnabled(false); // Sadece veri değişince açılacak (onDataChanged)
                    deliver_button.setEnabled(service.getServiceStatus() != ServiceStatus.DELIVERED);
                    updateTitle();
                });
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Servis detayları yüklenemedi!"));
            return null;
        });
    }

    private void bindAllPanels() {
        customer_info.bindService(this.service);
        device_info.bindService(this.service);
        fault_process_info.bindService(this.service);
        warranty_info.bindService(this.service);
        price_info.bindService(this.service);
        status_info.bindService(this.service);
        part_notes_info.bindService(this.service);
    }

    private Service collectForm() {
        // Zaten elimizde olan nesnenin üzerine değişiklikleri yazıyoruz
        if (this.service == null) {
            this.service = new Service();
        }

        // Temiz kapsüllenmiş Getter'lar ile veri toplama
        Customer customer = customer_info.getSelectedCustomer();
        if (customer != null) {
            service.setCustomerId(customer.getId());
        } else {
            service.setCustomerId(null);
        }

        service.setCreatedAt(customer_info.getRecordDate());
        service.setDeliveryAt(customer_info.getDeliverDate());

        service.setDeviceType(device_info.getSelectedDeviceType());
        service.setDeviceBrand(device_info.getSelectedBrand());
        service.setDeviceModel(device_info.getDeviceModel());
        service.setDeviceSerial(device_info.getDeviceSerial());
        service.setDevicePassword(device_info.getDevicePassword());
        service.setDeviceAccessory(device_info.getDeviceAccessory());

        service.setLaborCost(price_info.getLaborCost());
        service.setPaid(price_info.getPaid());
        service.setPaymentType(price_info.getPaymentType());

        service.setWarrantyDate(warranty_info.getWarrantyDate());
        service.setMaintenanceDate(warranty_info.getMaintenanceDate());

        service.setReportedFault(fault_process_info.getReportedFault());
        service.setDetectedFault(fault_process_info.getDetectedFault());
        service.setActionTaken(fault_process_info.getActionTaken());

        service.setServiceStatus(status_info.getSelected());
        service.setNotes(part_notes_info.getNotes());

        return service;
    }

    private void saveService() {
        Service newService = collectForm();
        save_button.setEnabled(false);

        try {
            if (newService.getServiceStatus() == ServiceStatus.DELIVERED && newService.getDeliveryAt() == null) {
                newService.setDeliveryAt(LocalDateTime.now());
            }

            // DİKKAT: Artık parts göndermiyoruz. Asenkron Kayıt!
            repairService.save(newService, false).thenAccept(savedService -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, "Servis başarıyla oluşturuldu.");
                    setService(savedService); // Arayüzü yeni ID ile güncelle (Parça ekleme aktifleşir)
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.ERROR, "Kayıt Hatası: " + ex.getCause().getMessage());
                    save_button.setEnabled(true);
                });
                return null;
            });
        } catch (Exception ex) {
            Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getMessage());
            save_button.setEnabled(true);
        }
    }

    private void updateService() {
        if (this.service == null || this.service.getId() <= 0) return;

        Service updated = collectForm();
        update_button.setEnabled(false);

        try {
            if (updated.getServiceStatus() == ServiceStatus.DELIVERED && updated.getDeliveryAt() == null) {
                updated.setDeliveryAt(LocalDateTime.now());
            }

            repairService.save(updated, true).thenAccept(savedService -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, "Servis güncellendi.");
                    setService(savedService); // Güncel veriyi yansıt
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.ERROR, "Güncelleme hatası: " + ex.getCause().getMessage());
                    update_button.setEnabled(true);
                });
                return null;
            });

        } catch (Exception e) {
            Toast.show(this, Toast.Type.ERROR, "Hata: " + e.getMessage());
            update_button.setEnabled(true);
        }
    }

    private void deleteService() {
        if (service == null || service.getId() <= 0) {
            Toast.show(this, Toast.Type.ERROR, "Silinecek bir servis seçili değil!");
            return;
        }

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Bu servisi silmek istediğinize emin misiniz?\nServise bağlı parçalar da silinecek. Bu işlem geri alınamaz!", "Servis Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
            if (action == SimpleModalBorder.YES_OPTION) {

                delete_button.setEnabled(false);

                repairService.delete(service.getId())
                        .thenAccept(result -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(this, Toast.Type.SUCCESS, "Servis silindi.");
                                FormManager.showForm(AllForms.getForm(FormServices.class));
                            });
                        }).exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(this, Toast.Type.ERROR, "Silme hatası: " + ex.getCause().getMessage());
                                delete_button.setEnabled(true);
                            });
                            return null;
                        });
            }
        }));
    }

    private void deliverService() {
        if (service == null || service.getId() <= 0) {
            Toast.show(this, Toast.Type.ERROR, "Teslim edilecek bir servis seçili değil!");
            return;
        }

        if (service.getServiceStatus() == ServiceStatus.DELIVERED) {
            Toast.show(this, Toast.Type.WARNING, "Bu Servis zaten teslim edilmiş.");
            return;
        }

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.DEFAULT,
                "Servis No: " + service.getId() + "\nBu cihazı teslim etmek istediğinize emin misiniz?",
                "Teslimat", SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {

            if (action == 0) {
                repairService.setDelivered(service.getId()).thenAccept(v -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, "Servis teslim edildi olarak işaretlendi.");
                        setService(service); // Formu yenile (Durum paneli kilitlensin diye)
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.ERROR, "İşlem hatası: " + ex.getCause().getMessage());
                    });
                    return null;
                });
            }
        }));
    }

    private void updateTitle() {
        if (service == null || service.getId() <= 0) {
            title.setText("Servis No: Yeni");
        } else {
            title.setText("Servis No: " + service.getId());
        }
    }

    private void onActionTaken() {
        String selectedDeviceType = device_info.getSelectedDeviceType();

        if (selectedDeviceType == null || selectedDeviceType.isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, "Lütfen bir cihaz türü seçin!");
            return;
        }

        ProcessSelectedPanel panel = new ProcessSelectedPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "İşlem Seç", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OPENED) {
                        DeviceSettings settings = Servicio.getDeviceSettings();
                        List<Process> processes = settings.getProcesses(selectedDeviceType);
                        panel.setProcess(processes);

                        panel.setOnProcessDoubleClick(process -> {
                            price_info.addLaborCost(process.getPrice());
                            fault_process_info.appendAction(process.getName());
                            controller.close();
                        });

                    } else if (action == SimpleModalBorder.OK_OPTION) {
                        List<Process> selected = panel.getSelectedProcesses();
                        if (selected == null || selected.isEmpty()) {
                            Toast.show(FormService.this, Toast.Type.WARNING, "Lütfen en az bir işlem seçin!");
                            return;
                        }

                        for (Process p : selected) {
                            price_info.addLaborCost(p.getPrice());
                            fault_process_info.appendAction(p.getName());
                        }

                        Toast.show(FormService.this, Toast.Type.SUCCESS, selected.size() + " işlem eklendi!");
                    }
                }), "processSelected");
    }

    private void initComponent() {
        setLayout(new MigLayout("wrap,fill", "[fill]", "[grow 0][fill]"));

        // TITLE
        JPanel panel = new JPanel(new MigLayout("fillx", "[]push[][]"));
        title = new JLabel("Servis No: Yeni");

        title.putClientProperty(FlatClientProperties.STYLE, "" +
                "font:bold +3");

        panel.add(title);
        add(panel);

        //CONTENT
        JPanel content = new JPanel(
                new MigLayout("wrap, fillx", "[grow, fill]", "")
        );
        content.setPreferredSize(null);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension pref = content.getPreferredSize();
                int vw = scrollPane.getViewport().getWidth();
                if (pref.width != vw) {
                    content.setPreferredSize(new Dimension(vw, pref.height));
                    content.revalidate();
                }
            }
        });

        customer_info = new CustomerInfoPanel();
        device_info = new DeviceInfoPanel();
        fault_process_info = new FaultProcessInfoPanel();
        warranty_info = new WarrantyInfoPanel();
        part_notes_info = new PartsNotesInfoPanel();
        price_info = new PriceInfoPanel();
        status_info = new StatusInfoPanel();

        content.add(customer_info, "growx, tag help2");
        content.add(device_info, "growx");
        content.add(price_info, "growx");
        content.add(warranty_info, "growx");
        content.add(fault_process_info, "growx");
        content.add(part_notes_info, "growx");
        content.add(status_info, "growx");

        add(scrollPane);

        //BUTTONS
        JPanel buttons_panel = new JPanel(new MigLayout("insets 0, fill", "", "[50:5%:]"));

        save_button = new JButton("Kaydet");
        update_button = new JButton("Güncelle");
        print_button = new JButton("Yazdır");
        deliver_button = new JButton("Teslim Et");
        delete_button = new JButton("Sil");
        whatsapp_button = new JButton("Whatsapp");

        buttons_panel.add(save_button, "grow");
        buttons_panel.add(update_button, "grow");
        buttons_panel.add(print_button, "grow");
        buttons_panel.add(deliver_button, "grow");
        buttons_panel.add(delete_button, "grow");
        buttons_panel.add(whatsapp_button, "grow");

        add(buttons_panel);
    }

    private JLabel title;

    private CustomerInfoPanel customer_info;
    private DeviceInfoPanel device_info;
    private FaultProcessInfoPanel fault_process_info;
    private PriceInfoPanel price_info;
    private WarrantyInfoPanel warranty_info;
    private PartsNotesInfoPanel part_notes_info;
    private StatusInfoPanel status_info;

    private JButton save_button;
    private JButton update_button;
    private JButton deliver_button;
    private JButton print_button;
    private JButton delete_button;
    private JButton whatsapp_button;
}

