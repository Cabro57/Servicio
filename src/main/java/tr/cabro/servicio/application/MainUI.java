package tr.cabro.servicio.application;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Drawer;
import raven.modal.menu.MyDrawerBuilder;
import raven.modal.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.manager.UpdateManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainUI extends JFrame {

    public MainUI() {
        initUI();
        setupWindowSize();
        setupCloseHandler();
    }

    private void initUI() {
        // FlatLaf pencere dekorasyonlarını (Title bar) etkinleştirir
        getRootPane().putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);

        Drawer.installDrawer(this, MyDrawerBuilder.getInstance());
        FormManager.install(this);
    }

    private void setupWindowSize() {
        // MODERN EKRAN HESAPLAMASI
        // Görev çubuğu vs. düşüldükten sonraki net alanı alır.
        GraphicsConfiguration config = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle usableBounds = config.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(config);

        int screenWidth = usableBounds.width - screenInsets.left - screenInsets.right;
        int screenHeight = usableBounds.height - screenInsets.top - screenInsets.bottom;

        int width = (int) (screenWidth * 0.8);
        int height = (int) (screenHeight * 0.8);

        setSize(width, height);
        setMinimumSize(new Dimension(1024, 768)); // Makul bir minimum boyut
        setLocationRelativeTo(null); // Ortala

        // Eğer ayarlarda tam ekran kayıtlıysa
        if (Servicio.getSettings().isFull_size()) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    private void setupCloseHandler() {
        // Pencere kapatma işlemini tamamen kendimiz yönetiyoruz
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                attemptExit();
            }
        });
    }

    /**
     * Çıkış işlemini dener. Kullanıcı onayı gerekiyorsa sorar.
     */
    public void attemptExit() {
        // Eğer "Onay sorma" (skip confirmation) ayarı TRUE ise direkt kapat
        boolean skipDialog = Servicio.getSettings().isSkipExitConfirmation();

        if (skipDialog) {
            Servicio.getInstance().shutdown();
            return;
        }

        // DIYALOG OLUŞTURMA
        JPanel panel = new JPanel(new MigLayout("wrap, insets 0, gapy 10"));
        panel.add(new JLabel("Uygulamadan çıkmak istediğinize emin misiniz?"));

        JCheckBox chkDontAsk = new JCheckBox("Bunu bir daha sorma");
        panel.add(chkDontAsk);

        int choice = JOptionPane.showOptionDialog(
                this,
                panel,
                "Çıkışı Onayla",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[]{"Evet, Çık", "İptal"},
                "Evet, Çık"
        );

        if (choice == JOptionPane.YES_OPTION) {
            // Ayarı kaydet
            if (chkDontAsk.isSelected()) {
                Servicio.getSettings().setSkipExitConfirmation(true);
                // Kaydetme işini Servicio.shutdown() yapacak, burada set etmek yeterli
            }

            // Uygulamayı kapat
            Servicio.getInstance().shutdown();
        }
        // "Hayır" veya pencere kapatılırsa hiçbir şey yapma, uygulama açık kalır.
    }
}