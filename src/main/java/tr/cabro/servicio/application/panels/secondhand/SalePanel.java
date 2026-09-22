package tr.cabro.servicio.application.panels.secondhand;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.CustomerSelectBox;
import tr.cabro.servicio.application.component.CustomerSummaryPanel;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.SegmentedButtons;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Stoktaki bir ikinci el cihazın satış formu. Cihaz bellidir; formun başında alım bilgisiyle
 * birlikte bir kart olarak durur (ne zaman, kaça, kimden alındı, kaç gündür stokta).
 * <ul>
 *   <li>Alıcı seçilince bakiyesi ve sorunlu müşteri uyarısı görünür.</li>
 *   <li>Satış fiyatı yazıldıkça alış fiyatına göre kâr/zarar hesaplanır.</li>
 *   <li>Garanti süresi hazır seçeneklerle (Yok / 1 / 3 / 6 / 12 ay) ya da "Diğer" ile girilir;
 *       satış sözleşmesi ve garanti belgesinde kullanılır.</li>
 *   <li>Fiyat yalnızca TL: satış kaydı döviz tutmuyor (eskiden döviz seçilip kur uygulanmadan
 *       TL olarak kaydediliyordu).</li>
 * </ul>
 */
public class SalePanel extends JPanel {

    private static final int OTHER = -1;

    private final DeviceTransaction purchase;
    private final CustomerSelectBox buyerCombo;
    private final JLabel buyerError = FormKit.errorLabel();
    private final CustomerSummaryPanel buyerSummary = new CustomerSummaryPanel();
    private final CurrencyField priceField;
    private final JLabel priceError = FormKit.errorLabel();
    private final JLabel profitLabel = new JLabel();
    private final DatePicker datePicker;
    private final SegmentedButtons<Integer> warrantyButtons = new SegmentedButtons<>();
    private final JSpinner warrantyMonthsSpinner;
    private final JTextArea noteArea;

