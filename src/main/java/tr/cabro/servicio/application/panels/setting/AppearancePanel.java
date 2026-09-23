package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.ColorFunctions;
import com.formdev.flatlaf.util.LoggingFacade;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.SegmentedButtons;
import tr.cabro.servicio.application.component.WrapLayout;
import tr.cabro.servicio.application.themes.FlatOledDarkLaf;
import tr.cabro.servicio.application.themes.LafService;
import tr.cabro.servicio.application.themes.PanelThemes;
import tr.cabro.servicio.settings.AppSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ayarlar &gt; Uygulama &gt; Görünüm: tema, vurgu rengi, yazı boyutu.
 * <p>
 * Öne çıkan dört tema, o temanın gerçek renkleriyle çizilmiş küçük bir pencere önizlemesiyle
 * seçilir; paketteki diğer IntelliJ temaları "Tüm temalar" altında durur. Her değişiklik anında
 * uygulanır ve {@code config.json}'a yazılır (bkz. {@link LafService}).
 */
public class AppearancePanel extends JPanel {

    /** Öne çıkan temalar: sınıf adı → kart etiketi. */
    private static final Map<String, String> FEATURED = new LinkedHashMap<>();

    static {
        FEATURED.put(FlatLightLaf.class.getName(), "Açık");
        FEATURED.put(FlatIntelliJLaf.class.getName(), "Açık klasik");
        FEATURED.put(FlatDarkLaf.class.getName(), "Koyu");
        FEATURED.put(FlatOledDarkLaf.class.getName(), "OLED siyah");
    }

    /**
     * Vurgu rengi seçenekleri (tema dosyalarındaki {@code Demo.accent.*} anahtarları).
     * Kırmızı bilerek yok: uygulamada kırmızı borç/iade demek, birincil düğmeleri kırmızıya
     * boyamak bu anlamı bozar.
     */
    private static final String[][] ACCENTS = {
            {"Demo.accent.default", "Tema varsayılanı"},
            {"Demo.accent.blue", "Mavi"},
            {"Demo.accent.purple", "Mor"},
            {"Demo.accent.green", "Yeşil"},
            {"Demo.accent.orange", "Turuncu"},
    };

    private final List<ThemeCard> themeCards = new ArrayList<>();
    private final List<AccentSwatch> swatches = new ArrayList<>();
    private final ButtonGroup themeGroup = new ButtonGroup();
    private final ButtonGroup accentGroup = new ButtonGroup();
    private JTextArea accentNote;
    private JPanel allThemesPanel;
    private JButton allThemesToggle;
    private PanelThemes panelThemes;
    private JLabel activeThemeNote;

    /** Tema başka bir yoldan (tüm temalar listesi, vurgu değişimi) değişince seçimleri tazeler. */
    private final PropertyChangeListener lafListener = e -> {
        if ("lookAndFeel".equals(e.getPropertyName())) SwingUtilities.invokeLater(this::refreshState);
    };

