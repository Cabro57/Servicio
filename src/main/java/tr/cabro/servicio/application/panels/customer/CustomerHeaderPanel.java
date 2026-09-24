package tr.cabro.servicio.application.panels.customer;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.Badge;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.contract.Visualizable;
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

    /** Sorunlu müşteri uyarısı; Visualizable olarak tanımlı ki rozet tema değişiminde rengini yenilesin. */
    private static final Visualizable PROBLEMATIC = new Visualizable() {
        @Override public String getDisplayName() { return "Sorunlu Müşteri"; }
        @Override public String getIconPath() { return "icons/triangle-alert.svg"; }
        @Override public BadgeColor getBadgeColor() { return BadgeColor.RED; }
    };

    private final JLabel lblName = new JLabel();
    private final Badge typeBadge = new Badge(CustomerType.BIREYSEL).setShowIcon(true);
    private final Badge problemBadge = new Badge(PROBLEMATIC).setShowIcon(true);
    private final JLabel lblMeta = new JLabel();
    private final JLabel lblWarning = new JLabel();
    private final JLabel lblBalance = new JLabel("—");
    private final JButton btnWhatsApp;

    public CustomerHeaderPanel(Runnable onBack, Runnable onWhatsApp, Runnable onEdit,
                               Runnable onCollect, Runnable onNewService) {
        setLayout(new MigLayout("insets 14 16 14 20, fillx, gapx 14", "[][grow, fill][][]", "[center][]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

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
        // Tip ve sorunlu rozetleri tablolardaki durum rozetleriyle aynı bileşen (ikonlu, tema renkli);
        // eskiden isim etiketinin içinde HTML span olarak çiziliyordu.
        JPanel nameRow = new JPanel(new MigLayout("insets 0, gap 10", "[][][]", "[center]"));
        nameRow.setOpaque(false);
        nameRow.add(lblName, "wmin 0");
        nameRow.add(typeBadge);
        nameRow.add(problemBadge);
        identity.add(nameRow, "wmin 0");
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

        JButton btnNewService = new JButton("Yeni Servis", new Ikon("icons/plus.svg", 0.9f, "Servicio.onAccentForeground"));
        btnNewService.putClientProperty(FlatClientProperties.STYLE,
                "background: $Component.accentColor; foreground: $Servicio.onAccentForeground; arc: 10; margin: 7,14,7,14; font: bold");
        btnNewService.addActionListener(e -> onNewService.run());

        JPanel actions = new JPanel(new MigLayout("insets 0, gap 8", "[][][][]", "[]"));
        actions.setOpaque(false);
        actions.add(btnWhatsApp);
        actions.add(btnEdit);
        actions.add(btnCollect);
        actions.add(btnNewService);
        add(actions, "wrap");

        // Sorunlu müşteri yalnızca küçük bir rozetle gözden kaçabiliyordu; işlem yapmadan önce
        // okunsun diye tam genişlikte, kırmızı bir uyarı satırı.
        lblWarning.setIcon(new Ikon("icons/triangle-alert.svg", 0.9f, "Servicio.dangerColor"));
        lblWarning.setIconTextGap(8);
        lblWarning.putClientProperty(FlatClientProperties.STYLE,
                "font: bold; foreground: " + SemanticColor.hex(SemanticColor.danger()));
        lblWarning.setVisible(false);
        add(lblWarning, "skip 1, span 3, wmin 0, gaptop 6, hidemode 3");
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
        lblName.setText(name);
        lblName.setToolTipText(name);
        typeBadge.setVisualizable(customer.getType() != null ? customer.getType() : CustomerType.BIREYSEL);
        problemBadge.setVisible(customer.isProblematic());

        boolean hasNote = customer.getNote() != null && !customer.getNote().isBlank();
        // Notun tamamı soldaki Notlar kartında; burada tekrarlanmaz, oraya yönlendirilir.
        String warning = "Bu müşteri sorunlu olarak işaretli." + (hasNote ? "  İşlem yapmadan önce müşteri notunu okuyun." : "");
        lblWarning.setText(warning);
        lblWarning.setToolTipText(hasNote ? customer.getNote().trim() : null);
        lblWarning.setVisible(customer.isProblematic());
        problemBadge.setToolTipText(hasNote ? customer.getNote().trim() : null);

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

}
