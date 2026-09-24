package tr.cabro.servicio.application.forms.base;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.component.table.ListTabBar;
import tr.cabro.servicio.application.component.table.ListTable;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.component.table.TableStatePanel;
import tr.cabro.servicio.application.component.table.TableStyler;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.database.filter.ColumnFilterValue;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Liste sayfalarının ortak iskeleti (Müşteriler, Servis Kayıtları, Parçalar …).
 * <pre>
 *  Başlık                                     [ikincil işlemler] [Yeni … Alt+X]
 *  43 müşteri · 6 kurumsal · 31 borçlu
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │ [Tümü 43] [Borçlu 31] [Kurumsal 6]                   [ Ara…        ] │
 *  │ tablo — satırın tamamı kaydı açar, işlemler üstüne gelince görünür   │
 *  │ 43 kayıt                                             ‹ 1 2 3 ›       │
 *  └──────────────────────────────────────────────────────────────────────┘
 * </pre>
 * Özet cümlesi dört istatistik kutusunun, sayılı görünüm sekmeleri filtre açılır kutularının yerini
 * alır. Sekmeler başlık filtrelerinin hazır ön ayarlarıdır ({@link #viewFilters(String)}); sayıları
 * alt sınıfın {@link #countMatching(Map)} sorgusuyla, o anki arama da hesaba katılarak doldurulur.
 */
public abstract class AbstractTableForm extends Form {

    protected JTextField searchField;
    protected ListTable table;
    protected JButton btnNew;
    protected ListSummary summary;
    protected ViewTabs views;
    protected JComboBox<Object> filterCombo;
    protected TableRowSorter<? extends TableModel> sorter;

    private static final String CARD_DATA = "data";
    private static final String CARD_STATE = "state";

    private JPanel tableArea;
    private TableStatePanel statePanel;
    private TableHeaderFilterSupport<?> headerFilterSupport;
    private JLabel resultCount;
    private JButton clearLink;
    private ListTabBar toolbar;

    public AbstractTableForm() {
        // init formInit() üzerinden çağrılır.
    }

    @Override
    public void formInit() {
        initComponent();
        setupTable();
        refreshTable();
    }

    @Override
    public void formRefresh() {
        refreshTable();
    }

    @Override
    public void formOpen() {
        refreshTable();
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 14 18 16 18, gap 0", "[grow, fill]", "[pref]12[grow, fill]"));

        // --- 1. Başlık şeridi: başlık + özet cümlesi solda, işlemler sağda ---
        JPanel header = new JPanel(new MigLayout("insets 0, fillx, gap 8 2, hidemode 3", "[grow, fill]", "[][]"));
        header.setOpaque(false);

        SystemForm sysForm = getClass().getAnnotation(SystemForm.class);
        JLabel title = new JLabel(sysForm != null ? sysForm.name() : "Liste");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +5");
        if (sysForm != null) title.setToolTipText(sysForm.description());

        JPanel actions = new JPanel(new MigLayout("insets 0, gap 8", "", "[center]"));
        actions.setOpaque(false);
        searchField = createSearchField();
        actions.add(searchField, "w 220:300:340, sgy row, growy");
        for (JComponent c : createHeaderActions()) actions.add(c, "sgy row, growy");
        btnNew = createPrimaryButton();
        actions.add(btnNew, "sgy row, growy");

        summary = new ListSummary();

        header.add(title);
        header.add(actions, "gapleft push, spany 2, aligny center, wrap");
        header.add(summary, "wmin 0");
        add(header, "wrap");

        // --- 2. Liste kartı: sekme çubuğu (karta bitişik), tablo, alt bilgi ---
        JPanel card = new JPanel(new MigLayout("fill, insets 0 8 8 8, gap 0, hidemode 3",
                "[grow, fill]", "[pref]4[grow, fill]6[pref]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        views = new ViewTabs();
        initViews();
        views.setOnChange(key -> onViewChanged(key));

        // Sekme çubuğu kartın üst kenarına oturur; altındaki tek hairline tabloyu ayırır.
        toolbar = new ListTabBar();
        toolbar.add(views, "wmin 0");

        clearLink = new JButton("Filtreyi temizle", new Ikon("icons/x.svg", 14, "Label.disabledForeground"));
        clearLink.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        clearLink.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 3,8,3,8; iconTextGap: 4; focusWidth: 0");
        clearLink.setToolTipText("Arama, sekme ve sütun filtrelerini sıfırla");
        clearLink.setVisible(false);
        clearLink.addActionListener(e -> clearFilters());
        toolbar.add(clearLink);

        if (hasFilterCombo()) {
            filterCombo = createFilterCombo();
            if (filterCombo != null) {
                filterCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
                toolbar.add(filterCombo);
            }
        }
        JComponent extra = createExtraToolbarComponent();
        if (extra != null) toolbar.add(extra);
        card.add(toolbar, "wrap, growx");

        table = new ListTable();
        TableStyler.applyStandardStyle(table);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "width: 8");

        // Tablo ile durum paneli aynı yeri paylaşır: veri varken tablo, yokken
        // "yükleniyor / eşleşme yok / hiç kayıt yok" paneli görünür.
        statePanel = new TableStatePanel();
        tableArea = new JPanel(new CardLayout());
        tableArea.setOpaque(false);
        tableArea.add(scrollPane, CARD_DATA);
        tableArea.add(statePanel, CARD_STATE);
        card.add(tableArea, "wrap");

        JPanel footer = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[]push[]", "[center]"));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        resultCount = new JLabel(" ");
        resultCount.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        footer.add(resultCount);
        JComponent pagination = createPaginationComponent();
        if (pagination != null) footer.add(pagination);
        card.add(footer);

        add(card);

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "servicio.focusSearch");
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "servicio.focusSearch");
        getActionMap().put("servicio.focusSearch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isShowing()) focusSearch();
            }
        });
    }

    private JTextField createSearchField() {
        JTextField f = new JTextField(22);
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, getSearchPlaceholder());
        f.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,8,5,8");
        f.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                new Ikon("icons/search.svg", 16, "Label.disabledForeground"));
        f.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        f.setToolTipText("Ara (Ctrl+F)");
        f.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        return f;
    }

    /** Sayfanın tek vurgu dolgulu düğmesi: "Yeni …" + (varsa) Alt+harf kısayolu. */
    private JButton createPrimaryButton() {
        // İçerik alt etiketlerde olduğundan boyut BasicButtonUI'dan değil yerleşimden alınır.
        JButton b = new JButton() {
            @Override
            public Dimension getPreferredSize() {
                return getLayout().preferredLayoutSize(this);
            }

            @Override
            public Dimension getMinimumSize() {
                return getLayout().minimumLayoutSize(this);
            }
        };
        b.setLayout(new MigLayout("insets 0, gap 8, hidemode 3", "[][]", "[center]"));
        JLabel label = new JLabel(getNewButtonText(),
                new Ikon(getNewButtonIconPath(), 16, "Servicio.onAccentForeground"), SwingConstants.LEADING);
        label.setIconTextGap(8);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Servicio.onAccentForeground");
        b.add(label);
        QuickAction qa = getNewQuickAction();
        if (qa != null) {
            JLabel key = new JLabel(qa.getShortcutText());
            key.putClientProperty(FlatClientProperties.STYLE, "font: -2; foreground: fade($Servicio.onAccentForeground,85%);"
                    + " background: fade($Servicio.onAccentForeground,12%);"
                    + " border: 1,5,1,5,fade($Servicio.onAccentForeground,40%),1,6");
            b.add(key);
            b.setToolTipText(getNewButtonText() + " (" + qa.getShortcutText() + ")");
        }
        b.getAccessibleContext().setAccessibleName(getNewButtonText());
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 8,14,8,12; borderWidth: 0; focusWidth: 0;"
                + " innerFocusWidth: 1; background: $Component.accentColor;"
                + " hoverBackground: darken($Component.accentColor,6%); pressedBackground: darken($Component.accentColor,12%)");
        b.addActionListener(e -> onNew());
        return b;
    }

    /** Başlıkta birincil düğmenin soluna konan ikincil işlemler (ör. "Kasa Raporu"). */
    protected List<JComponent> createHeaderActions() {
        return Collections.emptyList();
    }

    /** İkincil başlık düğmesi: ikonlu, çerçeveli, arc 10. */
    protected static JButton secondaryButton(String text, String iconPath, Runnable action) {
        JButton b = new JButton(text, new Ikon(iconPath, 16, "Label.foreground"));
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,12,7,12; iconTextGap: 6");
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Birincil düğmenin kısayolu gösterilecekse ilgili {@link QuickAction}. */
    protected QuickAction getNewQuickAction() {
        return null;
    }

    protected void setTableModel(TableModel model) {
        table.setModel(model);
        initTableFilter(model);
        table.setRowSorter(sorter);

        // Görünür satır sayısı hem model değişiminden hem de istemci tarafı filtreden
        // etkilenir; ikisini de dinleyip duruma karar veriyoruz.
        model.addTableModelListener(e -> updateTableState());
        if (sorter != null) sorter.addRowSorterListener(e -> updateTableState());
        updateTableState();
    }

    /**
     * Satır tıklamasıyla (ve Enter ile) kaydı açar. {@code TableActionColumnSupport} kullanan
     * formlar bunu otomatik alır; özel işlem kolonu kuranlar kendileri çağırır.
     */
    protected <T> void openRowsWith(GenericTableModel<T> model, Consumer<T> open, int actionColumn) {
        table.setRowOpener(row -> {
            T item = model.getItemAt(table.convertRowIndexToModel(row));
            if (item != null) open.accept(item);
        }, actionColumn);
    }

    /**
     * Veri yüklemenin girişi. {@code final}: her çağrı önce "yükleniyor" durumunu gösterir,
     * asıl yükleme {@link #loadTableData()} içinde yapılır; sekme sayıları da buradan tazelenir.
     */
    protected final void refreshTable() {
        if (statePanel != null) {
            statePanel.showLoading();
            showCard(CARD_STATE);
        }
        if (clearLink != null) clearLink.setVisible(isFilterActive());
        loadTableData();
        refreshViewCounts();
        // Özet cümlesi aramadan bağımsızdır: her tuş vuruşunda değil, arama boşken (açılış,
        // kayıt ekleme/silme sonrası yenileme) tazelenir.
        if (searchField == null || searchField.getText().trim().isEmpty()) refreshStats();
    }

    /** Alt bilgi satırındaki toplam kayıt adedi (sayfalamadan bağımsız). */
    protected void setResultCount(long total) {
        if (resultCount == null) return;
        resultCount.setText(total == 0 ? " " : total + " kayıt");
    }

    /**
     * Görünür satır yoksa nedenine uygun paneli, varsa tabloyu gösterir.
     * Filtreli boşluk ile gerçekten boş liste farklı sorulardır ve farklı cevap ister.
     */
    private void updateTableState() {
        if (tableArea == null || statePanel == null) return;
        if (clearLink != null) clearLink.setVisible(isFilterActive());

        if (table.getRowCount() > 0) {
            showCard(CARD_DATA);
            return;
        }

        if (isFilterActive()) {
            statePanel.showNoResults(searchField.getText().trim(), this::clearFilters);
        } else {
            statePanel.showEmpty(getEmptyStateTitle(), getEmptyStateDescription(),
                    getNewButtonText(), this::onNew);
        }
        showCard(CARD_STATE);
    }

    private void showCard(String card) {
        ((CardLayout) tableArea.getLayout()).show(tableArea, card);
    }

    /** Arama kutusu, sekme veya filtre bir ölçüt taşıyor mu? */
    protected boolean isFilterActive() {
        boolean hasSearch = searchField != null && !searchField.getText().trim().isEmpty();
        boolean hasCombo = filterCombo != null && filterCombo.getSelectedIndex() > 0;
        boolean hasHeaderFilter = headerFilterSupport != null && headerFilterSupport.hasActiveFilters();
        boolean hasView = views != null && !views.isEmpty() && !viewFilters(views.getSelected()).isEmpty();
        return hasSearch || hasCombo || hasHeaderFilter || hasView;
    }

    /** "Filtreyi temizle" eylemi — arama, sekme, combo ve başlık filtrelerini sıfırlar. */
    protected void clearFilters() {
        if (views != null && !views.isEmpty()) views.select(firstViewKey(), false);
        if (filterCombo != null && filterCombo.getItemCount() > 0) filterCombo.setSelectedIndex(0);
        if (headerFilterSupport != null) headerFilterSupport.clearAll();
        if (searchField != null && !searchField.getText().isEmpty()) searchField.setText("");
        else applyFilter();
    }

    protected String getEmptyStateTitle() {
        return "Henüz kayıt yok";
    }

    protected String getEmptyStateDescription() {
        return "İlk kaydı oluşturduğunuzda bu liste dolmaya başlar.";
    }

    /**
     * Tablo başlığından kolon filtresi (ör. durum/tip enum'u, tarih aralığı) kurmak isteyen alt
     * sınıflar, {@code setupTable()} içinde kolonları/{@code TableColumnConfigurator}'ı uyguladıktan
     * SONRA bunu çağırır. Filtre değiştiğinde otomatik olarak {@link #refreshTable()} tetiklenir.
     */
    protected <T> TableHeaderFilterSupport<T> installHeaderFilters(List<ColumnDef<T>> columns) {
        TableHeaderFilterSupport<T> support = new TableHeaderFilterSupport<>(table, columns);
        support.setOnFilterChanged(this::refreshTable);
        this.headerFilterSupport = support;
        return support;
    }

    // --- Sıralama ---

    private final Map<String, String> sorts = new LinkedHashMap<>();
    private String sortKey;
    private JButton sortButton;

    /**
     * Sıralama seçeneği ekler (başlıksız tablolarda sütun başlığına tıklamanın yerini alır). İlk
     * eklenen varsayılandır. Düğme sekme çubuğunun sağında "Sırala: En yeni" olarak görünür.
     */
    protected void addSort(String key, String label) {
        sorts.put(key, label);
        if (sortKey == null) sortKey = key;
        if (sortButton == null && toolbar != null) {
            sortButton = new JButton();
            sortButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            sortButton.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,10,4,10; iconTextGap: 6; focusWidth: 0");
            sortButton.setIcon(new Ikon("icons/arrow-down-up.svg", 14, "Label.disabledForeground"));
            sortButton.addActionListener(e -> showSortMenu());
            toolbar.add(sortButton, "gapleft 4, shrink 0");
        }
        updateSortButton();
    }

    protected String getSortKey() {
        return sortKey;
    }

    private void showSortMenu() {
        JPopupMenu menu = new JPopupMenu();
        ButtonGroup group = new ButtonGroup();
        for (Map.Entry<String, String> e : sorts.entrySet()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(e.getValue(), e.getKey().equals(sortKey));
            item.addActionListener(a -> {
                if (e.getKey().equals(sortKey)) return;
                sortKey = e.getKey();
                updateSortButton();
                onSortChanged(sortKey);
            });
            group.add(item);
            menu.add(item);
        }
        menu.show(sortButton, 0, sortButton.getHeight() + 4);
    }

    private void updateSortButton() {
        if (sortButton == null) return;
        String label = sorts.get(sortKey);
        // Düz metin: HTML etiketi dar alanda satır atlayıp düğmeyi iki satıra bölüyordu.
        sortButton.setText("Sırala: " + (label != null ? label : ""));
    }

    /** Sıralama değişince çağrılır; sayfalı formlar sayfayı başa alıp {@code super}'i çağırmalıdır. */
    protected void onSortChanged(String key) {
        refreshTable();
    }

    // --- Görünüm sekmeleri ---

    /** Alt sınıflar {@link #addView(String, String)} ile sekmelerini ekler; ilk eklenen varsayılandır. */
    protected void initViews() {
    }

    /** Sekmenin sunucu tarafı ön ayarı (başlık filtreleriyle birleştirilir). Boş = süzgeçsiz. */
    protected Map<String, ColumnFilterValue> viewFilters(String viewKey) {
        return Collections.emptyMap();
    }

    /** Sekmeler için eşleşen kayıt sayısı; {@code null} dönerse sekmeler sayısız görünür. */
    protected CompletableFuture<Long> countMatching(Map<String, ColumnFilterValue> filters) {
        return null;
    }

    /** Sekme değişince çağrılır. Sayfalı formlar sayfayı başa alıp {@code super}'i çağırmalıdır. */
    protected void onViewChanged(String viewKey) {
        refreshTable();
    }

    /** Başlık filtreleri + seçili sekmenin ön ayarı; alt sınıfın sorgusuna bu verilir. */
    protected Map<String, ColumnFilterValue> effectiveFilters() {
        Map<String, ColumnFilterValue> filters = new LinkedHashMap<>();
        if (headerFilterSupport != null) filters.putAll(headerFilterSupport.getActiveFilters());
        if (views != null && !views.isEmpty()) filters.putAll(viewFilters(views.getSelected()));
        return filters;
    }

    /** Sekmeyi dışarıdan seçer (ör. ana sayfadan "Hazır" listesine gelmek). */
    protected void selectView(String key) {
        if (views != null) {
            if (key.equals(views.getSelected())) onViewChanged(key);
            else views.select(key, true);
        }
    }

    private String firstViewKey() {
        return viewKeys().isEmpty() ? null : viewKeys().get(0);
    }

    private final List<String> viewKeyOrder = new ArrayList<>();

    private List<String> viewKeys() {
        return viewKeyOrder;
    }

    /** {@link #initViews} içinden çağrılır: sekmeyi ekler ve sırasını tutar. */
    protected void addView(String key, String label) {
        views.addView(key, label);
        viewKeyOrder.add(key);
        views.revalidate();
    }

    /** Sekme sayılarını tazeler (sekmeler sonradan, ör. veritabanından okunarak eklendiyse). */
    protected void refreshViewCounts() {
        if (views == null || views.isEmpty()) return;
        Map<String, ColumnFilterValue> header = headerFilterSupport != null
                ? headerFilterSupport.getActiveFilters() : Collections.emptyMap();
        for (String key : viewKeys()) {
            Map<String, ColumnFilterValue> filters = new LinkedHashMap<>(header);
            filters.putAll(viewFilters(key));
            CompletableFuture<Long> count = countMatching(filters);
            if (count == null) return;
            count.thenAccept(n -> SwingUtilities.invokeLater(() -> views.setCount(key, n)))
                    .exceptionally(ex -> null);
        }
    }

    protected void applyFilter() {
        if (sorter == null) return;
        String text = searchField.getText().trim();
        List<RowFilter<TableModel, Object>> filters = new ArrayList<>();

        if (!text.isEmpty()) {
            try {
                filters.add(RowFilter.regexFilter("(?iu)" + Pattern.quote(text)));
            } catch (PatternSyntaxException e) {
                // Regex syntax hatalarını yok say
            }
        }

        RowFilter<TableModel, Object> customFilter = getCustomFilter();
        if (customFilter != null) {
            filters.add(customFilter);
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }

    // --- Görsel / Metin Özelleştirme Metodları (Override Edilebilir) ---

    protected String getNewButtonText() {
        return "Yeni Ekle";
    }

    protected String getNewButtonIconPath() {
        return "icons/plus.svg";
    }

    protected String getSearchPlaceholder() {
        return "Ara…";
    }

    // --- Filtre (ComboBox) Ayarları ---

    protected boolean hasFilterCombo() {
        return false;
    }

    protected JComboBox<Object> createFilterCombo() {
        return null;
    }

    protected RowFilter<TableModel, Object> getCustomFilter() {
        return null;
    }

    /** Alt sınıflar araç çubuğuna arama kutusunun soluna ek bir bileşen koymak için ezer. */
    protected JComponent createExtraToolbarComponent() {
        return null;
    }

    /** Sayfalı formlar DB-tabanlı sayfalama bileşeni döndürür; alt bilgi satırının sağına konur. */
    protected JComponent createPaginationComponent() {
        return null;
    }

    /** Özet cümlesini ({@link #summary}) tazeler. */
    protected void refreshStats() {
    }

    protected void initTableFilter(TableModel model) {
        sorter = new TableRowSorter<>(model);
    }

    /**
     * Sayfalanmış veri asenkron geldikten sonra çağrılmalı. JTable'ın kendi iç revalidate'i
     * sadece en yakın JViewport'a kadar yayılır; dış yerleşim bu olmadan yeniden hesaplanmaz.
     */
    protected void refreshLayout() {
        revalidate();
        repaint();
    }

    // --- Mevcut Abstract Metodlar ---

    protected abstract void setupTable();

    /**
     * Asıl veri yükleme. Alt sınıflar bunu ezer; çağrı her zaman {@link #refreshTable()}
     * üzerinden gelir, böylece "yükleniyor" durumu tek yerde yönetilir.
     */
    protected abstract void loadTableData();

    protected abstract void onNew();

    /**
     * Dışarıdan (kısayol, komut paleti, ana sayfa) "yeni kayıt" akışını başlatır.
     * Form ilk kez oluşturulduysa formInit() henüz EDT kuyruğunda bekliyor olabilir;
     * bu yüzden çağrı bir sonraki EDT döngüsüne ertelenir.
     */
    public final void startNew() {
        SwingUtilities.invokeLater(this::onNew);
    }

    /** Arama kutusuna odaklanır ve içeriğini seçer (ör. "Tahsilat Al" kısayolu müşteri aramaya iner). */
    public final void focusSearch() {
        SwingUtilities.invokeLater(() -> {
            if (searchField == null) return;
            searchField.requestFocusInWindow();
            searchField.selectAll();
        });
    }
}
