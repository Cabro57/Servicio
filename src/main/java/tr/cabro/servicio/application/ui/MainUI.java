package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.compenents.SMenuBar;
import tr.cabro.servicio.application.compenents.SearchField;
import tr.cabro.servicio.application.listeners.WindowClosingEvent;
import tr.cabro.servicio.model.Customer;

import javax.swing.*;
import java.awt.*;

public class MainUI extends JFrame {
    private JPanel main_panel;
    private SearchField search_field;
    private JButton customer_list_button;
    private JButton part_edits_button;
    private JButton record_service_button;

    public MainUI() {
        init();

        setContentPane(main_panel);
    }

    private void init() {
        setJMenuBar(new SMenuBar());

        Setup();

        String version = Servicio.getInstance().getAppVersion();
        setTitle("Servicio - " + version);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        if (Servicio.getSettings().isFull_size()) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        addWindowListener(new WindowClosingEvent());

    }

    public void Setup() {

        dispose();
        setUndecorated(false);
        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.8);
        int height = (int) (screen_size.height * 0.8);
        setSize(width, height);
        setMinimumSize(new Dimension(width, height));
        setLocationRelativeTo(null);
        setVisible(true);

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
        record_service_button.addActionListener(e -> showUI(new ServiceEditUI()));

        part_edits_button.putClientProperty(FlatClientProperties.STYLE_CLASS, "actionButton");
        customer_list_button.putClientProperty(FlatClientProperties.STYLE_CLASS, "actionButton");
        record_service_button.putClientProperty(FlatClientProperties.STYLE_CLASS, "actionButton");
    }

    private void showUI(JDialog dialog) {
        dialog.setModal(true);
        dialog.setVisible(true);
    }

    public void closeApplication() {
        Object[] options = {"Evet", "Hayır"};

        int choice = JOptionPane.showOptionDialog(
                null,
                "Uygulamayı kapatmak istiyor musunuz?",
                "Onay",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, // Varsayılan simge (null)
                options, // Özelleştirilmiş buton metinleri
                options[0] // Varsayılan seçim ("Evet")
        );

        if (choice == JOptionPane.YES_OPTION) {
            System.exit(1);
        }
    }
}
