package tr.cabro.servicio.application.forms.base;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.dashboard.CardBox;
import raven.modal.system.Form;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.application.util.Ikon;

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
        JPanel tableContainer = new JPanel(new MigLayout("fill, insets 15, gapy 15", "[grow]", "[pref][grow]"));
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

        JPanel toolbar = new JPanel(new MigLayout("insets 0, gapx 10", "[][grow][][]"));
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

        table = new JTable();

        // Modern Tablo Stili
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:40; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font: $large.font;");
        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:52; showHorizontalLines:true; intercellSpacing:0,1; " +
                        "cellFocusColor:$TableHeader.hoverBackground; selectionBackground:$TableHeader.hoverBackground; " +
                        "selectionForeground:$Table.foreground; font: $large.font;");

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        tableContainer.add(toolbar, "wrap, growx, pushx");
        tableContainer.add(scrollPane, "grow, push");

        add(tableContainer, "grow");
    }

    protected void setTableModel(TableModel model) {
        table.setModel(model);
        initTableFilter(model);
        table.setRowSorter(sorter);
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

    // --- Mevcut Abstract Metodlar ---

    protected abstract void setupTable();
    protected abstract void refreshTable();
    protected abstract void onNew();
}