    /**
     * @param purchase satılacak cihazın alım kaydı (cihaz, alış fiyatı ve tarihi buradan okunur)
     */
    public SalePanel(DeviceTransaction purchase, ActionListener onNewCustomerRequested) {
        this.purchase = purchase;
        setLayout(new MigLayout("wrap 2, insets 16 20 12 20, fillx, hidemode 3, width 760:760:",
                "[" + FormKit.RAIL_WIDTH + "!]24[grow, fill]"));

        // --- Cihaz ---
        add(FormKit.rail("Cihaz", "Satılan cihaz ve alım bilgisi."), "top");
        add(buildDeviceCard());
        add(FormKit.separator(), "span 2, growx, gaptop 12, gapbottom 12");

        // --- Alıcı ---
        add(FormKit.rail("Alıcı", "Cihazı satın alan müşteri; belgelerde alıcı olarak yazılır."), "top");
        JPanel buyer = new JPanel(new MigLayout("insets 0, fillx, wrap, hidemode 3, gapy 0", "[grow, fill]", ""));
        buyer.setOpaque(false);
        buyerCombo = new CustomerSelectBox(onNewCustomerRequested);
        buyer.add(buyerCombo);
        buyer.add(buyerError, "gaptop 3");
        buyer.add(buyerSummary, "gaptop 8");
        add(buyer);
        add(FormKit.separator(), "span 2, growx, gaptop 12, gapbottom 12");

        // --- Satış ---
        add(FormKit.rail("Satış", "Garanti süresi satış sözleşmesine ve garanti belgesine yazılır."), "top");
        JPanel sale = FormKit.grid(2);
        priceField = new CurrencyField();
        priceField.setAvailableCurrencies(List.of("TRY"));
        sale.add(FormKit.cell("Satış Fiyatı (TL) *", priceField, priceError));
        JFormattedTextField dateField = new JFormattedTextField();
        datePicker = new DatePicker();
        datePicker.setDateFormat("dd/MM/yyyy");
        datePicker.setEditor(dateField);
        datePicker.now();
        sale.add(FormKit.cell("Satış Tarihi", dateField, null));
        profitLabel.setIconTextGap(6);
        sale.add(profitLabel, "span 2, wmin 0");

        warrantyButtons.add(0, "Yok", null).add(1, "1 ay", null).add(3, "3 ay", null)
                .add(6, "6 ay", null).add(12, "12 ay", null).add(OTHER, "Diğer", null);
        warrantyMonthsSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 60, 1));
        warrantyMonthsSpinner.setVisible(false);
        JPanel warranty = new JPanel(new MigLayout("insets 0, gap 10, hidemode 3", "[][60!][]", "[center]"));
        warranty.setOpaque(false);
        warranty.add(warrantyButtons);
        warranty.add(warrantyMonthsSpinner);
        JLabel monthsSuffix = FormKit.note("ay");
        monthsSuffix.setVisible(false);
        warranty.add(monthsSuffix);
        warrantyButtons.setOnChange(v -> {
            warrantyMonthsSpinner.setVisible(v == OTHER);
            monthsSuffix.setVisible(v == OTHER);
            if (v == OTHER) SwingUtilities.invokeLater(warrantyMonthsSpinner::requestFocusInWindow);
            FormKit.revalidateUp(warrantyMonthsSpinner);
        });
        sale.add(FormKit.cell("Garanti", warranty, null), "span 2");
        add(sale);
        add(FormKit.separator(), "span 2, growx, gaptop 12, gapbottom 12");

        // --- Not ---
        add(FormKit.rail("Not", "İsteğe bağlı; satışla ilgili hatırlatma."), "top");
        noteArea = FormKit.textArea(3);
        add(FormKit.areaScroll(noteArea));

        buyerCombo.setOnSelectionChanged(c -> {
            buyerSummary.setCustomer(c);
            if (c != null) {
                buyerCombo.setError(false);
                FormKit.clear(null, buyerError);
            }
        });
        buyerCombo.setAfterUserChoice(priceField::requestFocusInWindow);
        priceField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateProfit(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateProfit(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        priceField.addPropertyChangeListener("value", e -> {
            updateProfit();
            if (getPrice().signum() > 0) FormKit.clear(priceField, priceError);
        });
        updateProfit();
    }

    /** Cihaz adı, IMEI ve alım özeti; yalnızca okunur. */
    private JPanel buildDeviceCard() {
        Device device = purchase != null ? purchase.getDevice() : null;
        JPanel card = new JPanel(new MigLayout("insets 12 14 12 14, gap 12, fillx", "[][grow, fill]", "[]3[]3[]"));
        card.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: fade($Label.foreground, 4%)");
        card.add(new JLabel(new Ikon("icons/tablet-smartphone.svg", 1.4f)), "spany 3, aligny top");

        JLabel name = new JLabel(device != null ? device.getDisplayName() : "Cihaz bilgisi yok");
        name.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        card.add(name, "wrap, wmin 0");

        String serial = device != null && device.getSerialNo() != null && !device.getSerialNo().isBlank()
                ? "IMEI / Seri: " + device.getSerialNo() : "IMEI / seri no kayıtlı değil";
        card.add(FormKit.note(serial), "wrap, wmin 0");

        if (purchase != null) {
            StringBuilder s = new StringBuilder("Alış: ").append(Format.formatPrice(purchasePrice()));
            if (purchase.getTransactionDate() != null) {
                s.append("  ·  ").append(purchase.getTransactionDate().format(DateFormats.shortDate()));
                long days = ChronoUnit.DAYS.between(purchase.getTransactionDate().toLocalDate(), java.time.LocalDate.now());
                s.append("  ·  ").append(days <= 0 ? "bugün alındı" : days + " gündür stokta");
            }
            if (purchase.getCustomer() != null) {
                s.append("  ·  satıcı: ").append(purchase.getCustomer().getFullName());
            }
            JLabel info = new JLabel(s.toString());
            info.putClientProperty(FlatClientProperties.STYLE, "font: -1");
            card.add(info, "wmin 0");
        }
        return card;
    }

    private BigDecimal purchasePrice() {
        return purchase != null && purchase.getPrice() != null ? purchase.getPrice() : BigDecimal.ZERO;
    }

    /** Satış fiyatı yazıldıkça alışa göre kâr/zarar; değer odak çıkmadan da metinden okunur. */
    private void updateProfit() {
        BigDecimal sell = liveAmount();
        BigDecimal buy = purchasePrice();
        if (purchase == null || sell.signum() == 0) {
            profitLabel.setVisible(false);
            return;
        }
        BigDecimal profit = sell.subtract(buy);
        if (profit.signum() < 0) {
            profitLabel.setText("Alış fiyatının " + Format.formatPrice(profit.negate()) + " altında: zararına satış.");
            profitLabel.setIcon(new Ikon("icons/triangle-alert.svg", 0.8f, "Servicio.dangerColor"));
            profitLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold -1; foreground: $Servicio.dangerColor");
        } else {
            String pct = buy.signum() > 0
                    ? "  ·  alışa göre %" + profit.multiply(BigDecimal.valueOf(100)).divide(buy, 0, RoundingMode.HALF_UP)
                    : "";
            profitLabel.setText("Kâr: " + Format.formatPrice(profit) + pct);
            profitLabel.setIcon(new Ikon("icons/banknote-arrow-up.svg", 0.8f, "Servicio.successColor"));
            profitLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold -1; foreground: $Servicio.successColor");
        }
        profitLabel.setVisible(true);
        FormKit.revalidateUp(profitLabel);
    }

    private BigDecimal liveAmount() {
        try {
            Object v = priceField.getFormatter() != null ? priceField.getFormatter().stringToValue(priceField.getText()) : null;
            if (v instanceof Number n) return new BigDecimal(n.toString());
        } catch (java.text.ParseException ignored) {
            // Yarım yazılmış metin: son işlenmiş değere düş.
        }
        return getPrice();
    }

    /**
     * Zorunlu alanları (alıcı, fiyat) denetler, hataları alanların altında gösterir.
     *
     * @return form kaydedilebilir durumdaysa true
     */
    public boolean validateForm() {
        JComponent first = null;
        if (getBuyer() == null) {
            buyerCombo.setError(true);
            FormKit.fail(new JLabel(), buyerError, "Cihazı alan müşteriyi seçin ya da yeni müşteri ekleyin.");
            first = buyerCombo;
        }
        if (getPrice().signum() <= 0) {
            JComponent c = FormKit.fail(priceField, priceError, "Satış fiyatını girin.");
            if (first == null) first = c;
        }
        if (first != null) {
            first.requestFocusInWindow();
            return false;
        }
        return true;
    }

    public void setCustomers(List<Customer> customers) {
        buyerCombo.setCustomers(customers);
    }

    public void appendNewCustomer(Customer customer) {
        buyerCombo.appendCustomer(customer);
        SwingUtilities.invokeLater(priceField::requestFocusInWindow);
    }

    public Customer getBuyer() {
        return buyerCombo.getSelectedItem();
    }

    public BigDecimal getPrice() {
        return BigDecimal.valueOf(priceField.getDoubleValue());
    }

    public LocalDateTime getTransactionDate() {
        return datePicker.getSelectedDate() != null ? datePicker.getSelectedDate().atStartOfDay() : LocalDateTime.now();
    }

    /** Garanti süresi (ay); garantisizse null — belgeler null'ı "Garantisiz" yazar. */
    public Integer getWarrantyMonths() {
        Integer selected = warrantyButtons.getSelected();
        int months = selected == null ? 0 : selected == OTHER ? (Integer) warrantyMonthsSpinner.getValue() : selected;
        return months > 0 ? months : null;
    }

    public String getNote() {
        return noteArea.getText().trim();
    }

    public void requestInitialFocus() {
        buyerCombo.grabFocus();
    }
}
