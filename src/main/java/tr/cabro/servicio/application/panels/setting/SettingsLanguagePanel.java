package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.settings.AppSettings;

import javax.swing.*;

/**
 * Ayarlar &gt; Uygulama &gt; Dil ve Bölge.
 * <p>
 * Windows'un Bölge ayarlarındaki gibi üç bağımsız tercih sunar: arayüz dili,
 * tarih/sayı biçimi bölgesi, ve gösterim para birimi sembolü. Üçü de makineye
 * özel olduğu için {@code config.json} içinde tutulur (bkz. {@link AppLocale}).
 * Seçim değişince anında uygulanır, uygulamanın yeniden başlatılması gerekmez.
 */
public class SettingsLanguagePanel extends JPanel {

    private static final Option[] LANGUAGE_OPTIONS = {
            new Option("auto", "Otomatik (İşletim Sistemi)"),
            new Option("tr", "Türkçe"),
            new Option("en", "English"),
    };

    private static final Option[] FORMAT_REGION_OPTIONS = {
            new Option("auto", "Otomatik (İşletim Sistemi)"),
            new Option("tr-TR", "Türkiye (dd.mm.yyyy, 1.234,56)"),
            new Option("en-US", "Amerika Birleşik Devletleri (mm/dd/yyyy, 1,234.56)"),
            new Option("en-GB", "İngiltere (dd/mm/yyyy, 1,234.56)"),
            new Option("de-DE", "Almanya (dd.mm.yyyy, 1.234,56)"),
    };

    private static final Option[] CURRENCY_OPTIONS = {
            new Option("TRY", "Türk Lirası (₺)"),
            new Option("USD", "Amerikan Doları ($)"),
            new Option("EUR", "Euro (€)"),
            new Option("GBP", "İngiliz Sterlini (£)"),
    };

    private JComboBox<Option> languageCombo;
    private JComboBox<Option> formatRegionCombo;
    private JComboBox<Option> currencyCombo;

    public SettingsLanguagePanel() {
        init();
    }

    private void init() {
        initComponent();
        loadSettings();

        languageCombo.addActionListener(e ->
                AppLocale.applyUiLanguage(selected(languageCombo).value));
        formatRegionCombo.addActionListener(e ->
                AppLocale.applyFormatRegion(selected(formatRegionCombo).value));
        currencyCombo.addActionListener(e ->
                AppLocale.applyCurrencyCode(selected(currencyCombo).value));
    }

    private void loadSettings() {
        var ui = AppSettings.get().getUi();
        selectValue(languageCombo, ui.getLanguage());
        selectValue(formatRegionCombo, ui.getFormatRegion());
        selectValue(currencyCombo, ui.getCurrencyCode());
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 10, gapy 15", "[grow]", "[][][grow]"));

        JPanel panel = new JPanel(new MigLayout("fill, insets 10", "[][grow]", "[]10[]10[]5[]"));
        panel.setBorder(BorderFactory.createTitledBorder("Dil ve Bölge"));

        panel.add(new JLabel("Dil:"));
        languageCombo = new JComboBox<>(LANGUAGE_OPTIONS);
        languageCombo.setToolTipText("Onay/hata diyalogları ve bildirimlerin dilini belirler.");
        panel.add(languageCombo, "growx, wrap");

        panel.add(new JLabel("Tarih ve Sayı Biçimi:"));
        formatRegionCombo = new JComboBox<>(FORMAT_REGION_OPTIONS);
        formatRegionCombo.setToolTipText("Tarih sırası ve binlik/ondalık ayırıcıları belirler; arayüz dilinden bağımsızdır.");
        panel.add(formatRegionCombo, "growx, wrap");

        panel.add(new JLabel("Para Birimi:"));
        currencyCombo = new JComboBox<>(CURRENCY_OPTIONS);
        currencyCombo.setToolTipText("Sadece görüntüleme sembolünü değiştirir, TL bazlı hesaplamayı etkilemez.");
        panel.add(currencyCombo, "growx, wrap");

        JLabel hint = new JLabel("Not: Para birimi ayarı yalnızca gösterim sembolünü değiştirir; tutarlar her zaman TL bazlı hesaplanmaya devam eder.");
        hint.putClientProperty("FlatLaf.styleClass", "small");
        panel.add(hint, "span 2, wrap");

        add(panel, "growx, wrap");
        add(new JLabel(), "pushy, growy, wrap");
    }

    private static Option selected(JComboBox<Option> combo) {
        return (Option) combo.getSelectedItem();
    }

    private static void selectValue(JComboBox<Option> combo, String value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).value.equals(value)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedIndex(0);
    }

    private static final class Option {
        final String value;
        final String label;

        Option(String value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
