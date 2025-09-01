package tr.cabro.servicio.application.ui;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.application.tablemodal.CustomerServiceRecordTableModel;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CustomerInfoUI extends  JDialog {
    private JTabbedPane tabbed_pane;
    private JTable table;
    private JPanel main_panel;
    private JPanel customer_info;
    private JPanel customer_debt;
    private JPanel service_records;
    private JScrollPane table_scroll;
    private JPanel process_panel;
    private JLabel customer_name;
    private JLabel business_name_info;
    private JLabel business_name_label;
    private JLabel phone_no_1_label;
    private JLabel phone_no_2_label;
    private JLabel phone_no_1_info;
    private JLabel phone_no_2_info;
    private JLabel id_no_label;
    private JLabel id_no_info;
    private JLabel e_mail_label;
    private JLabel e_mail_info;
    private JLabel address_label;
    private JLabel address_info;

    private final Customer customer;

    public CustomerInfoUI(Customer customer) {
        this.customer = customer;
        init();
        add(main_panel);


    }

    private void init() {
        setTitle("Müşteri Bilgisi - " + (customer != null ? customer.getName() : ""));
        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.6);
        int height = (int) (screen_size.height * 0.6);
        setSize(width, height);
        setLocationRelativeTo(null);

        if (customer != null) {
            customer_name.setText(customer.getName());
            business_name_info.setText(customer.getBusiness_name());
            phone_no_1_info.setText(customer.getPhone_number_1());
            phone_no_2_info.setText(customer.getPhone_number_2());
            id_no_info.setText(customer.getId_no());
            e_mail_info.setText(customer.getEmail());
            address_info.setText(customer.getAddress());

            // Hizmet kayıtları çek ve tabloya bas
            RepairService service = ServiceManager.getRepairService();
            List<Service> services = service.getServicesByCustomerId(customer.getID());
            CustomerServiceRecordTableModel model = new CustomerServiceRecordTableModel(services);
            table.setModel(model);

            table.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2 && table.getSelectedRow() != -1) { // Çift tıklama kontrolü
                        int row = table.getSelectedRow();
                        CustomerServiceRecordTableModel model = (CustomerServiceRecordTableModel) table.getModel();
                        Service service = model.getService(row);

                        // Yeni bir pencere aç, örneğin:
                        OldServiceEditUI dialog = new OldServiceEditUI(service);
                        dialog.setVisible(true);
                    }
                }
            });

        }
    }

}
