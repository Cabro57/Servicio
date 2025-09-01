package tr.cabro.servicio.application.ui;

import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.listeners.ServiceEditListener;
import tr.cabro.servicio.application.ui.service.*;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.application.context.ServiceContext;

import javax.swing.*;
import java.awt.*;

public class ServiceEditUI extends JDialog {

    private final ServiceContext context;

    private final RepairService repairService;
    private final PartService partService;

    public ServiceEditUI(JFrame owner) {
        super(owner, true);

        context = new ServiceContext();

        repairService = ServiceManager.getRepairService();
        partService = ServiceManager.getPartService();

        init();
    }

    private void init() {
        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth = screen_size.width;
        int screenHeight = screen_size.height;

        int width;
        int height;

        if (screenWidth < 1366 && screenHeight < 768) {
            width = (int) (screenWidth * 0.999);
            height = (int) (screenHeight * 0.95);
        } else if (screenWidth < 1920 && screenHeight < 1080) {
            width = (int) (screenWidth * 0.965);
            height = (int) (screenHeight * 0.90);
        } else {
            width = (int) (screenWidth * 0.816);
            height = (int) (screenHeight * 0.75);
        }

        setSize(width, height);
        setLocationRelativeTo(null);

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
            public void onProcessAdded(Process process) {
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

        fault_process_info.action_taken_button.addActionListener(e -> {
            String selectedDeviceType = (String) device_info.deviceTypeComboBoxModel.getSelectedItem();

            if (selectedDeviceType == null || selectedDeviceType.isEmpty()) {
                Toast.show(this, Toast.Type.WARNING, "Lütfen bir cihaz türü seçin!");
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

    }

    public void setService(@NonNull Service service) {
        this.context.setService(service);
        fillForm();
        save_button.setEnabled(false);
        updateTitle();
    }

    private void updateTitle() {
        Service service = context.getService();
        if (service == null || service.getId() == 0) {
            setTitle("Servis No: Yeni");
        } else {
            setTitle("Servis No: " + service.getId());
        }
    }

    private void fillForm() {
        Service service = context.getService();
        customer_info.loadCustomer(service.getCustomer_id());
        customer_info.setRecordDate(service.getCreated_at());
        customer_info.setDeliverDate(service.getDelivery_at());

        device_info.setDeviceType(service.getDevice_type());
        device_info.setDeviceBrand(service.getDevice_brand());
        device_info.model_field.setText(service.getDevice_model());
        device_info.seri_no_field.setText(service.getDevice_serial());
        device_info.accessory_field.setText(service.getDevice_accessory());
        device_info.password_field.setText(service.getDevice_password());

        fault_process_info.reported_fault_field.setText(service.getReported_fault());
        fault_process_info.detected_fault_field.setText(service.getDetected_fault());
        fault_process_info.action_taken_field.setText(service.getAction_taken());

        price_info.setLaborCost(service.getLabor_cost());
        price_info.setPaid(service.getPaid());

        warranty_info.setWarrantyDate(service.getWarranty_date());
        warranty_info.setMaintenanceDate(service.getMaintenance_date());

        part_notes_info.setServiceId(service.getId());
        part_notes_info.setNotes(service.getNotes());

        status_info.setSelected(service.getService_status().getDisplayName());
    }

    private Service collectForm() {
        Service service = context.getService();
        if (service == null) {
            service = new Service();
        }

        service.setCustomer_id(customer_info.getCustomerId());
        service.setCreated_at(customer_info.getRecordDate());
        service.setDelivery_at(customer_info.getDeliverDate());

        service.setDevice_type((String) device_info.deviceTypeComboBoxModel.getSelectedItem());
        service.setDevice_brand((String) device_info.brandComboBoxModel.getSelectedItem());
        service.setDevice_model(device_info.model_field.getText());
        service.setDevice_serial(device_info.seri_no_field.getText());
        service.setDevice_password(device_info.password_field.getText());
        service.setDevice_accessory(device_info.accessory_field.getText());

        service.setLabor_cost(price_info.getLaborCost());
        service.setPaid(price_info.getPaid());
        service.setPayment_type(price_info.getPaymentType());

        service.setWarranty_date(warranty_info.getWarrantyDate());
        service.setMaintenance_date(warranty_info.getMaintenanceDate());

        service.setReported_fault(fault_process_info.reported_fault_field.getText());
        service.setDetected_fault(fault_process_info.detected_fault_field.getText());
        service.setAction_taken(fault_process_info.action_taken_field.getText());

        service.setService_status(status_info.getSelected());

        service.setNotes(part_notes_info.getNotes());

        return service;
    }

    private void initComponent() {
        setLayout(new MigLayout(
                "fill, insets 10, gap 10", // wrap kullanmayacağız
                "[grow, fill][grow, fill]",       // 2 sütun eşit genişlikte
                "[fill]"                          // tek satır, tüm yükseklik paylaşılacak
        ));

        customer_info = new CustomerInfoPanel(context);
        device_info = new DeviceInfoPanel(context);
        fault_process_info = new FaultProcessInfoPanel(context);
        warranty_info = new WarrantyInfoPanel(context);
        part_notes_info = new PartsNotesInfoPanel(context);
        price_info = new PriceInfoPanel(context);
        status_info = new StatusInfoPanel(context);

        save_button = new JButton("Kaydet");
        update_button = new JButton("Güncelle");
        print_button = new JButton("Yazdır");
        deliver_button = new JButton("Teslim Et");
        delete_button = new JButton("Sil");
        whatsapp_button = new JButton("Whatsapp");

        JPanel left_panel = new JPanel(new MigLayout("insets 0, fill, wrap"));
        left_panel.add(customer_info, "growx");
        left_panel.add(device_info, "growx");
        left_panel.add(price_info, "growx");
        left_panel.add(warranty_info, "grow");

        JPanel right_panel = new JPanel(new MigLayout("insets 0, fill, wrap"));
        right_panel.add(fault_process_info, "growx, top");
        right_panel.add(part_notes_info, "growx, top");
        right_panel.add(status_info, "growx, top");

        JPanel buttons_panel = new JPanel(new MigLayout("insets 0, fill", "", "[50:5%:]"));
        buttons_panel.add(save_button, "grow");
        buttons_panel.add(update_button, "grow");
        buttons_panel.add(print_button, "grow");
        buttons_panel.add(deliver_button, "grow");
        buttons_panel.add(delete_button, "grow");
        buttons_panel.add(whatsapp_button, "grow");

        add(left_panel, "cell 0 0, grow, push, sg group1");
        add(right_panel, "cell 1 0, grow, push, sg group1");
        add(buttons_panel, "cell 1 1, grow, push");

    }


    private CustomerInfoPanel customer_info;
    private DeviceInfoPanel device_info;
    private FaultProcessInfoPanel fault_process_info;
    private PriceInfoPanel price_info;
    private WarrantyInfoPanel warranty_info;
    private PartsNotesInfoPanel part_notes_info;
    private StatusInfoPanel status_info;

    private JButton save_button;
    private JButton update_button;
    private JButton print_button;
    private JButton deliver_button;
    private JButton delete_button;
    private JButton whatsapp_button;
}
