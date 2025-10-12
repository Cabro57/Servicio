package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.ModalBorderAction;
import raven.modal.component.SimpleModalBorder;
import raven.modal.option.Option;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.SMenuBar;
import tr.cabro.servicio.application.util.SVGIconUIColor;

import javax.swing.*;
import java.util.Arrays;

public class PIN extends JPanel {

    public PIN() {
        pinField = new JPasswordField(4);

        initComponent();
    }

    public boolean verified() {
        char[] enteredPin = pinField.getPassword();
        char[] correctPin = String.valueOf(Servicio.getSettings().getPinConfig().getPin()).toCharArray();
        if (Arrays.equals(enteredPin, correctPin)) {
            return true;
        } else {
            pinField.setText("");
            pinField.requestFocus();
            return false;
        }
    }

    public static void showDialog() {
        PIN pinPanel = new PIN();
        String id = "pinModal";

        Option option = ModalDialog.createOption()
                        .setBackgroundClickType(Option.BackgroundClickType.BLOCK);

        ModalDialog.showModal(Servicio.getInstance().getFrame(), new SimpleModalBorder(pinPanel, "PIN", null,
            (controller, action) -> {
                boolean isVerified = pinPanel.verified();
                if (action == SimpleModalBorder.OPENED) {
                    Servicio.getInstance().getFrame().setJMenuBar(null);
                    Servicio.getInstance().getFrame().repaint();
                }
                if (action == SimpleModalBorder.CLOSE_OPTION) {
                    controller.consume();
                    System.exit(1);
                } else if (action == SimpleModalBorder.OK_OPTION) {
                    if (isVerified) {
                        controller.close();

                        Servicio.getInstance().getFrame().setJMenuBar(new SMenuBar());
                        Servicio.getInstance().getFrame().repaint();
                        Servicio.getInactivityListener().start();
                        Servicio.getLogger().info("İlk giriş başarılı. Hareketsizlik dinleyici başlatıldı ({} dakika).", Servicio.getSettings().getPinConfig().getTimeout());
                        Toast.show(Servicio.getInstance().getFrame(), Toast.Type.SUCCESS, "Giriş başarılı.");
                    } else {
                        controller.consume();
                        Servicio.getLogger().warn("İlk PIN girişi başarısız.");
                        Toast.show(Servicio.getInstance().getFrame(), Toast.Type.ERROR, "Hatalı PIN girişi.");
                    }
                }
            }),
        option, id);
    }

    private void initComponent() {
        setLayout(new MigLayout(
                "wrap, fill", "[center]", "[]10[]15[]"
        ));

        JLabel label = new JLabel("Lütfen 4 haneli PIN kodunuzu girin:");

        // Sadece PIN alanının genişliğini kontrol etmek için bir JPanel kullanıyoruz
        JPanel pinWrapper = new JPanel(new MigLayout("insets 0, fillx", "[center, grow]"));
        JButton verifiedButton = new JButton(new SVGIconUIColor("icon/ok.svg", 1f, "MenuItem.foreground"));
        verifiedButton.addActionListener(e -> {
            ModalBorderAction action = ModalBorderAction.getModalBorderAction(this);

            if (action != null) {
                action.doAction(SimpleModalBorder.OK_OPTION);
            }
        });

        pinField.setHorizontalAlignment(SwingConstants.CENTER);
        pinField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, verifiedButton);
        pinWrapper.add(pinField, "width 150::200"); // PIN alanını 150-200px arasında sınırla

        pinField.addActionListener(e -> {
            ModalBorderAction action = ModalBorderAction.getModalBorderAction(this);

            if (action != null) {
                action.doAction(SimpleModalBorder.OK_OPTION);
            }
        });

        add(label, "wrap");
        add(pinWrapper, "wrap, align center");
    }

    private final JPasswordField pinField;
}