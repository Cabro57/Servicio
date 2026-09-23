package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.AppSettings;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Ayarlar &gt; Uygulama &gt; Genel: dil ve bölge, barkod öneki, menü görünürlüğü, çıkış onayı.
 * <p>
 * Eskiden bunlar üç ayrı, neredeyse boş sayfaydı (Genel, Dil ve Bölge, Ürün Görünürlüğü).
 * Dil/bölge ve çıkış onayı makineye özel olduğu için {@code config.json} içinde; barkod öneki ve
 * menü görünürlüğü işletme ayarı olduğu için veritabanında ({@code app_settings}) tutulur.
 * Hepsi değiştiği anda kaydedilir.
 */
public class SettingsMainPanel extends JPanel {

    private static final Option[] LANGUAGE_OPTIONS = {
            new Option("auto", "Otomatik (işletim sistemi)"),
            new Option("tr", "Türkçe"),
            new Option("en", "English"),
    };

    private static final Option[] FORMAT_REGION_OPTIONS = {
            new Option("auto", "Otomatik (işletim sistemi)"),
            new Option("tr-TR", "Türkiye — 31.12.2026 · 1.234,56"),
            new Option("en-US", "ABD — 12/31/2026 · 1,234.56"),
            new Option("en-GB", "İngiltere — 31/12/2026 · 1,234.56"),
            new Option("de-DE", "Almanya — 31.12.2026 · 1.234,56"),
    };

    private static final Option[] CURRENCY_OPTIONS = {
            new Option("TRY", "Türk lirası (₺)"),
            new Option("USD", "ABD doları ($)"),
            new Option("EUR", "Euro (€)"),
            new Option("GBP", "İngiliz sterlini (£)"),
    };

    private JComboBox<Option> languageCombo;
    private JComboBox<Option> formatRegionCombo;
    private JComboBox<Option> currencyCombo;
    private JTextField barcodePrefix;
    private JCheckBox menuShowParts;
    private JCheckBox menuShowProducts;
    private JCheckBox skipExitConfirmation;

    public SettingsMainPanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());
        loadSettings();
        installListeners();
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        // --- Dil ve bölge ---
        languageCombo = new JComboBox<>(LANGUAGE_OPTIONS);
        formatRegionCombo = new JComboBox<>(FORMAT_REGION_OPTIONS);
        currencyCombo = new JComboBox<>(CURRENCY_OPTIONS);

        JPanel locale = SettingsKit.rows();
        locale.add(SettingsKit.label("Arayüz dili"));
        locale.add(languageCombo, SettingsKit.FIELD);
        locale.add(SettingsKit.label("Tarih ve sayı"));
        locale.add(formatRegionCombo, SettingsKit.FIELD);
        locale.add(SettingsKit.label("Para birimi"));
        locale.add(currencyCombo, SettingsKit.FIELD);
        locale.add(SettingsKit.wrappingNote("Para birimi yalnızca gösterilen sembolü değiştirir; "
                + "tutarlar her zaman TL bazlı hesaplanır."), "skip, wmin 0, wmax 380");
        SettingsKit.section(page, "Dil ve bölge",
                "Diyalogların dili, tarih sırası ve binlik/ondalık ayırıcılar.", locale);

        // --- Barkod ---
        barcodePrefix = new JTextField();
        barcodePrefix.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Önek yok");
        JPanel barcode = SettingsKit.rows();
        barcode.add(SettingsKit.label("Barkod öneki"));
        barcode.add(barcodePrefix, "growx, wmax 160");
        barcode.add(SettingsKit.note("Yeni üretilen barkod numaralarının başına eklenir."), "skip");
        SettingsKit.section(page, "Barkod", "Parça ve ürün etiketleri.", barcode);

        // --- Menü ---
        menuShowParts = new JCheckBox("Parçalar menüde görünsün");
        menuShowProducts = new JCheckBox("Ürünler menüde görünsün");
        JPanel menu = SettingsKit.stack();
        menu.add(SettingsKit.check(menuShowParts, "Servis parçası ekranı. Yalnızca perakende satış yapıyorsanız kapatın."));
        menu.add(SettingsKit.check(menuShowProducts, "Satış ürünü (POS) ekranı. Yalnızca servis veriyorsanız kapatın."), "gaptop 6");
        SettingsKit.section(page, "Menü", "Sol menüde hangi ekranların görüneceği.", menu);

        // --- Çıkış ---
        skipExitConfirmation = new JCheckBox("Kapatırken onay sorma");
        JPanel exit = SettingsKit.stack();
        exit.add(SettingsKit.check(skipExitConfirmation,
                "İşaretliyse uygulama kapatılırken \"emin misiniz?\" sorusu gösterilmez."));
        SettingsKit.section(page, "Çıkış", "Uygulamayı kapatma davranışı.", exit);

        return page;
    }

    private void loadSettings() {
        var ui = AppSettings.get().getUi();
        selectValue(languageCombo, ui.getLanguage());
        selectValue(formatRegionCombo, ui.getFormatRegion());
        selectValue(currencyCombo, ui.getCurrencyCode());
        barcodePrefix.setText(ServiceManager.getAppSettingService().getBarcodePrefix());
        menuShowParts.setSelected(ServiceManager.getAppSettingService().isMenuShowParts());
        menuShowProducts.setSelected(ServiceManager.getAppSettingService().isMenuShowProducts());
        skipExitConfirmation.setSelected(ui.isSkipExitConfirmation());
    }

    private void installListeners() {
        languageCombo.addActionListener(e -> {
            AppLocale.applyUiLanguage(selected(languageCombo).value);
            SettingsKit.saved(this);
        });
        formatRegionCombo.addActionListener(e -> {
            AppLocale.applyFormatRegion(selected(formatRegionCombo).value);
            SettingsKit.saved(this);
        });
        currencyCombo.addActionListener(e -> {
            AppLocale.applyCurrencyCode(selected(currencyCombo).value);
            SettingsKit.saved(this);
        });

        // Metin alanı odaktan çıkınca ya da Enter'a basılınca kaydedilir.
        barcodePrefix.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                saveBarcodePrefix();
            }
        });
        barcodePrefix.addActionListener(e -> saveBarcodePrefix());

        menuShowParts.addActionListener(e -> {
            ServiceManager.getAppSettingService().setMenuShowParts(menuShowParts.isSelected());
            MyDrawerBuilder.getInstance().rebuildMenu();
            SettingsKit.saved(this);
        });
        menuShowProducts.addActionListener(e -> {
            ServiceManager.getAppSettingService().setMenuShowProducts(menuShowProducts.isSelected());
            MyDrawerBuilder.getInstance().rebuildMenu();
            SettingsKit.saved(this);
        });
        skipExitConfirmation.addActionListener(e -> {
            AppSettings.get().getUi().setSkipExitConfirmation(skipExitConfirmation.isSelected());
            AppSettings.save();
            SettingsKit.saved(this);
        });
    }

    private void saveBarcodePrefix() {
        String value = barcodePrefix.getText().trim();
        if (value.equals(ServiceManager.getAppSettingService().getBarcodePrefix())) return;
        ServiceManager.getAppSettingService().setBarcodePrefix(value);
        SettingsKit.saved(this);
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

    private record Option(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
