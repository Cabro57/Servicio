package tr.cabro.servicio.application.forms.base;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.dashboard.CardBox;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.component.table.TableStatePanel;
import tr.cabro.servicio.application.component.table.TableStyler;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class AbstractTableForm extends Form {

    protected JTextField searchField;
    protected JTable table;
    protected JButton btnNew;
    protected CardBox cardBox;
    protected JComboBox<Object> filterCombo;
    protected TableRowSorter<? extends TableModel> sorter;

    private static final String CARD_DATA = "data";
    private static final String CARD_STATE = "state";

    private JPanel tableArea;
    private TableStatePanel statePanel;
    private TableHeaderFilterSupport<?> headerFilterSupport;

    public AbstractTableForm() {
        // init formInit() üzerinden çağrılır.
    }

    @Override
    public void formInit() {
        initComponent();
        setupTable();
        refreshTable();
        refreshStats();   // Alt sınıfların kart verilerini güncellemesi için
    }

    @Override
    public void formRefresh() {
        refreshTable();
        refreshStats();
    }

    @Override
    public void formOpen() {
        refreshTable();
        refreshStats();
    }

    private void initComponent() {
        // FormServices ile aynı ana layout
        setLayout(new MigLayout("fill, insets 15, gap 10, wrap", "[grow]", "[pref][pref][grow, fill]"));

        // 1. Üst Kısım (Header Panel)
        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[][]"));
        headerPanel.setOpaque(false);

        SystemForm sysForm = this.getClass().getAnnotation(SystemForm.class);
        String titleText = sysForm != null ? sysForm.name() : "Başlık";
        String subtitleText = sysForm != null ? sysForm.description() : "Açıklama";

        JLabel title = new JLabel(titleText);
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold $h1.font");

        JLabel subtitle = new JLabel(subtitleText);
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        btnNew = new JButton(getNewButtonText());
        btnNew.putClientProperty(FlatClientProperties.STYLE, "background: $Component.accentColor; foreground: #ffffff; arc: 10; margin: 5,10,5,10; iconTextGap: 23;");
        btnNew.setIcon(new Ikon(getNewButtonIconPath(), btnNew.getFont().getSize()));
        btnNew.addActionListener(e -> onNew());

        headerPanel.add(title, "cell 0 0");
        headerPanel.add(btnNew, "cell 1 0 1 2, aligny center"); // Butonu sağa yasla ve ortala
        headerPanel.add(subtitle, "cell 0 1");

        add(headerPanel, "wrap, growx");

        // 2. İstatistik Kartları (Stats Panel)
        JPanel statsPanel = new JPanel(new MigLayout("insets 0, gapx 15, fillx", "[fill]", "[fill]"));
        statsPanel.setOpaque(false);

        cardBox = new CardBox();

        // Alt sınıfların kart şablonlarını oluşturması için boş çağrı
        initCards();

        // Eğer alt sınıf kart eklemişse paneli görünüme dahil et
        if (cardBox.getComponentCount() > 0) {
            statsPanel.add(cardBox);
            add(statsPanel, "wrap, growx");
        }

        // 3. Tablo Konteyneri
        JPanel tableContainer = new JPanel(new MigLayout("fill, insets 15, gapy 15", "[grow]", "[pref][grow][pref!]"));
        tableContainer.putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");

        JLabel tableTitle = new JLabel(getTableTitleText());
        tableTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");

        searchField = new JTextField(20);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, getSearchPlaceholder());
        searchField.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg", 1f));

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JPanel toolbar = new JPanel(new MigLayout("insets 0, gapx 10", "[][grow][][][]"));
        toolbar.setOpaque(false);
        toolbar.add(tableTitle);
        toolbar.add(searchField, "cell 2 0");

        if (hasFilterCombo()) {
            filterCombo = createFilterCombo();
            if (filterCombo != null) {
                filterCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
                toolbar.add(filterCombo, "cell 3 0");
            }
        }

        JComponent extraToolbarComponent = createExtraToolbarComponent();
        if (extraToolbarComponent != null) {
            toolbar.add(extraToolbarComponent, "cell 4 0");
        }

        table = new JTable();

        // Modern Tablo Stili
        TableStyler.applyStandardStyle(table);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Tablo ile durum paneli aynı yeri paylaşır: veri varken tablo, yokken
        // "yükleniyor / eşleşme yok / hiç kayıt yok" paneli görünür.
        statePanel = new TableStatePanel();
        tableArea = new JPanel(new CardLayout());
        tableArea.setOpaque(false);
        tableArea.add(scrollPane, CARD_DATA);
        tableArea.add(statePanel, CARD_STATE);

        tableContainer.add(toolbar, "wrap, growx, pushx");
        tableContainer.add(tableArea, "grow, push, wrap");

        // Alt sınıflar DB-tabanlı sayfalama bileşeni (örn. JPagination) döndürebilir.
        JComponent paginationComponent = createPaginationComponent();
        if (paginationComponent != null) {
            tableContainer.add(paginationComponent, "align center");
        }

        add(tableContainer, "grow");
    }

    /** Alt sınıflar DB-tabanlı sayfalama için bir bileşen (örn. JPagination) döndürebilir. */
    protected JComponent createPaginationComponent() {
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
     * Veri yüklemenin girişi. {@code final}: her çağrı önce "yükleniyor" durumunu gösterir,
     * asıl yükleme {@link #loadTableData()} içinde yapılır. Böylece alt sınıfların onlarca
     * {@code refreshTable()} çağrısının tamamı tek yerden durum bildirimi kazanır.
     */
    protected final void refreshTable() {
        if (statePanel != null) {
            statePanel.showLoading();
            showCard(CARD_STATE);
        }
        loadTableData();
    }

    /**
     * Görünür satır yoksa nedenine uygun paneli, varsa tabloyu gösterir.
     * Filtreli boşluk ile gerçekten boş liste farklı sorulardır ve farklı cevap ister.
     */
    private void updateTableState() {
        if (tableArea == null || statePanel == null) return;

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

    /** Arama kutusu veya filtre combo'su bir ölçüt taşıyor mu? */
    protected boolean isFilterActive() {
        boolean hasSearch = searchField != null && !searchField.getText().trim().isEmpty();
        boolean hasCombo = filterCombo != null && filterCombo.getSelectedIndex() > 0;
        boolean hasHeaderFilter = headerFilterSupport != null && headerFilterSupport.hasActiveFilters();
        return hasSearch || hasCombo || hasHeaderFilter;
    }

    /** "Filtreyi temizle" eylemi — arama metnini ve varsa combo seçimini sıfırlar. */
    protected void clearFilters() {
        if (searchField != null) searchField.setText("");
        if (filterCombo != null && filterCombo.getItemCount() > 0) filterCombo.setSelectedIndex(0);
        if (headerFilterSupport != null) headerFilterSupport.clearAll();
        applyFilter();
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
     * SONRA bunu çağırır. Filtre değiştiğinde otomatik olarak {@link #refreshTable()} tetiklenir
     * (sunucu tarafı filtreleme — bkz. ilgili servis/repository metotları).
     * <p>
     * {@link TableHeaderFilterSupport} bilinçli olarak bu sınıfın iç yapısını bilmez; sadece
     * {@code table} ve {@code columns} ile çalışır, taşınabilir kalır.
     */
    protected <T> TableHeaderFilterSupport<T> installHeaderFilters(List<ColumnDef<T>> columns) {
        TableHeaderFilterSupport<T> support = new TableHeaderFilterSupport<>(table, columns);
        support.setOnFilterChanged(this::refreshTable);
        // Boş sonuç panelinin doğru soruyu sorabilmesi ve "Filtreyi temizle"nin başlık
        // filtrelerini de kaldırabilmesi için referans burada tutulur.
        this.headerFilterSupport = support;
        return support;
    }

    protected void applyFilter() {
        if (sorter == null) return;
        String text = searchField.getText().trim();
        List<RowFilter<TableModel, Object>> filters = new ArrayList<>();

        if (!text.isEmpty()) {
            try {
                // Her kolonda kelime arayabilmek için regex
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

    protected String getTableTitleText() {
        return "Tüm Kayıtlar";
    }

    protected String getSearchPlaceholder() {
        return "Ara...";
    }

    // --- Filtre (ComboBox) Ayarları ---

    protected boolean hasFilterCombo() {
        return false;
    }

    protected JComboBox<Object> createFilterCombo() {
        return null;
    }

    protected RowFilter<TableModel, Object> getCustomFilter() {
        return null; // Eğer combobox tabanlı özel filtre lazımsa ezilir
    }

    /** Alt sınıflar toolbar'a filtreCombo'nun yanına ek bir bileşen (ör. toplu işlem butonu) koymak için ezer. */
    protected JComponent createExtraToolbarComponent() {
        return null;
    }

    // --- İstatistik (CardBox) Ayarları ---

    protected void initCards() {
        // Alt sınıflar cardBox.addCardItem(...) ile kart eklemek için bu metodu ezer
    }

    protected void refreshStats() {
        // Alt sınıflar cardBox.setValueAt(...) ile kart verilerini güncellemek için bu metodu ezer
    }

    protected void initTableFilter(TableModel model) {
        sorter = new TableRowSorter<>(model);
    }

    /**
     * Sayfalanmış veri asenkron geldikten sonra çağrılmalı. JTable'ın kendi iç
     * revalidate'i sadece en yakın JViewport'a kadar yayılır (JViewport bir
     * "validate root"tur) — dıştaki tableContainer MigLayout'unun "[grow]" satırı
     * bu olmadan yeniden hesaplanmaz ve tablo ilk açılışta sadece birkaç satırlık
     * yer kaplar (başka forma geçip dönünce MainForm'un genel revalidate'i düzeltir).
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