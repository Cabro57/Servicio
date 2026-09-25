package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Ayarlardaki sözlük listeleri (cihaz türleri, markalar, işçilikler, kategoriler) için ortak satır listesi.
 * <p>
 * Satır: kalın ad, altında soluk açıklama, sağda kullanım sayısı (sıfırsa soluk), en sağda satır
 * eylemleri. Eylemler yalnızca satırın üstüne gelince ya da satır seçiliyken görünür (liste
 * sayfalarıyla aynı kural); sil eylemi üstüne gelince tehlike rengine döner.
 * <p>
 * İki kullanım: <b>ana liste</b> ({@link #onSelect}) — tıklamak seçer, seçim sağdaki ayrıntıyı
 * değiştirir; <b>kayıt listesi</b> ({@link #onOpen}) — tıklamak kaydı düzenlemeye açar.
 * Klavye: ↑/↓ gezer, Enter açar, F2 ad değiştirir, Delete siler (eylemin kısayolu varsa).
 * <p>
 * Liste kendi kartını çizmez; sayfa onu bir {@code listCard} içine koyar (kart içinde kart olmasın).
 */
class DictionaryList<T> extends JPanel {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private record RowAction<T>(String icon, String tip, boolean danger, KeyStroke key,
                                Predicate<T> visible, Consumer<T> handler) {}

    /**
     * Satırlar kaydırma alanının genişliğini izler: dar kolonda satır tercih ettiği genişliğe uzayıp
     * sağdaki sayı ve eylemleri (sil) görünür alanın dışına itmesin; uzun ad kısalır ("…").
     */
    private final JPanel rows = new WidthTrackingRows();
    private final JScrollPane scroll;
    private final Function<T, String> title;
    private Function<T, String> subtitle;
    private Function<T, String> trailing;
    private Predicate<T> trailingMuted = t -> false;
    private final List<RowAction<T>> actions = new ArrayList<>();
    private Consumer<T> onSelect, onOpen;
    private List<T> items = List.of();
    private final List<Row> visibleRows = new ArrayList<>();
    private String query = "";
    private T selected;
    private String emptyTitle = "Liste boş", emptyHint = "";

    DictionaryList(Function<T, String> title) {
        super(new BorderLayout());
        this.title = title;
        setOpaque(false);
        setFocusable(true);
        rows.setOpaque(false);
        scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "width: 8");
        // Tercih edilen yükseklik bilerek küçük: liste sayfayı uzatmasın, kendi içinde kaysın.
        scroll.setPreferredSize(new Dimension(200, 200));
        add(scroll);
        bindKeys();
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { repaintRows(); }
            @Override public void focusLost(FocusEvent e) { repaintRows(); }
        });
    }

    DictionaryList<T> subtitle(Function<T, String> fn) { this.subtitle = fn; return this; }

    /** Sağdaki kullanım metni; {@code muted} doğruysa (sayı sıfır) soluk yazılır. */
    DictionaryList<T> trailing(Function<T, String> fn, Predicate<T> muted) {
        this.trailing = fn;
        this.trailingMuted = muted != null ? muted : t -> false;
        return this;
    }

    DictionaryList<T> onSelect(Consumer<T> c) { this.onSelect = c; return this; }
    DictionaryList<T> onOpen(Consumer<T> c) { this.onOpen = c; return this; }

    DictionaryList<T> action(String icon, String tip, Consumer<T> handler) {
        return action(icon, tip, false, null, null, handler);
    }

    /** Satır eylemi. {@code key} (F2, Delete) seçili satırda klavyeyle de çalışır; {@code visible} null ise her satırda. */
    DictionaryList<T> action(String icon, String tip, boolean danger, KeyStroke key, Predicate<T> visible, Consumer<T> handler) {
        actions.add(new RowAction<>(icon, tip, danger, key, visible, handler));
        return this;
    }

    void setEmptyText(String title, String hint) {
        this.emptyTitle = title;
        this.emptyHint = hint;
    }

    T getSelected() {
        return selected;
    }

    /** Listeyi yeniler; seçili kayıt yeni listede varsa seçili kalır. */
    void setItems(List<T> items) {
        this.items = items;
        T keep = selected != null && items.contains(selected) ? items.get(items.indexOf(selected)) : null;
        selected = keep;
        rebuild();
        // Ana listede ayrıntı her yenilemede güncellenir (sayılar değişmiş olabilir); seçim yoksa ilk kayıt.
        if (onSelect != null) {
            if (keep != null) onSelect.accept(keep);
            else if (!visibleRows.isEmpty()) select(visibleRows.get(0).item, true);
            else onSelect.accept(null);
        }
    }

    /** Ada (ve açıklamaya) göre süzer; Türkçe harfsiz yazım da bulur ("sarj" → "Şarj"). */
    void filter(String text) {
        query = normalize(text == null ? "" : text.trim());
        rebuild();
    }

    /** Kaydı seçer ve görünür yapar (ör. yeni eklenen kayıt). */
    void select(T item) {
        select(item, true);
    }

    // ------------------------------------------------------------------ çizim

    private void rebuild() {
        rows.removeAll();
        visibleRows.clear();
        for (T item : items) {
            if (!query.isEmpty() && !matches(item)) continue;
            Row row = new Row(item);
            visibleRows.add(row);
            rows.add(row);
        }
        if (visibleRows.isEmpty()) rows.add(emptyState(), "gaptop 28");
        rows.revalidate();
        rows.repaint();
    }

    private boolean matches(T item) {
        String haystack = title.apply(item) + " " + (subtitle != null ? nullToEmpty(subtitle.apply(item)) : "");
        return normalize(haystack).contains(query);
    }

    private JComponent emptyState() {
        JPanel empty = new JPanel(new MigLayout("insets 0 16 0 16, wrap, fillx", "[center]", "[]4[]"));
        empty.setOpaque(false);
        boolean filtered = !query.isEmpty() && !items.isEmpty();
        JLabel t = new JLabel(filtered ? "Eşleşen kayıt yok" : emptyTitle);
        t.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        empty.add(t);
        String hint = filtered ? "Aramayı değiştirin ya da temizleyin." : emptyHint;
        if (hint != null && !hint.isBlank()) {
            JTextArea h = SettingsKit.wrappingNote(hint);
            h.setColumns(28);
            empty.add(h, "wmax 340");
        }
        return empty;
    }

    private void repaintRows() {
        for (Row r : visibleRows) r.repaint();
    }

    private void select(T item, boolean notify) {
        selected = item;
        for (Row r : visibleRows) r.updateState();
        Row row = rowOf(item);
        if (row != null) SwingUtilities.invokeLater(() -> row.scrollRectToVisible(new Rectangle(0, 0, row.getWidth(), row.getHeight())));
        if (notify && onSelect != null) onSelect.accept(item);
    }

    private Row rowOf(T item) {
        for (Row r : visibleRows) if (r.item.equals(item)) return r;
        return null;
    }

    // ------------------------------------------------------------------ klavye

    private void bindKeys() {
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next", () -> move(1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "previous", () -> move(-1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open", () -> {
            if (selected != null && onOpen != null) onOpen.accept(selected);
        });
    }

    private void bind(KeyStroke key, String name, Runnable run) {
        getInputMap(WHEN_FOCUSED).put(key, name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { run.run(); }
        });
    }

    /** Eylem kısayollarını kaydeder; eylemler tanımlandıktan sonra sayfa bunu bir kez çağırır. */
    DictionaryList<T> bindActionKeys() {
        for (int i = 0; i < actions.size(); i++) {
            RowAction<T> a = actions.get(i);
            if (a.key() == null) continue;
            bind(a.key(), "rowAction" + i, () -> {
                if (selected != null && (a.visible() == null || a.visible().test(selected))) a.handler().accept(selected);
            });
        }
        return this;
    }

    private void move(int delta) {
        if (visibleRows.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < visibleRows.size(); i++) if (visibleRows.get(i).item.equals(selected)) index = i;
        int next = Math.max(0, Math.min(visibleRows.size() - 1, index + delta));
        select(visibleRows.get(next).item, onSelect != null);
    }

    private static final class WidthTrackingRows extends JPanel implements Scrollable {
        WidthTrackingRows() {
            super(new MigLayout("insets 0, fillx, wrap, gap 0 1", "[grow, fill]", ""));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(16, r.height - 32); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ------------------------------------------------------------------ satır

    private final class Row extends JPanel {
        final T item;
        private final JPanel actionBar = new JPanel(new MigLayout("insets 0, gap 2", "", "[center]"));
        private boolean hover;

        Row(T item) {
            super(new MigLayout("insets 7 12 7 6, fillx, gap 12 0, hidemode 3", "[grow, fill, shrink 100][right, shrink 0][right, shrink 0]", "[]1[]"));
            this.item = item;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            String sub = subtitle != null ? subtitle.apply(item) : null;
            boolean twoLines = sub != null && !sub.isBlank();
            String span = twoLines ? "spany 2, " : "";

            JLabel name = new JLabel(title.apply(item));
            name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            add(name, "wmin 0");

            String trail = trailing != null ? trailing.apply(item) : null;
            JLabel right = new JLabel(trail != null ? trail : "");
            right.putClientProperty(FlatClientProperties.STYLE, trailingMuted.test(item)
                    ? "font: -1; foreground: $Label.disabledForeground" : "font: -1; foreground: $Label.foreground");
            add(right, span + "aligny center");

            actionBar.setOpaque(false);
            for (RowAction<T> a : actions) {
                if (a.visible() != null && !a.visible().test(item)) continue;
                actionBar.add(iconButton(a, item));
            }
            // Eylemler gizliyken de yer tutsun: üstüne gelince satırdaki sayı yerinden oynamasın.
            add(actionBar, span + "aligny center, wrap, w " + actionBar.getPreferredSize().width + "!");

            if (twoLines) {
                JLabel s = new JLabel(sub);
                s.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
                s.setToolTipText(sub);
                add(s, "wmin 0");
            }

            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { setHover(true); }

                @Override public void mouseExited(MouseEvent e) {
                    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Row.this);
                    if (!Row.this.contains(p)) setHover(false);
                }

                @Override public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    DictionaryList.this.requestFocusInWindow();
                    select(item, onSelect != null);
                    if (onOpen != null && onSelect == null) onOpen.accept(item);
                }
            };
            addMouseListener(mouse);
            for (Component c : getComponents()) if (c instanceof JLabel) c.addMouseListener(mouse);
            for (Component c : actionBar.getComponents()) c.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { setHover(true); }
            });
            updateState();
        }

        private void setHover(boolean h) {
            hover = h;
            updateState();
        }

        void updateState() {
            boolean show = hover || item.equals(selected);
            for (Component c : actionBar.getComponents()) c.setVisible(show);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean isSelected = item.equals(selected);
            Color bg = isSelected ? UIManager.getColor("Servicio.rowSelectedBackground")
                    : hover ? UIManager.getColor("Servicio.rowHoverBackground") : null;
            if (bg != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = UIScale.scale(10);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                // Klavye odağı listedeyken seçili satırın ince çerçevesi: ↑/↓ nerede olduğunu göstersin.
                if (isSelected && DictionaryList.this.isFocusOwner()) {
                    g2.setColor(UIManager.getColor("Component.focusColor"));
                    g2.setStroke(new BasicStroke(UIScale.scale(1f)));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                }
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private JButton iconButton(RowAction<T> a, T item) {
        Ikon normal = new Ikon(a.icon(), 15, "Label.foreground");
        JButton b = new JButton(normal);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,4,4,4");
        String tip = a.key() != null ? a.tip() + " (" + keyText(a.key()) + ")" : a.tip();
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(a.tip());
        b.setFocusable(false);
        if (a.danger()) {
            Ikon hot = new Ikon(a.icon(), 15, "Servicio.dangerColor");
            b.getModel().addChangeListener(e -> b.setIcon(b.getModel().isRollover() ? hot : normal));
        }
        b.addActionListener(e -> a.handler().accept(item));
        return b;
    }

    private static String keyText(KeyStroke key) {
        return KeyEvent.getKeyText(key.getKeyCode());
    }

    /** Türkçe harfleri sadeleştirir: "sarj" da "Şarj"ı bulsun. */
    static String normalize(String text) {
        return text.toLowerCase(TR)
                .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