    public AppearancePanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());
        refreshState();
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        // --- Tema ---
        JPanel themes = SettingsKit.stack();
        JPanel cards = WrapLayout.panel(10, 10);
        FEATURED.forEach((className, label) -> {
            ThemeCard card = new ThemeCard(className, label);
            themeGroup.add(card);
            themeCards.add(card);
            cards.add(card);
        });
        themes.add(cards, "wmin 0");

        activeThemeNote = SettingsKit.note("");
        themes.add(activeThemeNote);

        allThemesToggle = new JButton();
        allThemesToggle.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        allThemesToggle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.accentColor; margin: 2,6,2,6");
        allThemesToggle.addActionListener(e -> setAllThemesVisible(!allThemesPanel.isVisible()));
        themes.add(allThemesToggle, "growx 0, gapleft -6");

        allThemesPanel = new JPanel(new MigLayout("wrap, insets 0, fillx, gapy 6", "[grow, fill]"));
        allThemesPanel.setOpaque(false);
        allThemesPanel.setVisible(false);
        themes.add(allThemesPanel, "wmin 0");

        SettingsKit.section(page, "Tema", "Uygulamanın renk düzeni. Karanlık bir tezgâhta koyu tema gözü daha az yorar.", themes);

        // --- Vurgu rengi ---
        JPanel accents = SettingsKit.stack();
        JPanel swatchRow = new JPanel(new MigLayout("insets 0, gap 10", "", "[center]"));
        swatchRow.setOpaque(false);
        for (String[] accent : ACCENTS) {
            AccentSwatch swatch = new AccentSwatch(accent[0], accent[1]);
            accentGroup.add(swatch);
            swatches.add(swatch);
            swatchRow.add(swatch);
        }
        accents.add(swatchRow);
        accentNote = SettingsKit.wrappingNote("");
        accents.add(accentNote, "wmin 0, wmax 420");
        SettingsKit.section(page, "Vurgu rengi", "Birincil düğmeler, seçili öğeler ve bağlantılar bu renkle gösterilir.", accents);

        // --- Yazı boyutu ---
        SegmentedButtons<Integer> fontSize = new SegmentedButtons<Integer>()
                .add(-1, "Küçük", null)
                .add(0, "Normal", null)
                .add(1, "Büyük", null)
                .add(2, "Çok büyük", null);
        fontSize.setSelected(AppSettings.get().getUi().getFontSizeDelta());
        fontSize.setOnChange(delta -> {
            LafService.applyFontSize(delta);
            SettingsKit.saved(this);
        });
        JPanel sizes = SettingsKit.stack();
        sizes.add(fontSize, "growx 0");
        sizes.add(SettingsKit.wrappingNote("Tüm ekranlardaki yazıları büyütür ya da küçültür. 1366×768 ekranlarda "
                + "\"Çok büyük\" tabloları sıkıştırabilir."), "wmin 0, wmax 420");
        SettingsKit.section(page, "Yazı boyutu", "Tezgâhtan uzaktan okunacak ekranlar için büyütün.", sizes);

        return page;
    }

    private void setAllThemesVisible(boolean visible) {
        if (visible && panelThemes == null) {
            // Tema listesi paketteki tüm .theme.json dosyalarını okuyor; ancak açılınca kurulsun.
            panelThemes = new PanelThemes();
            SegmentedButtons<Integer> filter = new SegmentedButtons<Integer>()
                    .add(0, "Tümü", null)
                    .add(1, "Açık", null)
                    .add(2, "Koyu", null);
            filter.setOnChange(panelThemes::updateThemesList);
            allThemesPanel.add(filter, "growx 0");
            allThemesPanel.add(panelThemes, "h 260!, wmin 0");
        }
        allThemesPanel.setVisible(visible);
        updateAllThemesToggle();
        revalidate();
        repaint();
    }

    private void updateAllThemesToggle() {
        allThemesToggle.setText(allThemesPanel.isVisible() ? "Tüm temaları gizle" : "Tüm temalar…");
    }

    /** Seçili kartı, vurgu örneklerini ve açıklamaları aktif temaya göre tazeler. */
    private void refreshState() {
        String active = UIManager.getLookAndFeel().getClass().getName();
        boolean intelliJ = LafService.isIntelliJThemeActive();
        boolean featured = !intelliJ && FEATURED.containsKey(active);

        themeGroup.clearSelection();
        for (ThemeCard card : themeCards) {
            if (featured && card.className.equals(active)) card.setSelected(true);
            card.invalidatePreview();
        }
        if (featured) {
            activeThemeNote.setVisible(false);
        } else {
            activeThemeNote.setText("Şu an kullanılan: " + UIManager.getLookAndFeel().getName());
            activeThemeNote.setVisible(true);
            if (intelliJ && !allThemesPanel.isVisible()) setAllThemesVisible(true);
        }
        if (panelThemes != null) panelThemes.selectCurrentTheme();
        updateAllThemesToggle();

        // Seçilen renk başka bir temanın paletinden kalmış olabilir; eşleşen yoksa hiçbiri seçili görünmez.
        Color current = LafService.accentColor;
        accentGroup.clearSelection();
        for (AccentSwatch swatch : swatches) {
            swatch.setEnabled(!intelliJ);
            boolean selected = swatch.isDefault() ? current == null
                    : current != null && swatch.color() != null && swatch.color().getRGB() == current.getRGB();
            if (selected) swatch.setSelected(true);
        }
        accentNote.setText(intelliJ
                ? "Bu tema vurgu rengini kendisi belirliyor. Değiştirmek için yukarıdaki temalardan birini seçin."
                : "Renk, tema ve yazı boyutu yalnızca bu bilgisayar için kaydedilir.");
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        UIManager.addPropertyChangeListener(lafListener);
    }

    @Override
    public void removeNotify() {
        UIManager.removePropertyChangeListener(lafListener);
        super.removeNotify();
    }

    // ------------------------------------------------------------------ tema kartı

    /** Temanın kendi renkleriyle çizilmiş küçük pencere önizlemesi ve altında adı. */
    private final class ThemeCard extends JToggleButton {
        final String className;

        ThemeCard(String className, String label) {
            super(label);
            this.className = className;
            setIcon(new ThemePreviewIcon(className));
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            putClientProperty(FlatClientProperties.STYLE,
                    "arc: 14; margin: 8,8,6,8; iconTextGap: 6; "
                            + "toolbar.selectedBackground: fade($Component.accentColor, 15%); "
                            + "toolbar.hoverBackground: fade($Label.foreground, 6%)");
            setToolTipText(label + " temaya geç");
            addActionListener(e -> {
                if (LafService.isIntelliJThemeActive() || !className.equals(UIManager.getLookAndFeel().getClass().getName())) {
                    LafService.applyCoreTheme(className);
                    SettingsKit.saved(AppearancePanel.this);
                }
            });
        }

        void invalidatePreview() {
            ((ThemePreviewIcon) getIcon()).colors = null;
        }
    }

    /**
     * Temanın varsayılanlarını (arka plan, kart, yazı, vurgu) okuyup minyatür bir ekran çizer.
     * Renkler tema kurulmadan {@link LookAndFeel#getDefaults()} ile alınır; seçili vurgu rengi
     * de FlatLaf'ın sistem rengi üzerinden önizlemeye yansır.
     */
    private static final class ThemePreviewIcon implements Icon {
        private final String className;
        private Color[] colors;

        ThemePreviewIcon(String className) {
            this.className = className;
        }

        @Override public int getIconWidth() { return UIScale.scale(116); }
        @Override public int getIconHeight() { return UIScale.scale(72); }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color[] p = colors();
            if (p == null) return;
            Color panel = p[0], card = p[1], fg = p[2], muted = p[3], accent = p[4], border = p[5];

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            float w = getIconWidth(), h = getIconHeight(), s = UIScale.scale(1f);

            RoundRectangle2D frame = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 10 * s, 10 * s);
            g2.setColor(panel);
            g2.fill(frame);

            // Sol menü şeridi: bir satırı seçili, diğerleri soluk.
            g2.setColor(card);
            g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, 28 * s, h - 1, 10 * s, 10 * s));
            g2.fill(new Rectangle.Float(14 * s, 0.5f, 14.5f * s, h - 1));
            for (int i = 0; i < 4; i++) {
                g2.setColor(i == 1 ? accent : muted);
                g2.fill(new RoundRectangle2D.Float(7 * s, (12 + i * 10) * s, (i == 1 ? 15 : 13) * s, 3 * s, 3 * s, 3 * s));
            }

            // İçerik kartı: başlık, iki satır metin ve birincil düğme.
            RoundRectangle2D content = new RoundRectangle2D.Float(36 * s, 9 * s, w - 45 * s, h - 18 * s, 7 * s, 7 * s);
            g2.setColor(card);
            g2.fill(content);
            g2.setColor(border);
            g2.draw(content);
            g2.setColor(fg);
            g2.fill(new RoundRectangle2D.Float(43 * s, 16 * s, 34 * s, 4 * s, 4 * s, 4 * s));
            g2.setColor(muted);
            g2.fill(new RoundRectangle2D.Float(43 * s, 26 * s, w - 60 * s, 3 * s, 3 * s, 3 * s));
            g2.fill(new RoundRectangle2D.Float(43 * s, 33 * s, w - 76 * s, 3 * s, 3 * s, 3 * s));
            g2.setColor(accent);
            g2.fill(new RoundRectangle2D.Float(w - 40 * s, h - 22 * s, 24 * s, 7 * s, 7 * s, 7 * s));
            g2.fill(new Ellipse2D.Float(43 * s, h - 22 * s, 7 * s, 7 * s));

            g2.setColor(border);
            g2.draw(frame);
            g2.dispose();
        }

        private Color[] colors() {
            if (colors != null) return colors;
            try {
                LookAndFeel laf = (LookAndFeel) Class.forName(className).getDeclaredConstructor().newInstance();
                UIDefaults d = laf.getDefaults();
                boolean dark = laf instanceof FlatLaf flat && flat.isDark();
                Color panel = d.getColor("Panel.background");
                colors = new Color[]{
                        panel,
                        ColorFunctions.tint(panel, dark ? 0.03f : 0.25f),
                        d.getColor("Label.foreground"),
                        ColorFunctions.fade(d.getColor("Label.disabledForeground"), 0.6f),
                        d.getColor("Component.accentColor"),
                        d.getColor("Component.borderColor"),
                };
                for (Color color : colors) if (color == null) throw new IllegalStateException("eksik renk");
            } catch (Exception | LinkageError e) {
                LoggingFacade.INSTANCE.logSevere("Tema önizlemesi hazırlanamadı: " + className, e);
                colors = null;
            }
            return colors;
        }
    }

    // ------------------------------------------------------------------ vurgu rengi örneği

    /** Aktif temanın vurgu paletinden yuvarlak renk örneği; seçiliyken dışında halka çizilir. */
    private final class AccentSwatch extends JToggleButton {
        private final String key;

        AccentSwatch(String key, String name) {
            this.key = key;
            setToolTipText(name);
            getAccessibleContext().setAccessibleName("Vurgu rengi: " + name);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> {
                LafService.applyAccentColor(isDefault() ? null : color());
                SettingsKit.saved(AppearancePanel.this);
            });
        }

        boolean isDefault() {
            return key.endsWith(".default");
        }

        Color color() {
            return UIManager.getColor(key);
        }

        @Override
        public Dimension getPreferredSize() {
            int size = UIScale.scale(30);
            return new Dimension(size, size);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color color = color();
            if (color == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float size = Math.min(getWidth(), getHeight());
            float ring = UIScale.scale(2f), gap = UIScale.scale(3f);
            float inner = size - 2 * (ring + gap);

            if (!isEnabled()) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            g2.setColor(color);
            g2.fill(new Ellipse2D.Float(ring + gap, ring + gap, inner, inner));

            if (isSelected() || getModel().isRollover() || isFocusOwner()) {
                g2.setColor(isSelected() ? color : UIManager.getColor("Component.borderColor"));
                g2.setStroke(new BasicStroke(ring));
                g2.draw(new Ellipse2D.Float(ring / 2, ring / 2, size - ring, size - ring));
            }
            if (isDefault()) {
                // Varsayılanı ayırt etmek için ortasında küçük bir nokta.
                g2.setColor(UIManager.getColor("Button.default.foreground"));
                float dot = UIScale.scale(6f);
                g2.fill(new Ellipse2D.Float((size - dot) / 2, (size - dot) / 2, dot, dot));
            }
            g2.dispose();
        }
    }
}
