package tr.cabro.servicio.application.panels;

import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.system.FormManager;
import tr.cabro.servicio.application.tablemodal.CustomerServiceRecordTableModel;
import tr.cabro.servicio.forms.FormService;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CustomerInfoPanel extends JPanel {

    public CustomerInfoPanel() {
        init();
    }

    private void init() {
        initComponent();

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) { // Çift tıklama kontrolü
                    int row = table.getSelectedRow();
                    CustomerServiceRecordTableModel model = (CustomerServiceRecordTableModel) table.getModel();
                    Service service = model.getService(row);

                    // Yeni bir pencere aç, örneğin:
                    FormService form = new FormService();
                    form.setService(service);
                    FormManager.showForm(form);
                }
            }
        });
    }

    public void setData(@NonNull Customer data) {
        customerName.setText(data.getName());
        businessNameInfo.setText(data.getBusiness_name());
        phoneNo1Info.setText(data.getPhone_number_1());
        phoneNo2Info.setText(data.getPhone_number_2());
        idNoInfo.setText(data.getId_no());
        emailInfo.setText(data.getEmail());
        addressInfo.setText(data.getAddress());

        RepairService service = ServiceManager.getRepairService();
        List<Service> services = service.getAll(data.getId());
        CustomerServiceRecordTableModel model = new CustomerServiceRecordTableModel(services);
        table.setModel(model);
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 5, gap 10", "[50%][grow]", "[grow][grow]"));

        JPanel customerInfo = new JPanel(new MigLayout("wrap 2, insets 0", "[][grow]", "[][][][][][][]"));
        customerName = new JLabel("...");
        customerName.setFont(customerName.getFont().deriveFont(Font.BOLD, 18f));
        customerName.setHorizontalAlignment(SwingConstants.CENTER);

        customerInfo.add(customerName, "span 2, growx, gaptop 5, gapbottom 10");
        customerInfo.add(new JLabel("Firma Adı:"));
        businessNameInfo = new JLabel("...");
        customerInfo.add(businessNameInfo, "growx");

        customerInfo.add(new JLabel("Telefon No. 1:"));
        phoneNo1Info = new JLabel("...");
        customerInfo.add(phoneNo1Info, "growx");

        customerInfo.add(new JLabel("Telefon No. 2:"));
        phoneNo2Info = new JLabel("...");
        customerInfo.add(phoneNo2Info, "growx");

        customerInfo.add(new JLabel("TC Kimlik No.:"));
        idNoInfo = new JLabel("...");
        customerInfo.add(idNoInfo, "growx");

        customerInfo.add(new JLabel("E-Posta:"));
        emailInfo = new JLabel("...");
        customerInfo.add(emailInfo, "growx");

        customerInfo.add(new JLabel("Adres:"));
        addressInfo = new JLabel("...");
        customerInfo.add(addressInfo, "span 2, growx");

        // Alt sol panel: borç bilgisi
        customerDebt = new JPanel(new MigLayout("fill"));
        customerDebt.setBorder(BorderFactory.createTitledBorder("Borç Bilgisi"));

        // Sağ panel: sekmeler
        tabbedPane = new JTabbedPane();
        serviceRecords = new JPanel(new MigLayout("fill"));
        processPanel = new JPanel(new MigLayout("fill"));

        table = new JTable();
        tableScroll = new JScrollPane(table);
        serviceRecords.add(tableScroll, "grow, push");

        tabbedPane.addTab("Servis Kayıtları", serviceRecords);
        tabbedPane.addTab("İşlemler", processPanel);

        // Ana yerleşim
        add(customerInfo, "cell 0 0, grow");
        add(tabbedPane, "cell 1 0 1 2, grow, push");
        add(customerDebt, "cell 0 1, grow, pushy");
    }

    private JLabel customerName;
    private JLabel businessNameInfo;
    private JLabel phoneNo1Info;
    private JLabel phoneNo2Info;
    private JLabel idNoInfo;
    private JLabel emailInfo;
    private JLabel addressInfo;
    private JTable table;
    private JScrollPane tableScroll;
    private JTabbedPane tabbedPane;
    private JPanel serviceRecords;
    private JPanel processPanel;
    private JPanel customerDebt;

}
