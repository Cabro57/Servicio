package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tutar giriş alanı. Sağdaki para birimi ikonu aslında bir düğmedir; normalde
 * düz bir sembol gibi durur (border/arkaplan yok), sadece üzerine gelince
 * belli olur (FlatLaf "borderless" buton stili). Tıklanınca alanın altında,
 * alan genişliğinde bir popup açılır ve TRY/USD/EUR arasında seçim yapılır.
 * Girilen tutar her zaman seçili para biriminin ham tutarıdır — TL karşılığı
 * bu alanın dışında (bkz. PartEditPanel) kur ile hesaplanır.
 */
public class CurrencyField extends JFormattedTextField {

    private record CurrencyInfo(String code, String name, String iconPath) {}

    private static final Map<String, CurrencyInfo> SUPPORTED = new LinkedHashMap<>();
    static {
        SUPPORTED.put("TRY", new CurrencyInfo("TRY", "Türk Lirası", "icons/turkish-lira.svg"));
        SUPPORTED.put("USD", new CurrencyInfo("USD", "Amerikan Doları", "icons/dollar-sign.svg"));
        SUPPORTED.put("EUR", new CurrencyInfo("EUR", "Euro", "icons/euro.svg"));
    }

    private final JButton currencyButton;
    private String selectedCurrency = "TRY";
    private List<String> availableCurrencies = List.of("TRY", "USD", "EUR");
    private Runnable currencyChangeListener;

    public CurrencyField() {
        super(createFormatter());
        this.setValue(0.0);

        currencyButton = new JButton();
        currencyButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        currencyButton.putClientProperty(FlatClientProperties.STYLE, "focusWidth:0; innerFocusWidth:0; margin:2,4,2,4; arc:6;");
        currencyButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        currencyButton.setFocusable(false);
        currencyButton.addActionListener(e -> showCurrencyPopup());
        updateButtonIcon();

        putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, currencyButton);

        // Focus geldiğinde tüm yazıyı seç
        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(CurrencyField.this::selectAll);
            }
        });
    }

    private static NumberFormatter createFormatter() {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        NumberFormatter formatter = new NumberFormatter(decimalFormat);
        formatter.setValueClass(Double.class);
        formatter.setAllowsInvalid(false);
        formatter.setMinimum(0.0);  // Negatif para engelle
        return formatter;
    }

    public double getDoubleValue() {
        Object val = this.getValue();
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return 0.0;
    }

    public String getSelectedCurrency() {
        return selectedCurrency;
    }

    public void setSelectedCurrency(String code) {
        this.selectedCurrency = (code != null && SUPPORTED.containsKey(code)) ? code : "TRY";
        updateButtonIcon();
    }

    /** Popup'ta gösterilecek para birimi kodları; varsayılan TRY/USD/EUR. */
    public void setAvailableCurrencies(List<String> codes) {
        this.availableCurrencies = (codes == null || codes.isEmpty()) ? List.of("TRY") : codes;
    }

    /** Kullanıcı popup'tan farklı bir para birimi seçtiğinde tetiklenir. */
    public void addCurrencyChangeListener(Runnable listener) {
        this.currencyChangeListener = listener;
    }

    private void updateButtonIcon() {
        CurrencyInfo info = SUPPORTED.getOrDefault(selectedCurrency, SUPPORTED.get("TRY"));
        int size = getFont() != null ? getFont().getSize() : 13;
        currencyButton.setIcon(new Ikon(info.iconPath(), size));
        currencyButton.setToolTipText(info.name() + " (" + info.code() + ") — para birimini değiştirmek için tıklayın");
    }

    private void showCurrencyPopup() {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BoxLayout(popup, BoxLayout.Y_AXIS));

        for (String code : availableCurrencies) {
            CurrencyInfo info = SUPPORTED.get(code);
            if (info == null) continue;

            JMenuItem item = new JMenuItem(info.code() + " — " + info.name(), new Ikon(info.iconPath(), 1f));
            item.setHorizontalTextPosition(SwingConstants.RIGHT);
            item.addActionListener(e -> {
                setSelectedCurrency(info.code());
                if (currencyChangeListener != null) currencyChangeListener.run();
            });
            popup.add(item);
        }

        int width = Math.max(getWidth(), 170);
        popup.setPreferredSize(new Dimension(width, popup.getPreferredSize().height));
        popup.show(this, 0, getHeight());
    }
}
