package tr.cabro.servicio.forms.base;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.system.Form;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.PatternSyntaxException;

public abstract class AbstractTableForm<T> extends Form {

    protected JTextField searchField;
    protected JTable table;
    protected JScrollPane tableScroll;
    protected JPanel tablePanel;
    protected JButton newButton, editButton, deleteButton, refreshButton; // Refresh butonu eklendi
    protected TableRowSorter<? extends TableModel> sorter;

    public AbstractTableForm() {
        initLayout();
        styleComponents();
        attachListeners();
    }

    @Override
    public void formInit() {
        // Form ilk açıldığında verileri yükle
        refreshTable();
        updateButtonStates(); // Başlangıçta buton durumlarını ayarla
    }

    @Override
    public void formRefresh() {
        refreshTable();
        updateButtonStates();
    }

    private void initLayout() {
        // Layout iyileştirmesi: Butonları sağa yasladık ve 'sg' (size group) ile eşitledik.
        setLayout(new MigLayout("fill, insets 10, gap 10, wrap", "[grow][][][][]", "[]10[grow]"));

        searchField = new JTextField();
        newButton = new JButton("Yeni");
        editButton = new JButton("Düzenle");
        deleteButton = new JButton("Sil");
        refreshButton = new JButton(new FlatSVGIcon("icons/refresh.svg", 0.4f)); // İkon yolunuzu kontrol edin

        add(searchField, "growx");
        add(newButton, "sg btn, w 100!");   // 'sg btn': Hepsi aynı genişlikte olsun
        add(editButton, "sg btn, w 100!");
        add(deleteButton, "sg btn, w 100!");
        add(refreshButton, "w 40!, wrap");  // Küçük kare refresh butonu

        table = new JTable();
        // Tablo boşken gösterilecek placeholder (İsteğe bağlı)
        // table.setFillsViewportHeight(true);

        tableScroll = new JScrollPane(table);
        tablePanel = new JPanel(new MigLayout("fill", "fill, grow", "fill, grow"));
        tablePanel.add(tableScroll, "");

        add(tablePanel, "span 5, grow, pushy");
    }

    private void styleComponents() {
        tablePanel.putClientProperty(FlatClientProperties.STYLE, "arc:18; background:$Table.background");

        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:30; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font:bold;");
        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:25; showHorizontalLines:true; intercellSpacing:0,1; selectionBackground:$TableHeader.hoverBackground; selectionForeground:$Table.foreground;");

        tableScroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE,
                "trackArc:999; trackInsets:3,3,3,3; thumbInsets:3,3,3,3; background:$Table.background;");

        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ara...");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icons/search.svg", 0.4f));
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        searchField.putClientProperty(FlatClientProperties.STYLE,
                "arc:15; borderWidth:0; focusWidth:0; innerFocusWidth:0; margin:5,20,5,20; background:$Table.background");

        // Butonlara ikon eklemek isterseniz buraya ekleyebilirsiniz
        // newButton.setIcon(new FlatSVGIcon("icons/add.svg"));
    }

    private void attachListeners() {
        // Arama dinleyicisi
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        // Buton aksiyonları
        newButton.addActionListener(e -> onNew());
        editButton.addActionListener(e -> onEdit());
        deleteButton.addActionListener(e -> onDelete());
        refreshButton.addActionListener(e -> {
            refreshTable();
            updateButtonStates();
        });

        // Tablo seçim dinleyicisi (Butonları aktif/pasif yapmak için)
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonStates();
            }
        });

        // Çift tıklama ile düzenleme (UX İyileştirmesi)
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    onEdit(); // Çift tıklayınca düzenleme penceresini aç
                }
            }
        });
    }

    /**
     * Tablodan seçim yapılmadığında Düzenle ve Sil butonlarını pasif yapar.
     */
    protected void updateButtonStates() {
        boolean hasSelection = table.getSelectedRow() != -1;
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
    }

    protected void applyFilter() {
        if (sorter == null) return;
        String text = searchField.getText().trim();

        try {
            // (?i) case-insensitive flag'idir.
            sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + text));
        } catch (PatternSyntaxException e) {
            // Kullanıcı regex karakterleri (*, [, +) girdiğinde uygulama çökmemesi için
            // Hatalı regex durumunda filtreyi temizle veya logla
            // Opsiyonel: searchField kırmızı yapılabilir.
        }
    }

    protected void setTableModel(TableModel model) {
        table.setModel(model);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        updateButtonStates(); // Model değişince seçim sıfırlanır
    }

    // Soyut metodlar
    protected abstract void refreshTable();
    protected abstract void onNew();
    protected abstract void onEdit();
    protected abstract void onDelete();
}