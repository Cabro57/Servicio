package tr.cabro.servicio.application.panels.setting;

import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.dictionary.ExchangeRate;
import tr.cabro.servicio.service.ExchangeRateManager;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Döviz kurları: her para birimi bir kart (büyük kur, kaynak ve ne kadar eski olduğu; bir günden
 * eski kur uyarı renginde), altında dövizli fiyatı TL'ye çeviren hızlı hesap. Kurlar TCMB'den
 * çekilir ya da kart üzerindeki "Elle gir" ile yazılır.
 */
public class SettingsExchangeRatePanel extends JPanel implements SettingsModal.HeaderActions {

    private final ExchangeRateManager exchangeRateManager;
    private final JPanel cards = new JPanel(new MigLayout("insets 0, fillx, gap 16", "[grow, fill, sg c][grow, fill, sg c]", "[]"));
    private final List<ExchangeRate> rates = new ArrayList<>();
    private JButton refreshButton;

    private CurrencyField convertAmount;
    private JComboBox<ExchangeRate> convertCurrency;
    private JLabel convertResult;

    public SettingsExchangeRatePanel() {
        this.exchangeRateManager = ServiceManager.getExchangeRateManager();
        initComponent();
        refreshList();
    }

    private void initComponent() {
        setLayout(new MigLayout("insets 4 24 20 24, fillx, wrap 1", "[grow, fill]", "[]12[]20[]"));
        setOpaque(false);

        refreshButton = SettingsKit.headerButton("TCMB'den güncelle", "icons/refresh-cw.svg");
        refreshButton.addActionListener(e -> onRefreshFromTcmb());

        add(SettingsKit.note("Dövizli alış/satış fiyatları bu kurlarla TL'ye çevrilir. Kur her gün değişir; eski kurla "
                + "satış yapmamak için güncel tutun."), "wmin 0");
        cards.setOpaque(false);
        add(cards);
        add(buildConverter());
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(refreshButton);
    }

