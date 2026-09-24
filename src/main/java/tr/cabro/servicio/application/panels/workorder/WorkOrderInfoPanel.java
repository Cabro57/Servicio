package tr.cabro.servicio.application.panels.workorder;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.forms.FormCustomer;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * İş emri sayfasının sağ rayı: gövdeyle birlikte kaymaz, para ve dosya bilgisi hep görünür.
 * Üstten alta: tutar kartı (kalan, toplam, ödenen, "Tahsilat Al"), dosya kartı (müşteri + cihaz,
 * erişim kodu) ve zaman kartı (geliş, durum değişimi, tahmini bitiş, teslim).
 * Arıza/tespit artık gövdedeki "1 Arıza ve tespit" adımında ({@link WorkOrderDiagnosisPanel}).
 */
public class WorkOrderInfoPanel extends JPanel {

    private WorkOrder workOrder;

    // --- Tutar kartı ---
    private JLabel lblRemainCaption, lblRemain, lblTotal, lblPaid;
    private JButton btnCollect;

    // --- Dosya kartı ---
    private JLabel lblCustomerName, lblCustomerPhone, lblCustomerEmail;
    private JButton btnGoCustomer;
    private JLabel lblDeviceType, lblDeviceBrand, lblDeviceModel, lblDeviceSerial;
    private JLabel lblDeviceAccess;
    private JButton btnRevealAccess;
    private String currentAccessTypeLabel;
    private String currentAccessSecret; // düz metin — sadece "göster"e basılınca gösterilir
    private boolean accessRevealed;

    // --- Zaman kartı ---
    private Timeline timeline;

    /**
     * @param onCollect "Tahsilat Al" düğmesi: gövdedeki ödeme adımına kaydırıp tutarı odaklar.
     */
    public WorkOrderInfoPanel(WorkOrder workOrder, Runnable onCollect) {
        this.workOrder = workOrder;
        setOpaque(false);
        setLayout(new MigLayout("insets 0, wrap, fillx, gapy 16", "[grow, fill]", ""));
        add(createMoneyCard(onCollect));
        add(createFileCard());
        add(createTimeCard());
    }

    /** Servis kaydı yeniden yüklendiğinde ({@code setService}/{@code formRefresh}) çağrılır. */
    public void refresh(WorkOrder updatedWorkOrder) {
        this.workOrder = updatedWorkOrder;
        refreshMoney();
        refreshFile();
        refreshTime();
    }

    // =========================================================================
    // TUTAR
    // =========================================================================

