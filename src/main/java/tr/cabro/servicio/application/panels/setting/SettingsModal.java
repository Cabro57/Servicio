package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Ayarlar ekranı: solda gruplu gezinme ve ayar arama, sağda seçilen sayfa.
 * <p>
 * Modal olarak açılır (bkz. {@code FormManager.showSettings}); üzerinde çalışılan form kapanmaz.
 * Sayfa başlığı, açıklaması, sayfaya özel eylem düğmeleri ve "Kaydedildi" göstergesi bu kabukta
 * çizilir; sayfalar yalnızca bölümlerini kurar (bkz. {@link SettingsKit}).
 * <p>
 * Ayarların çoğu anında kaydedilir; sayfalar kaydettikten sonra {@link SettingsKit#saved} çağırır.
 * Sayfalar ilk seçildiklerinde oluşturulur: sözlük sayfaları veritabanından okuduğu için hepsini
 * birden kurmak modalın açılışını gereksiz yavaşlatırdı.
 */
public class SettingsModal extends JPanel {

    public static final String MODAL_ID = "app-settings";

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    /** Oturum boyunca son açılan sayfa; modal yeniden açılınca kalınan yerden devam edilir. */
    private static String lastPageId;

    /** Sayfanın başlığında gösterilecek eylemler (Ekle, Şimdi yedekle…). */
    interface HeaderActions {
        List<JComponent> headerActions();
    }

    private record Page(String group, String title, String description, String icon,
                        String keywords, Supplier<JComponent> factory) {
        String id() {
            return group + "/" + title;
        }
    }

    private final List<Page> pages = new ArrayList<>();
    private final Map<Page, JToggleButton> navButtons = new HashMap<>();
    private final Map<String, JLabel> groupLabels = new HashMap<>();
    private final Map<String, JComponent> createdPages = new HashMap<>();
    private final ButtonGroup navGroup = new ButtonGroup();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    private JTextField searchField;
    private JLabel noResultLabel;
    private JLabel titleLabel;
    private JLabel descriptionLabel;
    private JPanel actionsPanel;
    private JLabel savedLabel;
    private Timer savedTimer;

    /** Belirli bir sayfada ("Grup/Başlık") açılır; ör. alt çubuktan Ayarlar &gt; Güncelleme. */
    public SettingsModal(String pageId) {
        this();
        pages.stream().filter(p -> p.id().equals(pageId)).findFirst().ifPresent(this::selectPage);
    }

    public SettingsModal() {
        buildPages();
        initComponent();
        selectPage(pages.stream().filter(p -> p.id().equals(lastPageId)).findFirst().orElse(pages.get(0)));
    }

    private void buildPages() {
        page("Uygulama", "Genel", "Dil, bölge, barkod ve menü tercihleri.", "settings.svg",
                "dil bölge tarih sayı biçim para birimi barkod önek menü parçalar ürünler çıkış onay kapat",
                SettingsMainPanel::new);
        page("Uygulama", "Görünüm", "Tema, vurgu rengi ve yazı boyutu. Değişiklikler hemen uygulanır.", "palette.svg",
                "tema koyu açık oled siyah renk vurgu yazı boyutu font büyük küçük",
                AppearancePanel::new);

        page("Uygulama", "Ses", "Bildirim seslerini aç/kapat, seviyesini ayarla ve dinle.", "volume-2.svg",
                "ses bildirim sessiz zil hoparlör seviye volume tahsilat uyarı",
                SettingsSoundPanel::new);
        page("Uygulama", "Klavye kısayolları", "Tüm kısayolların listesi. Salt bilgi; kısayollar değiştirilemez.", "keyboard.svg",
                "kısayol klavye tuş alt ctrl f1 f2 pos kısa yol",
                SettingsShortcutsPanel::new);

        page("İşletme", "İşletme Bilgileri", "Belgelerin antedinde ve fişlerde görünen işletme bilgileri.", "store.svg",
                "ad telefon adres logo antet firma şirket işletme",
                SettingsBusinessPanel::new);
        page("İşletme", "Belge Metinleri", "Formlarda ve fişlerde basılan koşul, beyan ve garanti metinleri.", "file-text.svg",
                "garanti koşul beyan metin form fiş teslim kabul",
                SettingsDocumentTextsPanel::new);
        page("İşletme", "Yazdırma", "Fiş yazıcısının kağıt genişliği ve hizalama denemesi.", "printer.svg",
                "yazıcı fiş kağıt genişlik 80 58 mm termal deneme",
                SettingsPrintingPanel::new);
        page("İşletme", "WhatsApp Şablonları", "Müşteriye tek tıkla gönderilen hazır mesajlar.", "message-circle.svg",
                "mesaj whatsapp şablon bildirim",
                SettingsTemplatesPanel::new);

        page("Sözlükler", "Cihazlar", "Arıza kaydında seçilen cihaz türleri ve her türün markaları.", "tablet-smartphone.svg",
                "cihaz tür marka telefon tablet",
                SettingsDevicePanel::new);
        page("Sözlükler", "Tamirler", "Cihaz türüne göre hazır işçilik kalemleri ve fiyatları.", "wrench.svg",
                "tamir işçilik fiyat onarım",
                SettingsRepairPanel::new);
        page("Sözlükler", "Parça Kategorileri", "Stok ve parça ekranlarında seçilen kategoriler.", "tag.svg",
                "kategori parça stok",
                SettingsPartCategoryPanel::new);
        page("Sözlükler", "Döviz Kurları", "Dövizli parça fiyatlarını TL'ye çevirirken kullanılan kurlar.", "banknote.svg",
                "kur döviz dolar euro sterlin tcmb",
                SettingsExchangeRatePanel::new);

        page("Sistem", "Güvenlik", "Ekran kilidi ve cihaz erişim bilgilerinin saklanma süresi.", "shield-user.svg",
                "kilit ekran şifre pin desen erişim güvenlik",
                SettingsSecurityPanel::new);
        page("Sistem", "Yedekleme", "Veritabanı yedekleri, otomatik yedekleme ve geri yükleme.", "database-backup.svg",
                "yedek yedekleme geri yükle klasör veritabanı",
                SettingsDatabasePanel::new);
        page("Sistem", "Güncelleme", "Yüklü sürüm, yeni sürüm denetimi ve otomatik denetim.", "circle-arrow-up.svg",
                "güncelleme sürüm versiyon yeni indir denetle kontrol otomatik atla yama",
                SettingsUpdatePanel::new);
    }

    private void page(String group, String title, String description, String icon, String keywords,
                      Supplier<JComponent> factory) {
        pages.add(new Page(group, title, description, icon, keywords, factory));
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 0, gap 0", "[236!][pref!][grow, fill]", "[grow, fill]"));

        add(createNav(), "grow");
        add(new JSeparator(SwingConstants.VERTICAL), "growy");
        add(createContent(), "grow");

        // Ctrl+F: modalın herhangi bir yerinden aramaya dön.
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "focusSearch");
        getActionMap().put("focusSearch", action(e -> {
            searchField.requestFocusInWindow();
            searchField.selectAll();
        }));
    }

    /**
     * Modal, küçük ekranda (1366×768) pencereden taşmasın; geniş ekranda da gereksiz büyümesin.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension size = new Dimension(UIScale.scale(1020), UIScale.scale(640));
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            size.width = Math.min(size.width, window.getWidth() - UIScale.scale(120));
            size.height = Math.min(size.height, window.getHeight() - UIScale.scale(150));
        }
        return size;
    }

    // ------------------------------------------------------------------ gezinme

    private JComponent createNav() {
        JPanel nav = new JPanel(new MigLayout("wrap, insets 14 10 14 10, fillx, gap 0, hidemode 3", "[grow, fill]"));
        nav.putClientProperty(FlatClientProperties.STYLE_CLASS, "settingsNav");

        searchField = new JTextField();
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ayar ara");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                new Ikon("icons/search.svg", 14, "Label.disabledForeground"));
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        searchField.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,8,4,8");
        searchField.setToolTipText("Ayar adı ya da içeriği: \"barkod\", \"logo\", \"kilit\"… (Ctrl+F)");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterNav(); }
            @Override public void removeUpdate(DocumentEvent e) { filterNav(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        bind(searchField, KeyEvent.VK_ENTER, "openFirst", () -> firstVisible().ifPresent(p -> {
            selectPage(p);
            navButtons.get(p).requestFocusInWindow();
        }));
        bind(searchField, KeyEvent.VK_DOWN, "focusFirst", () ->
                firstVisible().ifPresent(p -> navButtons.get(p).requestFocusInWindow()));
        // Esc: arama doluysa önce temizlensin, modal kapanmasın.
        searchField.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSearch");
        searchField.getActionMap().put("clearSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.setText("");
            }

            @Override
            public boolean accept(Object sender) {
                return !searchField.getText().isEmpty();
            }
        });
        nav.add(searchField, "gapbottom 6");

        String group = null;
        for (Page page : pages) {
            if (!page.group().equals(group)) {
                group = page.group();
                JLabel caption = new JLabel(group);
                caption.putClientProperty(FlatClientProperties.STYLE,
                        "font: bold -1; foreground: $Label.disabledForeground");
                groupLabels.put(group, caption);
                nav.add(caption, "gaptop 12, gapleft 10, gapbottom 4");
            }
            JToggleButton button = navButton(page);
            navButtons.put(page, button);
            navGroup.add(button);
            nav.add(button, "gapbottom 1");
        }

        noResultLabel = new JLabel("Eşleşen ayar yok");
        noResultLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        noResultLabel.setVisible(false);
        nav.add(noResultLabel, "gaptop 12, gapleft 10");

        // Sarmalayıcı yüksekliği doldurur: gezinme tonu listenin altında da devam etsin.
        JScrollPane scroll = new JScrollPane(new ScrollablePage(nav));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "width: 5");
        return scroll;
    }

    private JToggleButton navButton(Page page) {
        JToggleButton b = new JToggleButton(page.title(), new Ikon("icons/" + page.icon(), 16, "Label.disabledForeground"));
        b.setSelectedIcon(new Ikon("icons/" + page.icon(), 16, "Component.accentColor"));
        b.setHorizontalAlignment(SwingConstants.LEADING);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; margin: 6,10,6,10; iconTextGap: 10; "
                        + "toolbar.selectedBackground: fade($Component.accentColor, 15%); "
                        + "toolbar.hoverBackground: fade($Label.foreground, 6%)");
        b.addActionListener(e -> selectPage(page));
        bind(b, KeyEvent.VK_DOWN, "next", () -> moveSelection(page, 1));
        bind(b, KeyEvent.VK_UP, "previous", () -> moveSelection(page, -1));
        return b;
    }

    /** Yukarı/aşağı ok: görünür sayfalar arasında gez; ilk sayfadan yukarı çıkınca aramaya dön. */
    private void moveSelection(Page from, int step) {
        List<Page> visible = pages.stream().filter(p -> navButtons.get(p).isVisible()).toList();
        int index = visible.indexOf(from) + step;
        if (index < 0) {
            searchField.requestFocusInWindow();
            return;
        }
        if (index >= visible.size()) return;
        Page target = visible.get(index);
        selectPage(target);
        navButtons.get(target).requestFocusInWindow();
    }

    private void filterNav() {
        String query = normalize(searchField.getText().trim());
        Map<String, Boolean> groupHasMatch = new HashMap<>();
        boolean any = false;
        for (Page page : pages) {
            boolean match = query.isEmpty()
                    || normalize(page.title() + " " + page.group() + " " + page.keywords()).contains(query);
            navButtons.get(page).setVisible(match);
            if (match) {
                groupHasMatch.put(page.group(), true);
                any = true;
            }
        }
        groupLabels.forEach((group, label) -> label.setVisible(groupHasMatch.containsKey(group)));
        noResultLabel.setVisible(!any);
        noResultLabel.getParent().revalidate();
        noResultLabel.getParent().repaint();
    }

    private java.util.Optional<Page> firstVisible() {
        return pages.stream().filter(p -> navButtons.get(p).isVisible()).findFirst();
    }

    /** Türkçe harfleri sadeleştirir: "yazdirma" da "Yazdırma"yı bulsun. */
    private static String normalize(String text) {
        return text.toLowerCase(TR)
                .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
    }

    // ------------------------------------------------------------------ içerik

    private JComponent createContent() {
        JPanel content = new JPanel(new MigLayout("fill, insets 0, gap 0", "[grow, fill]", "[][grow, fill]"));

        JPanel header = new JPanel(new MigLayout("fillx, insets 18 24 8 24, gap 0, hidemode 3",
                "[grow, fill][][]", "[]2[]"));
        header.setOpaque(false);
        titleLabel = new JLabel();
        titleLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +4");
        descriptionLabel = new JLabel();
        descriptionLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        savedLabel = new JLabel("Kaydedildi", new Ikon("icons/circle-check.svg", 14, "Servicio.successColor"), SwingConstants.LEADING);
        savedLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1; iconTextGap: 5");
        savedLabel.setVisible(false);
        savedTimer = new Timer(1800, e -> savedLabel.setVisible(false));
        savedTimer.setRepeats(false);

        actionsPanel = new JPanel(new MigLayout("insets 0, gap 8", "", "[center]"));
        actionsPanel.setOpaque(false);

        header.add(titleLabel, "wmin 0");
        header.add(savedLabel, "spany 2, aligny center, gapright 12");
        header.add(actionsPanel, "spany 2, aligny center, wrap");
        header.add(descriptionLabel, "wmin 0");

        content.add(header, "wrap");
        content.add(contentPanel);
        return content;
    }

    private void selectPage(Page page) {
        lastPageId = page.id();
        navButtons.get(page).setSelected(true);

        JComponent body = createdPages.computeIfAbsent(page.id(), id -> {
            JComponent created = page.factory().get();
            JScrollPane scroll = new JScrollPane(new ScrollablePage(created));
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "width: 5");
            contentPanel.add(scroll, id);
            return created;
        });

        titleLabel.setText(page.title());
        descriptionLabel.setText(page.description());
        actionsPanel.removeAll();
        if (body instanceof HeaderActions withActions) {
            withActions.headerActions().forEach(actionsPanel::add);
        }
        savedTimer.stop();
        savedLabel.setVisible(false);
        actionsPanel.revalidate();
        actionsPanel.repaint();

        cardLayout.show(contentPanel, page.id());
    }

    /** Anında kaydedilen bir değişiklikten sonra başlıkta kısa süre "Kaydedildi" gösterir. */
    void flashSaved() {
        savedLabel.setVisible(true);
        savedTimer.restart();
    }

    // ------------------------------------------------------------------ yardımcılar

    private static void bind(JComponent component, int keyCode, String name, Runnable runnable) {
        component.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        component.getActionMap().put(name, action(e -> runnable.run()));
    }

    private static Action action(java.util.function.Consumer<ActionEvent> body) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.accept(e);
            }
        };
    }

    /**
     * Sayfayı kaydırma alanına yerleştiren sarmalayıcı: yatay kaydırma olmaz; sayfa alandan kısaysa
     * yüksekliği doldurur (liste/tablo sayfaları pencerenin altına kadar uzansın, boşluk kalmasın).
     */
    private static final class ScrollablePage extends JPanel implements Scrollable {
        ScrollablePage(JComponent page) {
            super(new BorderLayout());
            setOpaque(false);
            add(page);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(16, r.height - 32); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
        }
    }
}
