package tr.cabro.servicio.application.panels.customer;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Müşteri detayının kimlik şeridi: kim olduğu, ne kadar borcu olduğu ve sık yapılan işlemler
 * her bölümde aynı yerde durur. Eski düzende iletişim bilgisi dar bir kolonda dört satır
 * kaplıyor, bakiye sayfanın en altında kalıyordu.
 */
public class CustomerHeaderPanel extends JPanel {

    private final JLabel lblName = new JLabel();
    private final JLabel lblMeta = new JLabel();
    private final JLabel lblBalance = new JLabel("—");
    private final JButton btnWhatsApp;

    public CustomerHeaderPanel(Runnable onBack, Runnable onWhatsApp, Runnable onEdit,
                               Runnable onCollect, Runnable onNewService) {
        setLayout(new MigLayout("insets 14 16 14 20, fillx, gapx 14", "[][grow, fill][][]", "[center]"));
        putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");

        JButton btnBack = new JButton(new Ikon("icons/arrow-left.svg", 1.1f));
        btnBack.setToolTipText("Geri");
        btnBack.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnBack.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 8,8,8,8");
        btnBack.addActionListener(e -> onBack.run());
        add(btnBack, "aligny center");

        lblName.putClientProperty(FlatClientProperties.STYLE, "font: bold +6");
        lblMeta.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        JPanel identity = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx", "[grow, fill]", "[]4[]"));
        identity.setOpaque(false);
        identity.add(lblName, "wmin 0");
        identity.add(lblMeta, "wmin 0");
        add(identity, "wmin 0");

        JLabel lblBalanceCaption = new JLabel("Bakiye");
        lblBalanceCaption.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        lblBalanceCaption.setHorizontalAlignment(SwingConstants.TRAILING);
        lblBalance.putClientProperty(FlatClientProperties.STYLE, "font: bold +6");
        lblBalance.setHorizontalAlignment(SwingConstants.TRAILING);
        JPanel balance = new JPanel(new MigLayout("insets 0 0 0 6, gap 0, wrap", "[right]", "[]2[]"));
        balance.setOpaque(false);
        balance.add(lblBalanceCaption);
        balance.add(lblBalance);
        add(balance);

        btnWhatsApp = secondaryButton("WhatsApp", "icons/message-circle.svg", onWhatsApp);
        JButton btnEdit = secondaryButton("Düzenle", "icons/user-pen.svg", onEdit);
        JButton btnCollect = secondaryButton("Tahsilat Al", "icons/hand-coins.svg", onCollect);

        JButton btnNewService = new JButton("Yeni Servis", new Ikon("icons/plus.svg", 0.9f, "Button.default.foreground"));
        btnNewService.putClientProperty(FlatClientProperties.STYLE,
                "background: $Component.accentColor; foreground: $Button.default.foreground; arc: 10; margin: 7,14,7,14; font: bold");
        btnNewService.addActionListener(e -> onNewService.run());

        JPanel actions = new JPanel(new MigLayout("insets 0, gap 8", "[][][][]", "[]"));
        actions.setOpaque(false);
        actions.add(btnWhatsApp);
        actions.add(btnEdit);
        actions.add(btnCollect);
        actions.add(btnNewService);
        add(actions);
    }

    private static JButton secondaryButton(String text, String iconPath, Runnable action) {
        JButton button = new JButton(text, new Ikon(iconPath, 0.85f));
        button.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,12,7,12; iconTextGap: 6");
        button.addActionListener(e -> action.run());
        return button;
    }

    public void setCustomer(Customer customer) {
        if (customer == null) return;
        boolean hasBusinessName = customer.getBusinessName() != null && !customer.getBusinessName().isBlank();
        String name = (customer.getType() == CustomerType.KURUMSAL && hasBusinessName)
                ? customer.getBusinessName()
                : customer.getFullName();
        String typeName = customer.getType() != null ? customer.getType().getDisplayName() : "Bireysel";

        // Rozet renkleri tema token'ından gelir; sabit hex açık temada kara blok gibi duruyordu.
        StringBuilder html = new StringBuilder("<html><span>").append(escape(name)).append("</span>&nbsp;&nbsp;")
                .append(badge(BadgeColor.GRAY, escape(typeName), false));
        if (customer.isProblematic()) {
            html.append("&nbsp;").append(badge(BadgeColor.RED, "&#9888; Sorunlu Müşteri", true));
        }
        lblName.setText(html.append("</html>").toString());
        lblName.setToolTipText(name);

        // İletişim bilgileri tek satırda; boş alanlar yazılmaz, satır dar ekranda kesilir, tamamı ipucunda.
        List<String> parts = new ArrayList<>();
        String phone = customer.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(customer.getPhoneNumber1()) : null;
        addIfPresent(parts, phone);
        addIfPresent(parts, customer.getEmail());
        addIfPresent(parts, customer.getAddress());
        if (customer.getCreatedAt() != null) {
            parts.add("Kayıt: " + customer.getCreatedAt().format(DateFormats.shortDate()));
        }
        String meta = parts.isEmpty() ? "İletişim bilgisi girilmemiş" : String.join("   ·   ", parts);
        lblMeta.setText(meta);
        lblMeta.setToolTipText(meta);

        boolean hasPhone = customer.getPhoneNumber1() != null && !customer.getPhoneNumber1().isBlank();
        btnWhatsApp.setEnabled(hasPhone);
        btnWhatsApp.setToolTipText(hasPhone ? null : "Müşterinin telefon numarası kayıtlı değil");
    }

    /** Kırmızı yalnızca gerçekten borç varken; sıfır bakiye nötr, alacak (eksi) yeşil okunur. */
    public void setBalance(BigDecimal balance) {
        BigDecimal value = balance != null ? balance : BigDecimal.ZERO;
        lblBalance.setText(Format.formatPrice(value));
        String color;
        if (value.signum() > 0) {
            color = SemanticColor.hex(SemanticColor.danger());
        } else if (value.signum() < 0) {
            color = SemanticColor.hex(SemanticColor.success());
        } else {
            color = "$Label.foreground";
        }
        lblBalance.putClientProperty(FlatClientProperties.STYLE, "font: bold +6; foreground: " + color);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) parts.add(value.trim());
    }

    private static String badge(BadgeColor color, String text, boolean bold) {
        return "<span style='background-color:" + BadgePalette.backgroundHex(color)
                + "; color:" + BadgePalette.foregroundHex(color)
                + "; font-size:11px; font-weight:" + (bold ? "bold" : "normal") + ";'>&nbsp;" + text + "&nbsp;</span>";
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