    private JComponent buildConverter() {
        JPanel card = new JPanel(new MigLayout("insets 14 16 14 16, fillx, gap 10", "[][160!][120!][grow]", "[]10[center]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JLabel title = new JLabel("Hızlı çevir");
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
        card.add(title, "span, wrap");

        convertAmount = new CurrencyField();
        convertAmount.setAvailableCurrencies(List.of("TRY"));
        convertAmount.setValue(new BigDecimal("100"));
        convertAmount.addPropertyChangeListener("value", e -> updateConversion());
        convertCurrency = new JComboBox<>();
        convertCurrency.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        convertCurrency.addActionListener(e -> updateConversion());
        convertResult = new JLabel("—");
        convertResult.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");

        card.add(SettingsKit.label("Tutar"));
        card.add(convertAmount, "growx");
        card.add(convertCurrency, "growx");
        card.add(convertResult, "gapleft 12, wmin 0");
        return card;
    }

    private void refreshList() {
        exchangeRateManager.getAll().thenAccept(list -> SwingUtilities.invokeLater(() -> applyRates(list)))
                .exceptionally(ex -> ErrorHandler.handle(this, "Döviz kurları yüklenemedi", ex));
    }

    private void applyRates(List<ExchangeRate> list) {
        rates.clear();
        rates.addAll(list);
        cards.removeAll();
        for (ExchangeRate r : list) cards.add(rateCard(r));
        cards.revalidate();
        cards.repaint();

        Object selected = convertCurrency.getSelectedItem();
        convertCurrency.removeAllItems();
        for (ExchangeRate r : list) convertCurrency.addItem(r);
        if (selected instanceof ExchangeRate) {
            for (ExchangeRate r : list) if (r.getCurrencyCode().equals(((ExchangeRate) selected).getCurrencyCode())) convertCurrency.setSelectedItem(r);
        }
        updateConversion();
    }

    private JComponent rateCard(ExchangeRate r) {
        JPanel card = new JPanel(new MigLayout("insets 14 16 14 16, fillx, gap 10 2", "[][grow][]", "[][]8[]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        JLabel code = new JLabel(r.getCurrencyCode());
        code.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        JLabel name = new JLabel(r.getCurrencyName() != null ? r.getCurrencyName() : "");
        name.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        JButton edit = new JButton("Elle gir", new Ikon("icons/pencil.svg", 14, "Label.foreground"));
        edit.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 3,8,3,8; iconTextGap: 4");
        edit.addActionListener(e -> onEdit(r));

        boolean hasRate = r.getRate() != null && r.getRate().signum() > 0;
        JLabel value = new JLabel(hasRate ? rateText(r.getRate()) + " ₺" : "Kur girilmemiş");
        value.putClientProperty(FlatClientProperties.STYLE, hasRate ? "font: bold +8" : "font: bold +2; foreground: $Servicio.warningColor");

        JLabel age = new JLabel(ageText(r));
        boolean stale = r.getUpdatedAt() == null || Duration.between(r.getUpdatedAt(), LocalDateTime.now()).toHours() >= 24;
        age.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: "
                + (stale ? "$Servicio.warningColor" : "$Label.disabledForeground"));

        card.add(code, "split 2");
        card.add(name, "gapleft 8");
        card.add(edit, "skip 1, wrap");
        card.add(value, "span 3, wrap");
        card.add(age, "span 3");
        return card;
    }

    private static String rateText(BigDecimal rate) {
        return rate.setScale(4, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** "TCMB · 13.08.2026 21:36 · 42 gün önce" */
    private static String ageText(ExchangeRate r) {
        if (r.getUpdatedAt() == null) return "Hiç güncellenmedi";
        String source = "Manuel".equalsIgnoreCase(r.getSource()) || "MANUAL".equalsIgnoreCase(r.getSource()) ? "Elle girildi" : (r.getSource() != null ? r.getSource() : "Kaynak yok");
        Duration d = Duration.between(r.getUpdatedAt(), LocalDateTime.now());
        String ago = d.toMinutes() < 60 ? "az önce" : d.toHours() < 24 ? d.toHours() + " saat önce" : d.toDays() + " gün önce";
        return source + "  ·  " + r.getUpdatedAt().format(DateFormats.dateTime()) + "  ·  " + ago;
    }

    private void updateConversion() {
        if (convertResult == null) return;
        ExchangeRate r = (ExchangeRate) convertCurrency.getSelectedItem();
        Object v = convertAmount.getValue();
        BigDecimal amount = v instanceof BigDecimal ? (BigDecimal) v : v instanceof Number ? new BigDecimal(v.toString()) : BigDecimal.ZERO;
        if (r == null || r.getRate() == null || r.getRate().signum() <= 0) {
            convertResult.setText("—");
            return;
        }
        convertResult.setText("= " + Format.formatPrice(amount.multiply(r.getRate()).setScale(2, RoundingMode.HALF_UP)));
    }

    private void onRefreshFromTcmb() {
        refreshButton.setEnabled(false);
        exchangeRateManager.refreshFromTcmb().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            refreshButton.setEnabled(true);
            Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.exchangeRate.refreshed"));
            applyRates(list);
            SettingsKit.saved(this);
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> refreshButton.setEnabled(true));
            return ErrorHandler.handle(this, "TCMB'den kur güncellenemedi", ex);
        });
    }

    /** Elle kur girişi: tek alan, virgül ya da nokta kabul edilir; hata alanın altında. */
    private void onEdit(ExchangeRate rate) {
        String code = rate.getCurrencyCode();
        SettingsDialog d = new SettingsDialog("1 " + code + " kaç TL?", 380);
        d.lead("Elle girilen kur, TCMB'den yeniden güncellenene kadar dövizli parça fiyatlarında kullanılır.");
        JTextField field = new JTextField(rate.getRate() != null && rate.getRate().signum() > 0 ? rate.getRate().toPlainString() : "");
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, new JLabel("TL  "));
        JLabel error = FormKit.errorLabel();
        d.body().add(FormKit.cell("Kur", field, error), "span 2");
        d.primary("Kaydet", false, () -> {
            BigDecimal value;
            try {
                value = new BigDecimal(field.getText().trim().replace(',', '.'));
            } catch (NumberFormatException ex) {
                FormKit.fail(field, error, "Sayı girin, örn. 34,25");
                return;
            }
            if (value.signum() <= 0) {
                FormKit.fail(field, error, "Kur sıfırdan büyük olmalı.");
                return;
            }
            d.busy();
            exchangeRateManager.setManualRate(code, value).thenAccept(v -> SwingUtilities.invokeLater(() -> {
                d.close();
                SettingsKit.saved(this);
                refreshList();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(d::idle);
                return ErrorHandler.handle(this, "Kur güncellenemedi", ex);
            });
        });
        d.show(this, field);
    }
}
