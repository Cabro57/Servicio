package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.compenents.SMenuBar;
import tr.cabro.servicio.application.compenents.SearchField;
import tr.cabro.servicio.application.listeners.WindowClosingEvent;
import tr.cabro.servicio.icons.SVGIconUIColor;
import tr.cabro.servicio.model.Customer;

import javax.swing.*;
import java.awt.*;

public class MainUI extends JFrame {

    private JPanel main_panel;
    private  SearchField search_field;
    private JButton customer_list_button;
    private JButton part_edits_button;
    private JButton record_service_button;
    private JButton second_hand_device;

    public MainUI() {

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.8);
        int height = (int) (screen_size.height * 0.8);
        setSize(width, height);
        setMinimumSize(new Dimension(width, height));
        setLocationRelativeTo(null);

        init();
    }

    private void init() {
        initComponent();

        String version = Servicio.getInstance().getAppVersion();
        setTitle("Servicio - " + version);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        setJMenuBar(new SMenuBar());

        search_field.addActionListener(e -> {
            String s = search_field.getText().trim();
            CustomerSearchUI customerSearchUI = new CustomerSearchUI(s);
            customerSearchUI.setModal(true);
            customerSearchUI.setVisible(true);

            Customer cs = customerSearchUI.getSelectedCustomer();
            if (cs != null) {
                CustomerInfoUI dialog = new CustomerInfoUI(cs);
                dialog.setModal(true);
                dialog.setVisible(true);
            }
        });

        customer_list_button.addActionListener(e -> showUI(new CustomerListUI()));
        part_edits_button.addActionListener(e -> showUI(new PartEditUI()));
        record_service_button.addActionListener(e -> new ServiceEditUI().setVisible(true));


        addWindowListener(new WindowClosingEvent());
    }

    private void initComponent() {
        main_panel = new JPanel(new MigLayout(
                "insets 10, fillx, filly", // wrap 5 -> 5 sütun sonra satır atla
                "[][center][][][][]",   // 5 sütun, hepsi eşit büyüyebilir
                "[6%::12%]10[grow]"                        // Üst satır sabit, alt satır boşluğu doldurur
        ));

        search_field = new SearchField();

        customer_list_button = createActionButton("Müşteri Listesi", "icon/customer_list.svg");
        part_edits_button = createActionButton("Parça Ekle/Güncelle", "icon/part_edit.svg");
        record_service_button = createActionButton("Yeni Servis", "icon/new_service.svg");
        second_hand_device = createActionButton("İkinci El Cihaz", "icon/second_hand_device.svg");
        second_hand_device.setEnabled(false);

        // Arama kutusu tüm genişliği kaplasın
        main_panel.add(search_field, "grow, pushx, width 21%:24%:");

        // Ayraç
        main_panel.add(new JSeparator(JSeparator.VERTICAL), "growy, pushy, width 3::");

        // Butonlar yan yana, hepsi eşit genişlikte
        main_panel.add(customer_list_button, "grow, pushx");
        main_panel.add(part_edits_button, "grow, pushx");
        main_panel.add(record_service_button, "grow, pushx");
        main_panel.add(second_hand_device, "grow, pushx");

        setContentPane(main_panel);
    }

    private void showUI(JDialog dialog) {
        dialog.setModal(true);
        dialog.setVisible(true);
    }

    private JButton createActionButton(String text, String iconPath) {
        JButton button = new JButton(text, new SVGIconUIColor(iconPath, 0.04f, "Button.foreground"));
        button.putClientProperty(FlatClientProperties.STYLE_CLASS, "actionButton");
        button.setPreferredSize(new Dimension(0, 50));
        return button;
    }

    public void closeApplication() {

        if (Servicio.getSettings().isConfirmExitDialog()) {
            System.exit(0);
            return;
        }

        JPanel panel = new JPanel(new BorderLayout(15, 5));

        JLabel label = new JLabel("Çıkmak istediğinizden emin misiniz?");
        panel.add(label, BorderLayout.NORTH);

        JCheckBox dontAskAgain = new JCheckBox("Bir daha sorma");
        panel.add(dontAskAgain, BorderLayout.CENTER);


        Object[] options = {"Evet", "Hayır"};

        int choice = JOptionPane.showOptionDialog(
                this,
                panel,
                "",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, // Varsayılan simge (null)
                options, // Özelleştirilmiş buton metinleri
                options[0] // Varsayılan seçim ("Evet")
        );

        if (choice == JOptionPane.YES_OPTION) {
            if (dontAskAgain.isSelected()) {
                Servicio.getSettings().setConfirmExitDialog(true);
                Servicio.getSettings().save();
            }

            System.exit(1);
        }
    }

}
