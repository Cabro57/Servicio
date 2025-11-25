package tr.cabro.servicio.forms;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.simple.SimpleMessageModal;
import raven.modal.system.Form;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.context.ServiceContext;
import tr.cabro.servicio.application.listeners.ServiceEditListener;
import tr.cabro.servicio.application.panels.ProcessSelectedPanel;
import tr.cabro.servicio.application.panels.service.*;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.Process;
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

    private final ServiceContext context;
    private final RepairService repairService;

    public FormService(Service service) {
        context = new ServiceContext();
        repairService = ServiceManager.getRepairService();

        init();
        setService(service);
    }

    public FormService() {
        context = new ServiceContext();
        repairService = ServiceManager.getRepairService();
        init();
    }

    private void init() {
        initComponent();

        ServiceEditListener serviceEditListener = new ServiceEditListener() {
            @Override
            public void onPartChange(double price) {
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

        clearForm();
    }

    public void setService(@NonNull Service service) {
        this.context.setService(service);
        this.context.setParts(repairService.getParts(service.getId()));
        fillForm();
        save_button.setEnabled(false);
        updateTitle();
    }

    private void saveService() {
        Service newService = collectForm();

        try {
            if (newService.getServiceStatus().equals(ServiceStatus.DELIVERED)) {
                if (newService.getDeliveryAt() == null) newService.setDeliveryAt(LocalDateTime.now());
            }

            repairService.save(newService, false, part_notes_info.getAddedParts());
            Toast.show(this, Toast.Type.SUCCESS, "Servis başarıyla oluşturuldu.");

        } catch (Exception e) {
            Toast.show(this, Toast.Type.ERROR, "Hata: " + e.getMessage());
            Servicio.getLogger().error("Servis kayıt hatası", e);
        }
    }

    private void updateService() {
        Service service = context.getService();
        if (service == null) {
            Toast.show(this, Toast.Type.WARNING, "Güncellenecek bir servis seçili değil!");
            return;
        }

        Service updated = collectForm();

        try {
            updated.setId(service.getId());
            updated.setCreatedAt(service.getCreatedAt());

            if (updated.getServiceStatus().equals(ServiceStatus.DELIVERED)) {
                if (updated.getDeliveryAt() == null) updated.setDeliveryAt(LocalDateTime.now());
            }

            repairService.save(updated, true, part_notes_info.getAddedParts());

            Toast.show(this, Toast.Type.SUCCESS, "Servis güncellendi.");

        } catch (Exception e) {
            Toast.show(this, Toast.Type.ERROR, "Güncelleme hatası: " + e.getMessage());
            Servicio.getLogger().error("Servis güncelleme hatası", e);
        }
    }

    private void deleteService() {
        Service service = context.getService();
        if (service == null) {
            Toast.show(this, Toast.Type.ERROR, "Silinecek bir servis seçili değil!");
            return;
        }

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Bu servisi silmek istediğinize emin misiniz?\n" +
                        "Servise bağlı parçalar da silinecek. Bu işlem geri alınamaz!", "Servis Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
            if (action == SimpleModalBorder.YES_OPTION) {
                try {
                    repairService.delete(service.getId());
                    Toast.show(this, Toast.Type.SUCCESS, "Servis silindi.");
                    clearForm();

                } catch (Exception e) {
                    Toast.show(this, Toast.Type.ERROR, "Silme hatası: " + e.getMessage());
                }
            }
        }));
    }

    private void deliverService() {
        Service service = context.getService();

        if (service == null) {
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
                        try {
                            repairService.setDelivered(service.getId());
                            Toast.show(this, Toast.Type.SUCCESS, "Servis teslim edildi olarak işaretlendi.");

                        } catch (Exception e) {
                            Toast.show(this, Toast.Type.ERROR, "İşlem hatası: " + e.getMessage());
                        }
                    }
        }));
    }

    private void updateTitle() {
        Service service = context.getService();
        if (service == null || service.getId() == 0) {
            title.setText("Servis No: Yeni");
        } else {
            title.setText("Servis No: " + service.getId());
        }
    }

    private void onActionTaken() {
        final String id = "processSelected";
        String selectedDeviceType = (String) device_info.deviceTypeComboBoxModel.getSelectedItem();

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
                                List<tr.cabro.servicio.model.Process> processes = settings.getProcesses(selectedDeviceType);
                                panel.setProcess(processes);

                                panel.setOnProcessDoubleClick(process -> {
                                    price_info.addLaborCost(process.getPrice());
                                    fault_process_info.appendAction(process.getName());
                                    controller.close();
                                });

                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                List<tr.cabro.servicio.model.Process> selected = panel.getSelectedProcesses();
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

                        }),
                id);
    }

    private void fillForm() {
        Service service = context.getService();
        customer_info.setCustomer(service.getCustomerId());
        customer_info.setRecordDate(service.getCreatedAt());
        customer_info.setDeliverDate(service.getDeliveryAt());

        device_info.setDeviceType(service.getDeviceType());
        device_info.setDeviceBrand(service.getDeviceBrand());
        device_info.model_field.setText(service.getDeviceModel());
        device_info.seri_no_field.setText(service.getDeviceSerial());
        device_info.accessory_field.setText(service.getDeviceAccessory());
        device_info.password_field.setText(service.getDevicePassword());

        fault_process_info.reported_fault_field.setText(service.getReportedFault());
        fault_process_info.detected_fault_field.setText(service.getDetectedFault());
        fault_process_info.action_taken_field.setText(service.getActionTaken());

        price_info.setLaborCost(service.getLaborCost());
        price_info.setPaid(service.getPaid());

        warranty_info.setWarrantyDate(service.getWarrantyDate());
        warranty_info.setMaintenanceDate(service.getMaintenanceDate());

        part_notes_info.setAddedParts(context.getParts());
        part_notes_info.setNotes(service.getNotes());

        status_info.setSelected(service.getServiceStatus().getDisplayName());
    }

    private Service collectForm() {
        Service service = context.getService();
        if (service == null) {
            service = new Service();
        }

        Customer customer = customer_info.selectedCustomer;
        if (customer != null) {
            service.setCustomerId(customer.getId());
        }

        service.setCreatedAt(customer_info.getRecordDate());
        service.setDeliveryAt(customer_info.getDeliverDate());

        service.setDeviceType((String) device_info.deviceTypeComboBoxModel.getSelectedItem());
        service.setDeviceBrand((String) device_info.brandComboBoxModel.getSelectedItem());
        service.setDeviceModel(device_info.model_field.getText());
        service.setDeviceSerial(device_info.seri_no_field.getText());
        service.setDevicePassword(device_info.password_field.getText());
        service.setDeviceAccessory(device_info.accessory_field.getText());

        service.setLaborCost(price_info.getLaborCost());
        service.setPaid(price_info.getPaid());
        service.setPaymentType(price_info.getPaymentType());

        service.setWarrantyDate(warranty_info.getWarrantyDate());
        service.setMaintenanceDate(warranty_info.getMaintenanceDate());

        service.setReportedFault(fault_process_info.reported_fault_field.getText());
        service.setDetectedFault(fault_process_info.detected_fault_field.getText());
        service.setActionTaken(fault_process_info.action_taken_field.getText());

        service.setServiceStatus(status_info.getSelected());

        service.setNotes(part_notes_info.getNotes());

        return service;
    }

    private void clearForm() {
        context.setService(new Service());
        context.setParts(new ArrayList<>());
        customer_info.setCustomer(-1);
        customer_info.setRecordDate(LocalDateTime.now());
        customer_info.setDeliverDate(null);

        device_info.setDeviceType(null);
        device_info.setDeviceBrand(null);
        device_info.model_field.setText("");
        device_info.seri_no_field.setText("");
        device_info.accessory_field.setText("");
        device_info.password_field.setText("");

        fault_process_info.reported_fault_field.setText("");
        fault_process_info.detected_fault_field.setText("");
        fault_process_info.action_taken_field.setText("");

        price_info.setLaborCost(0);
        price_info.setPaid(0);

        warranty_info.setWarrantyDate(null);
        warranty_info.setMaintenanceDate(null);

        part_notes_info.setAddedParts(null);
        part_notes_info.setNotes("");

        status_info.setSelected(null);

        updateTitle();
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

        customer_info = new CustomerInfoPanel(context);
        device_info = new DeviceInfoPanel(context);
        fault_process_info = new FaultProcessInfoPanel(context);
        warranty_info = new WarrantyInfoPanel(context);
        part_notes_info = new PartsNotesInfoPanel(context);
        price_info = new PriceInfoPanel(context);
        status_info = new StatusInfoPanel(context);

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

