package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Parça ve ürün formlarının ortak fiyat bölümü: alış ve satış tutarı (her biri kendi para
 * birimiyle), dövizse TL karşılığı ve canlı kâr göstergesi.
 * <p>
 * Eskiden bu mantık {@code PartEditPanel} ve {@code ProductEditPanel}'de birebir kopyaydı.
 * Tutar her zaman seçili para biriminin ham değeridir; TL karşılığı Ayarlar &gt; Döviz Kurları'ndaki
 * kurla hesaplanır. Döviz seçili ama kuru tanımlı değilse {@link #validateRates()} alanı işaretler.
 * <p>
 * Kâr göstergesi yazarken güncellenir (alanın değeri odak çıkınca işlense de metin anlık okunur):
 * kâr tutarı ve alışa göre yüzdesi; satış alıştan düşükse tehlike rengiyle uyarır.
 */
public class PriceFields extends JPanel {

    private final Map<String, BigDecimal> rates = new HashMap<>();

    private final CurrencyField purchaseField = new CurrencyField();
    private final CurrencyField saleField = new CurrencyField();
    private final JLabel purchaseNote = FormKit.note(" ");
    private final JLabel saleNote = FormKit.note(" ");
    private final JLabel purchaseError = FormKit.errorLabel();
    private final JLabel saleError = FormKit.errorLabel();
    private final JLabel profitLabel = new JLabel();

    public PriceFields() {
        super(new MigLayout("insets 0, fillx, wrap 2, hidemode 3, gapx 14, gapy 8",
                "[grow, fill, sg col][grow, fill, sg col]", "[top]"));
        setOpaque(false);

        add(FormKit.cell("Alış Fiyatı", purchaseField, stack(purchaseNote, purchaseError)));
        add(FormKit.cell("Satış Fiyatı", saleField, stack(saleNote, saleError)));

        profitLabel.setIconTextGap(6);
        add(profitLabel, "span 2, wmin 0");

        purchaseField.addCurrencyChangeListener(this::onCurrencyChanged);
        saleField.addCurrencyChangeListener(this::onCurrencyChanged);
        watch(purchaseField);
        watch(saleField);
        loadRates();
        refresh();
    }

    private static JPanel stack(JComponent a, JComponent b) {
        JPanel p = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx, hidemode 3", "[grow, fill]", "[][]"));
        p.setOpaque(false);
        p.add(a, "wmin 0");
        p.add(b, "wmin 0");
        return p;
    }

    private void watch(CurrencyField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        field.addPropertyChangeListener("value", e -> refresh());
    }

    private void onCurrencyChanged() {
        FormKit.clear(purchaseField, purchaseError);
        FormKit.clear(saleField, saleError);
        refresh();
    }

    private void loadRates() {
        if (ServiceManager.getExchangeRateManager() == null) return;
        ServiceManager.getExchangeRateManager().getAll().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            rates.clear();
            list.forEach(r -> rates.put(r.getCurrencyCode(), r.getRate()));
            refresh();
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Döviz kurları yüklenemedi", ex);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Hesap
    // -------------------------------------------------------------------------

    /** Alanın o anki metnini sayıya çevirir; değer henüz işlenmemiş (odak içinde) olsa da çalışır. */
    private static BigDecimal amountOf(CurrencyField field) {
        try {
            Object v = field.getFormatter() != null ? field.getFormatter().stringToValue(field.getText()) : field.getValue();
            if (v instanceof Number n) return new BigDecimal(n.toString());
        } catch (java.text.ParseException ignored) {
            // Yarım yazılmış metin: son işlenmiş değere düş.
        }
        Object v = field.getValue();
        return v instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
    }

    /** TL karşılığı; döviz kuru tanımlı değilse null. */
    private BigDecimal tryAmount(CurrencyField field) {
        BigDecimal amount = amountOf(field);
        String code = field.getSelectedCurrency();
        if ("TRY".equals(code)) return amount;
        BigDecimal rate = rates.get(code);
        return rate != null && rate.signum() > 0 ? amount.multiply(rate) : null;
    }

    private void refresh() {
        updateNote(purchaseField, purchaseNote);
        updateNote(saleField, saleNote);

        BigDecimal buy = tryAmount(purchaseField);
        BigDecimal sell = tryAmount(saleField);
        if (buy == null || sell == null || sell.signum() == 0) {
            profitLabel.setVisible(false);
            return;
        }
        BigDecimal profit = sell.subtract(buy);
        if (profit.signum() < 0) {
            profitLabel.setText("Satış, alıştan " + Format.formatPrice(profit.negate()) + " düşük: zararına satış.");
            profitLabel.setIcon(new Ikon("icons/triangle-alert.svg", 0.8f, "Servicio.dangerColor"));
            profitLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold -1; foreground: $Servicio.dangerColor");
        } else {
            String pct = buy.signum() > 0
                    ? "  ·  alışa göre %" + profit.multiply(BigDecimal.valueOf(100)).divide(buy, 0, RoundingMode.HALF_UP)
                    : "";
            profitLabel.setText("Birim kâr: " + Format.formatPrice(profit) + pct);
            profitLabel.setIcon(new Ikon("icons/banknote-arrow-up.svg", 0.8f, "Servicio.successColor"));
            profitLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold -1; foreground: $Servicio.successColor");
        }
        profitLabel.setVisible(true);
    }

    private void updateNote(CurrencyField field, JLabel note) {
        String code = field.getSelectedCurrency();
        if ("TRY".equals(code)) {
            note.setText(" ");
            note.setVisible(false);
            return;
        }
        BigDecimal rate = rates.get(code);
        note.setVisible(true);
        note.setText(rate == null || rate.signum() <= 0
                ? "Bu döviz için kur tanımlı değil (Ayarlar > Döviz Kurları)"
                : "≈ " + Format.formatPrice(amountOf(field).multiply(rate)) + "  (1 " + code + " = " + Format.formatPrice(rate) + ")");
    }

    // -------------------------------------------------------------------------
    // Dışa açık API
    // -------------------------------------------------------------------------

    /**
     * Dövizli bir fiyatın kuru tanımlı değilse alanı işaretler.
     *
     * @return ilk hatalı alan; sorun yoksa null
     */
    public JComponent validateRates() {
        JComponent first = null;
        if (tryAmount(purchaseField) == null) {
            first = FormKit.fail(purchaseField, purchaseError, "Kur tanımlı değil; TL seçin ya da önce kuru girin.");
        }
        if (tryAmount(saleField) == null) {
            JComponent c = FormKit.fail(saleField, saleError, "Kur tanımlı değil; TL seçin ya da önce kuru girin.");
            if (first == null) first = c;
        }
        return first;
    }

    /**
     * Kayıtlı fiyatı alanlara yazar: döviz seçiliyse ham tutar gösterilir, değilse TL fiyatı.
     */
    public void setPurchase(String currency, BigDecimal original, BigDecimal tryPrice) {
        populate(purchaseField, currency, original, tryPrice);
    }

    public void setSale(String currency, BigDecimal original, BigDecimal tryPrice) {
        populate(saleField, currency, original, tryPrice);
    }

    private void populate(CurrencyField field, String currency, BigDecimal original, BigDecimal tryPrice) {
        if (currency != null && !"TRY".equals(currency) && original != null) {
            field.setSelectedCurrency(currency);
            field.setValue(original.doubleValue());
        } else {
            field.setSelectedCurrency("TRY");
            field.setValue(tryPrice != null ? tryPrice.doubleValue() : 0.0);
        }
        refresh();
    }

    public String getPurchaseCurrency() { return purchaseField.getSelectedCurrency(); }
    public String getSaleCurrency() { return saleField.getSelectedCurrency(); }

    /** Dövizli fiyatın ham tutarı; TL seçiliyse null (kayıtta "original" alanı boş kalır). */
    public BigDecimal getPurchaseOriginal() { return originalOf(purchaseField); }
    public BigDecimal getSaleOriginal() { return originalOf(saleField); }

    /** Kanonik TL fiyatı. Kur tanımlı değilse 0 — kayıttan önce {@link #validateRates()} çağrılmalı. */
    public BigDecimal getPurchaseTry() { return orZero(tryAmount(purchaseField)); }
    public BigDecimal getSaleTry() { return orZero(tryAmount(saleField)); }

    private static BigDecimal originalOf(CurrencyField field) {
        return "TRY".equals(field.getSelectedCurrency()) ? null : amountOf(field);
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public void clear() {
        setPurchase("TRY", null, BigDecimal.ZERO);
        setSale("TRY", null, BigDecimal.ZERO);
        FormKit.clear(purchaseField, purchaseError);
        FormKit.clear(saleField, saleError);
    }
}
