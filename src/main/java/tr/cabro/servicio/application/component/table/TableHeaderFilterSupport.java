package tr.cabro.servicio.application.component.table;

import lombok.Setter;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.FilterType;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.model.contract.Visualizable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tablo başlığından kolon bazlı filtreleme (Excel benzeri: başlıkta ikon + popup) ekler.
 * <p>
 * Bilinçli olarak {@code Form}/{@code AbstractTableForm} hiyerarşisini bilmez — sadece bir
 * {@link JTable} ve o tablonun {@link ColumnDef} listesiyle çalışır. Bu sayede forms/ tarafında
 * ileride yapılacak bir düzenleme mekanizmayı etkilemez, taşınabilir kalır.
 * <p>
 * Filtreleme sunucu tarafında yapıldığından ({@code refreshTable()} yeniden sorgu atar), bu sınıf
 * satırları kendisi filtrelemez — sadece aktif filtre durumunu tutar ve değiştiğinde
 * {@link #setOnFilterChanged(Runnable)} ile verilen callback'i tetikler.
 */
public class TableHeaderFilterSupport<T> {

    private final JTable table;
    private final List<ColumnDef<T>> columns;
    private final Map<Integer, ColumnFilterValue> activeFilters = new HashMap<>();

    @Setter
    private Runnable onFilterChanged;

    public TableHeaderFilterSupport(JTable table, List<ColumnDef<T>> columns) {
        this.table = table;
        this.columns = columns;
        install();
    }

    /** Sadece SQL kolonu aktif olanları döner — repository katmanına doğrudan geçirilebilir. */
    public Map<String, ColumnFilterValue> getActiveFilters() {
        Map<String, ColumnFilterValue> result = new HashMap<>();
        for (Map.Entry<Integer, ColumnFilterValue> entry : activeFilters.entrySet()) {
            ColumnDef<T> def = columns.get(entry.getKey());
            if (def.getFilterColumn() != null && entry.getValue().isActive()) {
                result.put(def.getFilterColumn(), entry.getValue());
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Kurulum
    // -------------------------------------------------------------------------

    private void install() {
        // Sıralama tıklaması ile filtre ikonu tıklamasının çakışmaması için filtrelenebilir
        // kolonlarda başlığa tıklayınca sıralama tetiklenmesin (filtre ikonu zaten aynı bölgede).
        if (table.getRowSorter() instanceof TableRowSorter) {
            TableRowSorter<?> sorter = (TableRowSorter<?>) table.getRowSorter();
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).isFilterable()) sorter.setSortable(i, false);
            }
        }

        TableCellRenderer existingRenderer = table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((headerTable, value, isSelected, hasFocus, row, column) -> {
            JLabel label = (JLabel) existingRenderer.getTableCellRendererComponent(headerTable, value, isSelected, hasFocus, row, column);
            if (column < columns.size() && columns.get(column).isFilterable()) {
                boolean active = isColumnActive(column);
                label.setIcon(new Ikon("icons/filter.svg", 12, active ? "Component.accentColor" : "Label.disabledForeground"));
                label.setHorizontalTextPosition(SwingConstants.LEADING);
                label.setIconTextGap(6);
            } else {
                label.setIcon(null);
            }
            return label;
        });

        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = table.columnAtPoint(e.getPoint());
                if (column < 0 || column >= columns.size()) return;
                ColumnDef<T> def = columns.get(column);
                if (!def.isFilterable()) return;

                // Etiket hizalamasına göre ikon her zaman hücrenin sağ kenarında olmayabiliyor
                // (ör. ortalı/sola yaslı başlıklar) — bu yüzden ikon bölgesiyle sınırlamak yerine
                // filtrelenebilir kolonun tüm başlık hücresi tıklanabilir kabul ediliyor. Zaten
                // bu kolonlarda sıralama (sort) devre dışı bırakıldığı için çakışma olmaz.
                Rectangle cellRect = table.getTableHeader().getHeaderRect(column);
                showPopup(column, def, cellRect);
            }
        });
    }

    private boolean isColumnActive(int column) {
        ColumnFilterValue value = activeFilters.get(column);
        return value != null && value.isActive();
    }

    // -------------------------------------------------------------------------
    // Popup içeriği
    // -------------------------------------------------------------------------

    private void showPopup(int column, ColumnDef<T> def, Rectangle cellRect) {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());

        JPanel content;
        if (def.getFilterType() == FilterType.ENUM) {
            content = buildEnumFilterPanel(column, def, popup);
        } else if (def.getFilterType() == FilterType.DATE_RANGE) {
            content = buildDateRangeFilterPanel(column, popup);
        } else {
            return;
        }

        popup.add(content, BorderLayout.CENTER);
        popup.show(table.getTableHeader(), cellRect.x, cellRect.y + cellRect.height);
    }

    private JPanel buildEnumFilterPanel(int column, ColumnDef<T> def, JPopupMenu popup) {
        ColumnFilterValue current = activeFilters.get(column);
        Set<String> selected = new HashSet<>(current != null && current.getEnumValues() != null ? current.getEnumValues() : Set.of());

        JPanel panel = new JPanel(new MigLayout("insets 10, wrap 1", "[fill,grow]"));
        List<JCheckBox> checkBoxes = new java.util.ArrayList<>();

        for (Enum<?> constant : def.getEnumClass().getEnumConstants()) {
            String label = constant instanceof Visualizable ? ((Visualizable) constant).getDisplayName() : constant.toString();
            JCheckBox checkBox = new JCheckBox(label, selected.contains(constant.name()));
            checkBox.putClientProperty("enumName", constant.name());
            checkBoxes.add(checkBox);
            panel.add(checkBox);
        }

        panel.add(new JSeparator(), "growx");

        JPanel buttons = new JPanel(new MigLayout("insets 0", "[grow][]"));
        JButton clearButton = new JButton("Temizle");
        JButton applyButton = new JButton("Uygula");
        buttons.add(clearButton);
        buttons.add(applyButton, "align right");
        panel.add(buttons, "growx");

        clearButton.addActionListener(e -> {
            activeFilters.remove(column);
            popup.setVisible(false);
            refreshHeaderAndNotify();
        });

        applyButton.addActionListener(e -> {
            Set<String> newSelection = new HashSet<>();
            for (JCheckBox cb : checkBoxes) {
                if (cb.isSelected()) newSelection.add((String) cb.getClientProperty("enumName"));
            }
            ColumnFilterValue value = new ColumnFilterValue();
            value.setEnumValues(newSelection);
            activeFilters.put(column, value);
            popup.setVisible(false);
            refreshHeaderAndNotify();
        });

        return panel;
    }

    private JPanel buildDateRangeFilterPanel(int column, JPopupMenu popup) {
        ColumnFilterValue current = activeFilters.get(column);

        JPanel panel = new JPanel(new MigLayout("insets 10, wrap 1", "[fill,grow]"));
        panel.add(new JLabel("Tarih Aralığı:"));

        // DatePicker kendisi bir JPanel'dir (takvim ızgarasını doğrudan çizer) — kendi
        // popup-editor mekanizması kullanılmadan doğrudan bu popup'ın içine gömülüyor.
        DatePicker datePicker = new DatePicker();
        datePicker.setDateSelectionMode(DatePicker.DateSelectionMode.BETWEEN_DATE_SELECTED);
        if (current != null && current.getDateFrom() != null && current.getDateTo() != null) {
            datePicker.setSelectedDateRange(current.getDateFrom(), current.getDateTo());
        }
        panel.add(datePicker, "growx");

        JPanel buttons = new JPanel(new MigLayout("insets 0", "[grow][]"));
        JButton clearButton = new JButton("Temizle");
        JButton applyButton = new JButton("Uygula");
        buttons.add(clearButton);
        buttons.add(applyButton, "align right");
        panel.add(buttons, "growx");

        clearButton.addActionListener(e -> {
            activeFilters.remove(column);
            datePicker.clearSelectedDate();
            popup.setVisible(false);
            refreshHeaderAndNotify();
        });

        applyButton.addActionListener(e -> {
            LocalDate[] range = datePicker.getSelectedDateRange();
            ColumnFilterValue value = new ColumnFilterValue();
            if (range != null) {
                value.setDateFrom(range[0]);
                value.setDateTo(range[1]);
            }
            activeFilters.put(column, value);
            popup.setVisible(false);
            refreshHeaderAndNotify();
        });

        return panel;
    }

    private void refreshHeaderAndNotify() {
        table.getTableHeader().repaint();
        if (onFilterChanged != null) onFilterChanged.run();
    }
}
