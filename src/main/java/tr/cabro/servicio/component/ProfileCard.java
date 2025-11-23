package tr.cabro.servicio.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.extras.AvatarIcon;
import tr.cabro.servicio.model.Customer;

import javax.swing.*;
import java.awt.*;

public class ProfileCard extends JPanel {

    public ProfileCard() {
        init();
    }

    private void init() {
        initComponent();
    }

    public void setProfileData(Customer customer) {
        if (nameLabel != null) {
            nameLabel.setText(customer.toString());
        }
        if (emailPanel != null) {
            emailPanel.setValue(customer.getEmail());
        }
        if (addressPanel != null) {
            addressPanel.setValue(customer.getAddress());
        }
        if (phonePanel != null) {
            phonePanel.setValue(customer.getPhoneNumber1());
        }
        if (transactionPanel != null) {
            transactionPanel.setValue(customer.getCreatedAt().toString());
        }
    }

    private JPanel createTopBlock() {
        JPanel topBlock = new JPanel(new MigLayout("fillx, insets 0", "[grow][right]"));
        topBlock.setOpaque(false);

        JPanel nameStatusPanel = new JPanel(new MigLayout("wrap 1, insets 0"));
        nameStatusPanel.setOpaque(false);

        // İsim Label'ı başlatılıyor ve class değişkenine atanıyor
        nameLabel = new JLabel("");
        nameLabel.putClientProperty("FlatLaf.style", "font: bold +4");
        nameStatusPanel.add(nameLabel);

        JButton statusButton = new JButton("Active");
        statusButton.putClientProperty("FlatLaf.style",
                "background: $" + Integer.toHexString(Color.GREEN.getRGB()).substring(2) + ";" +
                        "foreground: #FFFFFF;" + "arc: 999;" + "font: bold;" + "borderWidth: 0;" + "focusWidth: 0;" + "innerFocusWidth: 0;"
        );
        // nameStatusPanel.add(statusButton, "gapy 5");

        JPanel iconPanel = new JPanel(new MigLayout("insets 0", "[]5[]"));
        iconPanel.setOpaque(false);

        JButton callButton = createIconButton("📞");
        JButton msgButton = createIconButton("💬");

//        iconPanel.add(callButton);
//        iconPanel.add(msgButton);

        topBlock.add(nameStatusPanel, "growx");
        topBlock.add(iconPanel, "aligny top");

        return topBlock;
    }

    private JButton createIconButton(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("FlatLaf.style",
                "background: $" + Integer.toHexString(button.getBackground().getRGB()).substring(2) + ";" +
                        "arc: 10;" + "font: +4;" + "minimumWidth: 36;" + "minimumHeight: 36;" + "borderWidth: 0;" + "focusWidth: 0;" + "innerFocusWidth: 0;"
        );
        return button;
    }

    private JPanel createInfoPanel(String labelText, String key, String initialValue) {
        JPanel panel = new JPanel(new MigLayout("wrap 1, insets 0 0 15 0"));
        panel.setOpaque(false);

        JLabel labelLabel = new JLabel(labelText);
        labelLabel.setForeground(Color.gray);
        labelLabel.putClientProperty("FlatLaf.style", "font: -2");
        panel.add(labelLabel);

        JLabel valueLabel = new JLabel(initialValue);
        valueLabel.putClientProperty("FlatLaf.style", "font: +1");
        panel.add(valueLabel, "gapy 2");

        return panel;
    }

    private void initComponent() {
        setLayout(new MigLayout("wrap 1, fillx, insets 160 25 25 25", "[fill]", ""));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");

        AvatarIcon icon = new AvatarIcon(new FlatSVGIcon("drawer/image/avatar_male.svg", 100, 100), 50, 50, 3.5f);
        icon.setType(AvatarIcon.Type.MASK_SQUIRCLE);
        icon.setBorder(2, 2);

        JLabel avatar = new JLabel(icon);

        add(avatar, "pos (container.w/2 - comp.w/2) 50, w " + 80 + "!, h " + 80 + "!");

        add(createTopBlock(), "growx, wrap");

        // Yeni InfoPanel kullanımı ve değişkenlere atama:
        emailPanel = new InfoItem("Email address", "");
        addressPanel = new InfoItem("Address", "");
        phonePanel = new InfoItem("Phone number", "");
        transactionPanel = new InfoItem("Last transaction", "");

        add(emailPanel, "growx, wrap");
        add(addressPanel, "growx, wrap");
        add(phonePanel, "growx, wrap");
        add(transactionPanel, "growx, wrap");
    }

//    private final Color flatAccent = UIManager.getColor("Component.accentColor");
//    private final Color gradientStart = (flatAccent != null) ? flatAccent.brighter().brighter() : new Color(190, 100, 255);
//    private final Color gradientEnd = (flatAccent != null) ? flatAccent.darker().darker() : new Color(100, 150, 255);
//    private final int headerHeight = 100;
//
//    @Override
//    protected void paintComponent(Graphics g) {
//        super.paintComponent(g);
//        Graphics2D g2d = (Graphics2D) g;
//
//        // Gradient (FlatLaf vurgu rengi ile)
//        GradientPaint gradient = new GradientPaint(
//                0, 0, gradientStart,
//                getWidth(), 0, gradientEnd
//        );
//        g2d.setPaint(gradient);
//        g2d.fillRect(0, 0, getWidth(), headerHeight);
//
//        // Alt Düz Koyu Renk
//        g2d.setColor(getBackground());
//        g2d.fillRect(0, headerHeight, getWidth(), getHeight() - headerHeight);
//    }

    private JLabel nameLabel;
    private InfoItem emailPanel;
    private InfoItem addressPanel;
    private InfoItem phonePanel;
    private InfoItem transactionPanel;
}
