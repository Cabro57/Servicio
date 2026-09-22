package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.math.BigDecimal;

/**
 * Müşteri seçicinin altında duran kısa özet: bakiye (müşteri detayıyla aynı renk kuralı),
 * isteğe bağlı kayıtlı cihaz sayısı ve sorunlu müşteride notuyla birlikte kırmızı uyarı satırı.
 * <p>
 * Servis kaydı ve 2.el alım/satım formlarında ortak kullanılır; müşteri seçilmemişken gizlidir.
 * Bakiye kendi içinde yüklenir; cihaz sayısını ise cihazları zaten yükleyen form
 * {@link #setDeviceCount(int)} ile bildirir.
 */
public class CustomerSummaryPanel extends JPanel {

    private final JLabel balanceLabel = new JLabel();
    private final JLabel deviceCountLabel = new JLabel();
    private final JPanel problemBanner;
    private final JLabel problemText = new JLabel();
    private int loadToken;

    public CustomerSummaryPanel() {
        super(new MigLayout("insets 0, gap 0, wrap, fillx, hidemode 3", "[grow, fill]", "[]8[]"));
        setOpaque(false);

        deviceCountLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        deviceCountLabel.setVisible(false);
        JPanel facts = new JPanel(new MigLayout("insets 0, gap 18, hidemode 3", "[][]", "[]"));
        facts.setOpaque(false);
        facts.add(balanceLabel);
        facts.add(deviceCountLabel);
        add(facts);

        problemText.putClientProperty(FlatClientProperties.STYLE, "foreground: $Servicio.dangerColor");
        problemBanner = new JPanel(new MigLayout("insets 8 10 8 10, gap 10", "[][grow, fill]", "[center]"));
        problemBanner.putClientProperty(FlatClientProperties.STYLE, "arc: 10; background: fade($Servicio.dangerColor, 12%)");
        problemBanner.add(new JLabel(new Ikon("icons/triangle-alert.svg", 0.9f, "Servicio.dangerColor")));
        problemBanner.add(problemText, "wmin 0");
        add(problemBanner);

        setVisible(false);
    }

    /** Müşteri değişince çağrılır; null gizler. Önceki müşterinin geç gelen bakiyesi yok sayılır. */
    public void setCustomer(Customer customer) {
        int token = ++loadToken;
        if (customer == null) {
            setVisible(false);
            FormKit.revalidateUp(this);
            return;
        }

        // Sorunlu müşteri: işlem yapmadan önce okunsun diye tam genişlikte, not ile birlikte.
        problemBanner.setVisible(customer.isProblematic());
        if (customer.isProblematic()) {
            String note = customer.getNote() != null ? customer.getNote().trim() : "";
            String shortNote = note.length() > 70 ? note.substring(0, 70) + "…" : note;
            problemText.setText("<html><b>Sorunlu müşteri.</b>" + (note.isEmpty() ? " İşlem yapmadan önce kontrol edin."
                    : " Not: " + escape(shortNote)) + "</html>");
            problemText.setToolTipText(note.isEmpty() ? null : note);
        }
        balanceLabel.setText("Bakiye yükleniyor…");
        balanceLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        deviceCountLabel.setVisible(false);
        setVisible(true);
        FormKit.revalidateUp(this);

        if (ServiceManager.getPaymentService() == null) return;
        ServiceManager.getPaymentService().getCustomerBalance(customer.getId())
                .thenAccept(dto -> SwingUtilities.invokeLater(() -> {
                    if (token != loadToken) return;
                    BigDecimal balance = dto.map(d -> d.getBalance()).orElse(BigDecimal.ZERO);
                    showBalance(balance != null ? balance : BigDecimal.ZERO);
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> { if (token == loadToken) balanceLabel.setText(""); });
                    Servicio.getLogger().error("Müşteri bakiyesi yüklenemedi", ex);
                    return null;
                });
    }

    public void setDeviceCount(int count) {
        deviceCountLabel.setText(count == 0 ? "Kayıtlı cihazı yok" : count + " kayıtlı cihaz");
        deviceCountLabel.setVisible(true);
    }

    /** Müşteri detayıyla aynı kural: kırmızı yalnızca borç varken, alacak yeşil, sıfır nötr. */
    private void showBalance(BigDecimal value) {
        String color;
        String text;
        if (value.signum() > 0) {
            color = "$Servicio.dangerColor";
            text = "Borç: " + Format.formatPrice(value);
        } else if (value.signum() < 0) {
            color = "$Servicio.successColor";
            text = "Alacak: " + Format.formatPrice(value.negate());
        } else {
            color = "$Label.foreground";
            text = "Borcu yok";
        }
        balanceLabel.setText(text);
        balanceLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: " + color);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
