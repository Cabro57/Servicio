package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ayarlardaki sözlük listeleri (parça kategorileri, cihaz türleri, işçilikler) için ortak satır
 * listesi. Her satır: kalın ad, altında soluk açıklama, sağda soluk sayı/tutar; düzenle ve sil
 * düğmeleri yalnızca satırın üstüne gelince ya da satır seçiliyken görünür (liste sayfalarıyla
 * aynı kural). Satırın kendisine tıklamak {@code onSelect}'i çağırır.
 */
class DictionaryList<T> extends JPanel {

    private final JPanel rows = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0 0", "[grow, fill]", ""));
    private final Function<T, String> title;
    private final Function<T, String> subtitle;
    private final Function<T, String> trailing;
    private Consumer<T> onSelect, onEdit, onDelete;
    private T selected;
    private String emptyTitle = "Liste boş", emptyHint = "";

    DictionaryList(Function<T, String> title, Function<T, String> subtitle, Function<T, String> trailing) {
        super(new MigLayout("insets 4 6 4 6, fill", "[grow, fill]", "[grow, fill]"));
        this.title = title;
        this.subtitle = subtitle;
        this.trailing = trailing;
        putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        rows.setOpaque(false);
        JScrollPane scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(200, 200));
        add(scroll);
    }

    DictionaryList<T> onSelect(Consumer<T> c) { this.onSelect = c; return this; }
    DictionaryList<T> onEdit(Consumer<T> c) { this.onEdit = c; return this; }
    DictionaryList<T> onDelete(Consumer<T> c) { this.onDelete = c; return this; }

    void setEmptyText(String title, String hint) {
        this.emptyTitle = title;
        this.emptyHint = hint;
    }

    T getSelected() {
        return selected;
    }

    void setItems(List<T> items, T select) {
        rows.removeAll();
        selected = null;
        if (items.isEmpty()) {
            JPanel empty = new JPanel(new MigLayout("insets 24 10 24 10, wrap, fillx", "[center]", "[]4[]"));
            empty.setOpaque(false);
            JLabel t = new JLabel(emptyTitle);
            t.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            empty.add(t);
            empty.add(SettingsKit.note(emptyHint));
            rows.add(empty);
        }
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) rows.add(new JSeparator(), "growx, gapx 8 8");
            rows.add(new Row(items.get(i)));
        }
        rows.revalidate();
        rows.repaint();
        if (select != null) {
            for (Component c : rows.getComponents()) {
                if (!(c instanceof DictionaryList.Row)) continue;
                DictionaryList<?>.Row row = (DictionaryList<?>.Row) c;
                if (select.equals(row.item)) row.choose();
            }
        }
    }

    private final class Row extends JPanel {
        final T item;
        private final JPanel actions = new JPanel(new MigLayout("insets 0, gap 2", "", "[center]"));
        private boolean hover;

        Row(T item) {
            super(new MigLayout("insets 7 10 7 6, fillx, gap 10 0", "[grow][right][70!, right]", "[]0[]"));
            this.item = item;
            setOpaque(false);
            setCursor(onSelect != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());

            JLabel name = new JLabel(title.apply(item));
            name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            String sub = subtitle != null ? subtitle.apply(item) : null;
            String trail = trailing != null ? trailing.apply(item) : null;
            JLabel right = new JLabel(trail != null ? trail : "");
            right.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

            actions.setOpaque(false);
            if (onEdit != null) actions.add(iconButton("icons/pencil.svg", "Düzenle", () -> onEdit.accept(item)));
            if (onDelete != null) actions.add(iconButton("icons/trash-2.svg", "Sil", () -> onDelete.accept(item)));
            actions.setVisible(false);

            add(name, "wmin 0");
            add(right, sub != null && !sub.isBlank() ? "spany 2" : "");
            add(actions, (sub != null && !sub.isBlank() ? "spany 2, " : "") + "wrap");
            if (sub != null && !sub.isBlank()) {
                JLabel s = new JLabel(sub);
                s.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
                add(s, "wmin 0");
            }

            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { setHover(true); }
                @Override public void mouseExited(MouseEvent e) {
                    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Row.this);
                    if (!Row.this.contains(p)) setHover(false);
                }
                @Override public void mouseClicked(MouseEvent e) { choose(); }
            };
            addMouseListener(mouse);
            for (Component c : getComponents()) if (c instanceof JLabel) c.addMouseListener(mouse);
            for (Component c : actions.getComponents()) c.addMouseListener(mouse);
        }

        void choose() {
            selected = item;
            for (Component c : rows.getComponents()) if (c instanceof DictionaryList.Row) c.repaint();
            for (Component c : rows.getComponents()) {
                if (c instanceof DictionaryList.Row) ((DictionaryList<?>.Row) c).updateActions();
            }
            if (onSelect != null) onSelect.accept(item);
        }

        private void setHover(boolean h) {
            hover = h;
            updateActions();
            repaint();
        }

        void updateActions() {
            actions.setVisible(hover || item.equals(selected));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color bg = item.equals(selected) ? UIManager.getColor("Servicio.rowSelectedBackground")
                    : hover ? UIManager.getColor("Servicio.rowHoverBackground") : null;
            if (bg != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                int arc = com.formdev.flatlaf.util.UIScale.scale(10);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private static JButton iconButton(String icon, String tip, Runnable action) {
        JButton b = new JButton(new Ikon(icon, 15, "Label.foreground"));
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,4,4,4");
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        b.addActionListener(e -> action.run());
        return b;
    }
}
