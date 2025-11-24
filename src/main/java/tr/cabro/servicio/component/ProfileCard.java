package tr.cabro.servicio.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.extras.AvatarIcon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.util.Format;

import javax.swing.*;

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
        if (email != null) {
            email.setValue(customer.getEmail());
        }
        if (address != null) {
            address.setValue(customer.getAddress());
        }
        if (phoneNo != null) {
            phoneNo.setValue(Format.formatPhoneNumber(customer.getPhoneNumber1()));
        }
        if (createdAt != null) {
            createdAt.setValue(Format.formatDate(customer.getCreatedAt()));
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
        email = new InfoItem("E-Posta", "");
        address = new InfoItem("Adres", "");
        phoneNo = new InfoItem("Telefon Numarası", "");
        createdAt = new InfoItem("Kayıt Tarihi", "");

        add(email, "growx, wrap");
        add(address, "growx, wrap");
        add(phoneNo, "growx, wrap");
        add(createdAt, "growx, wrap");
    }


    private JLabel nameLabel;
    private InfoItem email;
    private InfoItem address;
    private InfoItem phoneNo;
    private InfoItem createdAt;
}
