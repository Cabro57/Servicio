package tr.cabro.servicio.application.ui;

import tr.cabro.servicio.application.panels.*;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.RepairService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class ServiceEditUI extends JDialog {
    private JPanel main_panel;
    private JPanel left_panel;
    private JPanel right_panel;
    private DeviceInfoPanel device_info;
    private CustomerInfoPanel customer_info;
    private FaultProcessInfoPanel fault_process_info;
    private PriceInfoPanel price_info;
    private WarrantyInfoPanel warranty_info;
    private PartsNotesInfoPanel part_notes_info;
    private StatusInfoPanel status_info;
    private JPanel buttons_panel;
    private JButton save_button;
    private JButton update_button;
    private JButton print_button;
    private JButton deliver_button;
    private JButton delete_button;
    private JButton whatsapp_button;

    private Service service;

    private final RepairService repairService;
    private final PartService partService;

    public ServiceEditUI(Service service) {
        super((Frame) null, service != null ? "Seri No: " + service.getId() : "Seri No: Yeni", true);
        this.service = service;
        this.partService = ServiceManager.getPartService();
        this.repairService = ServiceManager.getRepairService();
        init();
        setContentPane(main_panel);
    }

    public ServiceEditUI() {
        this(null);
    }

    private void init() {

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth = screen_size.width;
        int screenHeight = screen_size.height;

        if (screenWidth > 1280 && screenHeight > 720) {
            // 720p ve altı → yüzde boyut
            int width = (int) (screenWidth * 0.80);
            int height = (int) (screenHeight * 0.75);
            setSize(width, height);
        } else {
            // 1080p ve üstü → tam ekran
            setSize(screenWidth, screenHeight);
        }
        setLocationRelativeTo(null);

        if (service != null) {
            fillForm();
            save_button.setEnabled(false);
        }

        save_button.addActionListener(e -> saveService());
        update_button.addActionListener(e -> updateService());
        deliver_button.addActionListener(e -> deliverCmd());
        delete_button.addActionListener(e -> deleteService());
        whatsapp_button.addActionListener(e -> sendWhatsAppMessage());

        fault_process_info.getAction_taken_button().addActionListener(e -> {
            String selectedDeviceType = (String) device_info.getDevice_type_combo().getSelectedItem();

            if (selectedDeviceType == null || selectedDeviceType.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Lütfen bir cihaz türü seçin!", "Uyarı", JOptionPane.WARNING_MESSAGE);
                return;
            }

            ProcessSelectedUI processSelectedUI = new ProcessSelectedUI(selectedDeviceType);
            processSelectedUI.setModal(true);
            processSelectedUI.setVisible(true);

            Process process = processSelectedUI.getSelectedProcess();

            if (process != null) {
                price_info.addLaborCost(process.getPrice());
                fault_process_info.appendAction(process.getName());
            }
        });

        part_notes_info.addPartsChangeListener(totalMaterialCost -> {
            price_info.setMaterialCost(totalMaterialCost);
        });
    }

    private void fillForm() {
        // Basit örnek doldurma, sen panel metodlarını uyarlarsın
        customer_info.loadCustomer(service.getCustomer_id());
        customer_info.setRecordDate(service.getCreated_at());
        customer_info.setDeliverDate(service.getDelivery_at());

        device_info.setDeviceType(service.getDevice_type());
        device_info.setDeviceBrand(service.getDevice_brand());
        device_info.setDeviceModel(service.getDevice_model());
        device_info.setDeviceSerial(service.getDevice_serial());
        device_info.setDeviceAccessory(service.getDevice_accessory());
        device_info.setDevicePassword(service.getDevice_password());

        fault_process_info.setReportedFault(service.getReported_fault());
        fault_process_info.setDetectedFault(service.getDetected_fault());
        fault_process_info.setActionTaken(service.getAction_taken());

        price_info.setLaborCost(service.getLabor_cost());
        price_info.setPaid(service.getPaid());

        warranty_info.setWarrantyDate(service.getWarranty_date());
        warranty_info.setMaintenanceDate(service.getMaintenance_date());

        PartService partService = ServiceManager.getPartService();
        List<AddedPart> addedParts = partService.getPartsByServiceId(service.getId());
        part_notes_info.setServiceId(service.getId());
        part_notes_info.setAddedParts(addedParts);
        part_notes_info.setNotes(service.getNotes());
        status_info.setSelected(service.getService_status());
    }

    private void saveService() {
        Service newService = collectForm();

        if (repairService.saveService(newService, false)) {
            List<AddedPart> addedParts = part_notes_info.getAddedParts();
            for (AddedPart addedPart: addedParts) {
                addedPart.setServiceId(newService.getId());
                partService.addPartToService(addedPart);
            }
            JOptionPane.showMessageDialog(this, "Servis başarıyla kaydedildi!", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
            this.service = newService;
        } else {
            JOptionPane.showMessageDialog(this, "Servis kaydedilemedi!", "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateService() {
        if (service == null) {
            JOptionPane.showMessageDialog(this, "Güncellenecek bir servis seçili değil!", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Service updated = collectForm();
        updated.setId(service.getId());

        if (repairService.saveService(updated, true)) {
            partService.deleteAllPartsFromService(service.getId());
            List<AddedPart> addedParts = part_notes_info.getAddedParts();
            for (AddedPart addedPart: addedParts) {
                addedPart.setServiceId(updated.getId());
                partService.addPartToService(addedPart);
            }

            JOptionPane.showMessageDialog(this, "Servis başarıyla güncellendi!", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Servis güncellenemedi!", "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deliverCmd() {
        if (service == null) {
            JOptionPane.showMessageDialog(this, "Teslim edilecek bir servis seçili değil!", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bu servisi teslim etmek istediğinize emin misiniz?",
                "Teslim Onayı",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Güncellenmiş servis verilerini topla
        Service deliveredService = collectForm();
        deliveredService.setId(service.getId());
        deliveredService.setService_status("Teslim Edildi");
        deliveredService.setDelivery_at(LocalDate.now()); // Teslim tarihi şimdi

        if (repairService.saveService(deliveredService, true)) {
            // Parçaları güncelle
            partService.deleteAllPartsFromService(service.getId());
            List<AddedPart> addedParts = part_notes_info.getAddedParts();
            for (AddedPart addedPart: addedParts) {
                addedPart.setServiceId(deliveredService.getId());
                partService.addPartToService(addedPart);
            }

            JOptionPane.showMessageDialog(this, "Servis teslim edildi!", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Servis teslim edilemedi!", "Hata", JOptionPane.ERROR_MESSAGE);
        }

    }

    private void sendWhatsAppMessage() {
        if (service == null) {
            JOptionPane.showMessageDialog(this, "WhatsApp mesajı için bir servis seçili değil!", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int customerId = customer_info.getCustomerId();
        CustomerService customerService = ServiceManager.getCustomerService();
        Optional<Customer> optionalCustomer = customerService.get(customerId);
        if (!optionalCustomer.isPresent()) {
            JOptionPane.showMessageDialog(this, "Müşteri bilgisi bulunamadı!", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Customer c = optionalCustomer.get();

        String phone = c.getPhone_number_1();
        if (phone == null || phone.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Müşteri için telefon numarası bulunamadı!", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String message = "Merhaba, cihazınız (" + device_info.getDeviceBrand() + " " + device_info.getDeviceModel() +
                ") servis kaydınız hakkında bilgi almak için bu mesajı iletmekteyiz.";
        try {
            String url = "https://wa.me/" + phone.replaceAll("\\D+", "") + "?text=" +
                    java.net.URLEncoder.encode(message, "UTF-8");
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "WhatsApp açılamadı: " + e.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteService() {
        if (service == null) {
            JOptionPane.showMessageDialog(this, "Silinecek bir servis seçili değil!", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bu servisi silmek istediğinize emin misiniz?\n" +
                        "Servise bağlı parçalar da silinecek. Bu işlem geri alınamaz!",
                "Servis Silme Onayı",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            // Önce bağlı parçaları sil
            partService.deleteAllPartsFromService(service.getId());
            // Sonra servisi sil
            boolean deleted = repairService.deleteService(service.getId());

            if (deleted) {
                JOptionPane.showMessageDialog(this, "Servis ve bağlı parçalar başarıyla silindi.", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
                dispose(); // Dialog'u kapat
            } else {
                JOptionPane.showMessageDialog(this, "Servis silinemedi!", "Hata", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Silme sırasında hata oluştu: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Service collectForm() {
        Service s = new Service(
                customer_info.getCustomerId(),
                device_info.getDeviceType(),
                device_info.getDeviceBrand(),
                device_info.getDeviceModel()
        );

        s.setCreated_at(customer_info.getRecordDate());
        s.setDelivery_at(customer_info.getDeliverDate());

        s.setDevice_serial(device_info.getDeviceSerial());
        s.setDevice_password(device_info.getDevicePassword());
        s.setDevice_accessory(device_info.getDeviceAccessory());

        s.setLabor_cost(price_info.getLaborCost());
        s.setPaid(price_info.getPaid());
        s.setPayment_type(price_info.getPaymentType());

        s.setWarranty_date(warranty_info.getWarrantyDate());
        s.setMaintenance_date(warranty_info.getMaintenanceDate());

        s.setReported_fault(fault_process_info.getReportedFault());
        s.setDetected_fault(fault_process_info.getDetectedFault());
        s.setAction_taken(fault_process_info.getActionTaken());

        s.setService_status(status_info.getSelected());

        s.setNotes(part_notes_info.getNotes());

        return s;
    }
}
