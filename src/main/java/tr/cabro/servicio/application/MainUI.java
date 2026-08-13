package tr.cabro.servicio.application;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Drawer;
import raven.modal.ModalDialog;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.settings.AppSettings;

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
        User user = new User();
        MyDrawerBuilder.getInstance().setUser(user);
        FormManager.install(this);

        Image logo = new ImageIcon(getClass().getResource("/logo.png")).getImage();

        setIconImage(logo);
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
        if (AppSettings.get().getUi().isFullSize()) {
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
        boolean skipDialog = AppSettings.get().getUi().isSkipExitConfirmation();

        if (skipDialog) {
            Servicio.getInstance().shutdown();
            return;
        }

        // DIYALOG OLUŞTURMA
        JPanel panel = new JPanel(new MigLayout("wrap, insets 20 30 10 30, gapy 10"));
        panel.setOpaque(false);
        panel.add(new JLabel(Messages.get("confirm.exit.app")));

        JCheckBox chkDontAsk = new JCheckBox(Messages.get("exit.dontAskAgain"));
        panel.add(chkDontAsk);

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option(Messages.get("exit.button.yes"), SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option(Messages.get("exit.button.cancel"), SimpleModalBorder.CANCEL_OPTION)
        };

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.WARNING,
                panel, Messages.get("confirm.exit.title"), options, (controller, action) -> {
            if (action != SimpleModalBorder.YES_OPTION) return;

            // Ayarı kaydet
            if (chkDontAsk.isSelected()) {
                AppSettings.get().getUi().setSkipExitConfirmation(true);
                // Kaydetme işini Servicio.shutdown() yapacak, burada set etmek yeterli
            }

            // Uygulamayı kapat
            Servicio.getInstance().shutdown();
        }));
        // "Hayır" veya pencere kapatılırsa hiçbir şey yapma, uygulama açık kalır.
    }
}