    private JPanel createMoneyCard(Runnable onCollect) {
        JPanel card = new JPanel(new MigLayout("insets 14 16 14 16, fillx, wrap, hidemode 3", "[grow, fill]", "[]2[]12[]14[]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        lblRemainCaption = WorkOrderPanelSupport.createCaption("Kalan");
        lblRemain = new JLabel("—");
        lblRemain.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");
        card.add(lblRemainCaption);
        card.add(lblRemain, "wmin 0");

        JPanel facts = DetailKit.facts();
        lblTotal = DetailKit.fact(facts, "Toplam");
        lblPaid = DetailKit.fact(facts, "Ödenen");
        card.add(facts);

        btnCollect = DetailKit.secondaryButton("Tahsilat Al", "icons/hand-coins.svg", onCollect);
        btnCollect.setToolTipText("Ödemeler adımındaki tahsilat tutarına git");
        card.add(btnCollect);
        return card;
    }

    /** Kalem ya da ödeme değişince yeniden okunur (orkestratör {@code hydrateMoney}'den çağırır). */
    public void refreshMoney() {
        BigDecimal total = workOrder.getTotalServiceAmount();
        BigDecimal paid = workOrder.getTotalPaid();
        BigDecimal remaining = workOrder.getRemainingAmount();

        // Alacak kuralı: kalan > 0 uyarı rengi, fazla ödeme tehlike, sıfır soluk "Ödendi".
        String remainColor;
        if (remaining.signum() > 0) {
            lblRemainCaption.setText("Kalan");
            lblRemain.setText(Format.formatPrice(remaining));
            remainColor = "$Servicio.warningColor";
        } else if (remaining.signum() < 0) {
            lblRemainCaption.setText("Fazla ödeme");
            lblRemain.setText(Format.formatPrice(remaining.negate()));
            remainColor = "$Servicio.dangerColor";
        } else {
            lblRemainCaption.setText("Kalan");
            lblRemain.setText(total.signum() > 0 ? "Ödendi" : "—");
            remainColor = "$Label.disabledForeground";
        }
        lblRemain.putClientProperty(FlatClientProperties.STYLE, "font: bold +8; foreground: " + remainColor);

        DetailKit.setFact(lblTotal, Format.formatPrice(total));
        DetailKit.setFact(lblPaid, Format.formatPrice(paid));
        lblPaid.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: "
                + (paid.signum() > 0 ? "$Servicio.successColor" : "$Label.foreground"));

        btnCollect.setEnabled(remaining.signum() > 0);
        revalidate();
        repaint();
    }

    // =========================================================================
    // DOSYA (MÜŞTERİ + CİHAZ)
    // =========================================================================

    private JPanel createFileCard() {
        btnGoCustomer = DetailKit.link("Müşteriye git", () -> {
            if (workOrder.getCustomer() != null) FormManager.showForm(new FormCustomer(workOrder.getCustomer()));
        });
        JPanel card = DetailKit.card("Müşteri", btnGoCustomer);

        lblCustomerName = new JLabel("—");
        lblCustomerName.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        lblCustomerPhone = new JLabel("—");
        lblCustomerEmail = DetailKit.muted(" ");
        JPanel contact = new JPanel(new MigLayout("insets 0, wrap, fillx, gap 0 2, hidemode 3", "[grow, fill]", ""));
        contact.setOpaque(false);
        contact.add(lblCustomerName, "wmin 0");
        contact.add(lblCustomerPhone, "wmin 0");
        contact.add(lblCustomerEmail, "wmin 0");
        card.add(contact);

        card.add(new JSeparator(), "gaptop 4, gapbottom 2");
        card.add(DetailKit.title("Cihaz"));

        JPanel facts = DetailKit.facts();
        lblDeviceType = DetailKit.fact(facts, "Tür");
        lblDeviceBrand = DetailKit.fact(facts, "Marka");
        lblDeviceModel = DetailKit.fact(facts, "Model");
        lblDeviceSerial = DetailKit.fact(facts, "Seri no");

        lblDeviceAccess = new JLabel("—");
        lblDeviceAccess.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        btnRevealAccess = new JButton(new Ikon("icons/eye.svg", 16, "Label.disabledForeground"));
        btnRevealAccess.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnRevealAccess.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 2,3,2,3");
        btnRevealAccess.setToolTipText("Erişim kodunu göster/gizle");
        btnRevealAccess.getAccessibleContext().setAccessibleName("Erişim kodunu göster veya gizle");
        btnRevealAccess.setVisible(false);
        btnRevealAccess.addActionListener(e -> toggleAccessReveal());
        JPanel accessRow = new JPanel(new MigLayout("insets 0, gap 4, hidemode 3", "push[][]", "[center]"));
        accessRow.setOpaque(false);
        accessRow.add(lblDeviceAccess, "wmin 0");
        accessRow.add(btnRevealAccess);
        facts.add(DetailKit.muted("Erişim kodu"));
        facts.add(accessRow, "growx, wmin 0, wrap");
        card.add(facts);
        return card;
    }

    private void refreshFile() {
        Customer c = workOrder.getCustomer();
        if (c != null) {
            lblCustomerName.setText(c.getFullName());
            boolean hasPhone = c.getPhoneNumber1() != null && !c.getPhoneNumber1().isBlank();
            lblCustomerPhone.setText(hasPhone ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : "Telefon yok");
            lblCustomerPhone.putClientProperty(FlatClientProperties.STYLE, hasPhone ? null : "foreground: $Label.disabledForeground");
            String email = c.getEmail();
            lblCustomerEmail.setText(email != null && !email.isBlank() ? email : "");
            lblCustomerEmail.setVisible(email != null && !email.isBlank());
            btnGoCustomer.setVisible(true);
        } else {
            lblCustomerName.setText("Müşterisiz kayıt");
            lblCustomerPhone.setText("—");
            lblCustomerEmail.setVisible(false);
            btnGoCustomer.setVisible(false);
        }

        Device device = workOrder.getDevice();
        DetailKit.setFact(lblDeviceType, device != null && device.getDeviceType() != null ? device.getDeviceType().getName() : null);
        DetailKit.setFact(lblDeviceBrand, device != null && device.getBrand() != null ? device.getBrand().getName() : null);
        DetailKit.setFact(lblDeviceModel, device != null ? device.getModel() : null);
        DetailKit.setFact(lblDeviceSerial, device != null ? device.getSerialNo() : null);
        loadDeviceAccessCredential();
    }

    /** Bu servis kaydına ait cihaz erişim kodunu (varsa) yükler; süresi dolup silindiyse bunu yazar. */
    private void loadDeviceAccessCredential() {
        lblDeviceAccess.setText("—");
        btnRevealAccess.setVisible(false);
        currentAccessSecret = null;
        currentAccessTypeLabel = null;
        accessRevealed = false;

        if (workOrder.getId() == null) return;

        ServiceManager.getDeviceAccessCredentialService().getActive(workOrder.getId())
                .thenAccept(credentialOpt -> SwingUtilities.invokeLater(() -> credentialOpt.ifPresent(c -> {
                    if (c.getSecret() == null) {
                        lblDeviceAccess.setText("Süresi doldu, silindi");
                        return;
                    }
                    currentAccessTypeLabel = c.getAccessType().getDisplayName();
                    currentAccessSecret = c.getSecret();
                    btnRevealAccess.setVisible(true);
                    updateAccessLabel();
                })))
                .exceptionally(ex -> {
                    Servicio.getLogger().error("Cihaz erişim kodu yüklenemedi", ex);
                    return null;
                });
    }

    private void toggleAccessReveal() {
        accessRevealed = !accessRevealed;
        updateAccessLabel();
    }

    private void updateAccessLabel() {
        if (currentAccessSecret == null) return;
        lblDeviceAccess.setText(currentAccessTypeLabel + ": " + (accessRevealed ? currentAccessSecret : "••••••"));
    }

    // =========================================================================
    // ZAMAN
    // =========================================================================

    private JPanel createTimeCard() {
        JPanel card = DetailKit.card("Zaman", null);
        timeline = new Timeline();
        card.add(timeline, "wmin 0");
        return card;
    }

    private void refreshTime() {
        DateTimeFormatter f = DateFormats.dateTime();
        LocalDateTime created = workOrder.getCreatedAt();
        LocalDateTime changed = workOrder.getStatusChangedAt();
        LocalDateTime delivered = workOrder.getDeliveryDate();
        LocalDateTime estimated = created != null ? created.plusDays(3) : null;
        LocalDateTime now = LocalDateTime.now();

        // Gerçekleşen olaylar tarih sırasıyla; tahmini bitiş kayıtlı bir tarih değil (geliş + 3 gün
        // varsayımı), bu yüzden hiçbir zaman "oldu" gösterilmez: soluk, boş halka. Teslimden sonra düşer.
        List<Timeline.Entry> entries = new ArrayList<>();
        java.util.Map<Timeline.Entry, LocalDateTime> when = new java.util.HashMap<>();
        if (created != null) put(entries, when, new Timeline.Entry("Geliş", created.format(f), true), created);
        if (changed != null) {
            String status = workOrder.getServiceStatus() != null ? workOrder.getServiceStatus().getDisplayName() : "Durum";
            put(entries, when, new Timeline.Entry(status, changed.format(f), true), changed);
        }
        if (delivered != null) {
            put(entries, when, new Timeline.Entry("Teslim", delivered.format(f), true), delivered);
        } else if (estimated != null) {
            put(entries, when, new Timeline.Entry("Tahmini bitiş", "~ " + estimated.format(DateFormats.shortDate()), false), estimated);
        }
        entries.sort(java.util.Comparator.comparing(when::get));
        if (delivered == null) entries.add(new Timeline.Entry("Teslim", "Teslim edilmedi", false));
        timeline.setEntries(entries);
    }

    private static void put(List<Timeline.Entry> list, java.util.Map<Timeline.Entry, LocalDateTime> when,
                            Timeline.Entry e, LocalDateTime at) {
        list.add(e);
        when.put(e, at);
    }

    /**
     * Dikey mini zaman çizgisi: solda noktalar ve onları bağlayan ince çizgi. Gerçekleşmiş olay
     * dolu nokta, henüz gelmemiş olay (tahmini bitiş, teslim) boş halka; değer de soluk yazılır.
     */
    private static final class Timeline extends JPanel {
        private static final int GUTTER = 20;
        private static final int DOT = 9;

        record Entry(String label, String value, boolean reached) {}

        private final List<Boolean> reached = new ArrayList<>();

        Timeline() {
            super(new MigLayout("insets 0 " + GUTTER + " 0 0, wrap, fillx, gap 12 10", "[shrink 0][grow, right]", ""));
            setOpaque(false);
        }

        void setEntries(List<Entry> entries) {
            removeAll();
            reached.clear();
            for (Entry e : entries) {
                add(DetailKit.muted(e.label()), "wmin 0");
                JLabel value = new JLabel(e.value());
                value.putClientProperty(FlatClientProperties.STYLE, e.reached()
                        ? "font: bold" : "foreground: $Label.disabledForeground");
                add(value, "wmin 0");
                reached.add(e.reached());
            }
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int count = reached.size();
            if (count == 0 || getComponentCount() < count * 2) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color line = UIManager.getColor("Component.borderColor");
            Color ink = UIManager.getColor("Label.foreground");
            float cx = DOT / 2f + 1;
            int[] cy = new int[count];
            for (int i = 0; i < count; i++) {
                Component row = getComponent(i * 2);
                cy[i] = row.getY() + row.getHeight() / 2;
            }
            g2.setColor(line);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new java.awt.geom.Line2D.Float(cx, cy[0], cx, cy[count - 1]));
            for (int i = 0; i < count; i++) {
                java.awt.geom.Ellipse2D.Float dot = new java.awt.geom.Ellipse2D.Float(cx - DOT / 2f, cy[i] - DOT / 2f, DOT, DOT);
                if (reached.get(i)) {
                    g2.setColor(ink);
                    g2.fill(dot);
                } else {
                    g2.setColor(getParent() != null ? getParent().getBackground() : getBackground());
                    g2.fill(dot);
                    g2.setColor(line);
                    g2.draw(dot);
                }
            }
            g2.dispose();
        }
    }
}
