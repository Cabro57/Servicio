package tr.cabro.servicio.application;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Drawer;
import raven.modal.system.FormManager;
import raven.modal.menu.MyDrawerBuilder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.listeners.WindowClosingEvent;

import javax.swing.*;
import java.awt.*;

public class MainUI extends JFrame {

    public MainUI() {
        init();

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.8);
        int height = (int) (screen_size.height * 0.8);
        setSize(width, height);
        setMinimumSize(new Dimension(width, height));
        setLocationRelativeTo(null);

    }

    private void init() {

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        getRootPane().putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
        Drawer.installDrawer(this, MyDrawerBuilder.getInstance());
        FormManager.install(this);

        addWindowListener(new WindowClosingEvent());
    }

    public boolean closeApplication() {

        if (Servicio.getSettings().isConfirmExitDialog()) {
            //System.exit(0);
            return true;
        }

        JPanel panel = new JPanel(new MigLayout("wrap,insets 0"));

        JLabel label = new JLabel("Çıkmak istediğinizden emin misiniz?");
        panel.add(label, "wrap");

        JCheckBox donTAskAgain = new JCheckBox("Bir daha sorma");
        panel.add(donTAskAgain, "wrap");


        Object[] options = {"Evet", "Hayır"};

        int choice = JOptionPane.showOptionDialog(
                this,
                panel,
                "Çıkışı Onayla",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, // Varsayılan simge (null)
                options, // Özelleştirilmiş buton metinleri
                options[0] // Varsayılan seçim ("Evet")
        );

        if (choice == JOptionPane.YES_OPTION) {
            if (donTAskAgain.isSelected()) {
                Servicio.getSettings().setConfirmExitDialog(true);
                Servicio.getSettings().save();
            }

            //System.exit(1);
            return true;
        }
        return false;
    }

}
