package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Liste sayfasının sayılı görünüm sekmeleri: "Tümü 69 · Tamirde 15 · Hazır 10". Her sekme
 * önceden tanımlı bir süzgeçtir; sayı, o süzgeçle (ve o anki aramayla) eşleşen kayıt adedidir.
 * Sekmeler düz metindir: seçili olan kalın yazılır, altına 2px vurgu çizgisi çekilir ve sayısı
 * vurgu tonlu küçük bir hapta durur. Vurgu dolgusu yalnızca sayfanın birincil düğmesindedir.
 */
public class ViewTabs extends JPanel {

    private final Map<String, Tab> tabs = new LinkedHashMap<>();
    private final ButtonGroup group = new ButtonGroup();
    private Consumer<String> onChange;
    private String selected;
    private boolean silent;

    public ViewTabs() {
        super(null);
        setLayout(new OverflowLayout());
        setOpaque(false);
        more = new Tab("Diğer");
        more.setFocusable(true);
        more.setVisible(false);
        more.addActionListener(e -> { more.setSelected(false); showOverflowMenu(); });
        add(more);
    }

    /** Sığmayan sekmeleri toplayan "Diğer" düğmesi (toggle grubunda değildir, seçilmez). */
    private final Tab more;
    private java.util.List<String> hiddenKeys = new java.util.ArrayList<>();

    private void showOverflowMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (String key : hiddenKeys) {
            Tab t = tabs.get(key);
            String text = t.label + (t.count == null ? "" : "   " + t.count);
            JMenuItem item = new JMenuItem(text);
            item.addActionListener(e -> select(key, true));
            menu.add(item);
        }
        menu.show(more, 0, more.getHeight());
    }

    /**
     * Sekmeler sığdığı kadar soldan dizilir; sığmayanlar "Diğer" menüsüne girer. Seçili sekme
     * hiçbir zaman gizlenmez: gerekirse son görünür sekmenin yerini alır.
     */
    private final class OverflowLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component comp) {}
        @Override public void removeLayoutComponent(Component comp) {}

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int w = 0, h = 0;
            for (Tab t : tabs.values()) {
                Dimension d = t.getPreferredSize();
                w += d.width + gap();
                h = Math.max(h, d.height);
            }
            return new Dimension(Math.max(0, w - gap()), h > 0 ? h : more.getPreferredSize().height);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(more.getPreferredSize().width, more.getPreferredSize().height);
        }

        private int gap() { return UIScale.scale(4); }

        @Override
        public void layoutContainer(Container parent) {
            java.util.List<String> keys = new java.util.ArrayList<>(tabs.keySet());
            int avail = parent.getWidth();
            int h = parent.getHeight();

            int total = 0;
            for (String k : keys) total += tabs.get(k).getPreferredSize().width + gap();
            total -= gap();

            java.util.Set<String> visible = new java.util.LinkedHashSet<>();
            if (total <= avail) {
                visible.addAll(keys);
            } else {
                int moreW = more.getPreferredSize().width;
                int used = 0;
                for (String k : keys) {
                    int w = tabs.get(k).getPreferredSize().width + gap();
                    if (used + w + moreW > avail) break;
                    visible.add(k);
                    used += w;
                }
                // Seçili sekme görünür kalmalı.
                if (selected != null && !visible.contains(selected)) {
                    int selW = tabs.get(selected).getPreferredSize().width + gap();
                    java.util.List<String> list = new java.util.ArrayList<>(visible);
                    while (!list.isEmpty() && used + selW + moreW > avail) {
                        String last = list.remove(list.size() - 1);
                        used -= tabs.get(last).getPreferredSize().width + gap();
                    }
                    list.add(selected);
                    visible = new java.util.LinkedHashSet<>(list);
                }
            }

            hiddenKeys = new java.util.ArrayList<>();
            int x = 0;
            for (String k : keys) {
                Tab t = tabs.get(k);
                if (visible.contains(k)) {
                    Dimension d = t.getPreferredSize();
                    t.setVisible(true);
                    t.setBounds(x, 0, d.width, h);
                    x += d.width + gap();
                } else {
                    t.setVisible(false);
                    hiddenKeys.add(k);
                }
            }
            if (hiddenKeys.isEmpty()) {
                more.setVisible(false);
            } else {
                more.setCount((long) hiddenKeys.size());
                Dimension d = more.getPreferredSize();
                more.setVisible(true);
                more.setBounds(x, 0, d.width, h);
            }
        }
    }

    public void addView(String key, String label) {
        Tab b = new Tab(label);
        b.addItemListener(e -> {
            if (b.isSelected()) {
                selected = key;
                if (!silent && onChange != null) onChange.accept(key);
            }
        });
        group.add(b);
        tabs.put(key, b);
        add(b);
        revalidate();
        if (tabs.size() == 1) {
            silent = true;
            b.setSelected(true);
            silent = false;
        }
    }

    public boolean isEmpty() {
        return tabs.isEmpty();
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    public String getSelected() {
        return selected;
    }

    /** Sekmeyi seçer; {@code notify} false ise dinleyici tetiklenmez (programatik eşitleme). */
    public void select(String key, boolean notify) {
        Tab b = tabs.get(key);
        if (b == null || b.isSelected()) return;
        silent = !notify;
        b.setSelected(true);
        silent = false;
    }

    /** Sekmenin yanındaki sayıyı günceller; {@code null} sayıyı gizler. */
    public void setCount(String key, Long count) {
        Tab b = tabs.get(key);
        if (b == null) return;
        b.setCount(count);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** Tek sekme: metin + sayı hapı, seçiliyken alt çizgi. Boyut kalın yazıya göre ölçülür (zıplama olmaz). */
    private static final class Tab extends JToggleButton {
        final String label;
        Long count;
        private boolean hover;

        Tab(String label) {
            this.label = label;
            setFocusable(true);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName(label);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void setCount(Long count) {
            if (java.util.Objects.equals(this.count, count)) return;
            this.count = count;
            getAccessibleContext().setAccessibleName(count == null ? label : label + ", " + count + " kayıt");
            revalidate();
            repaint();
        }

        private Font boldFont() {
            return getFont().deriveFont(Font.BOLD);
        }

        private Font countFont() {
            return getFont().deriveFont(Font.PLAIN, getFont().getSize2D() - 1f);
        }

        private int padX() { return UIScale.scale(12); }
        private int pillPadX() { return UIScale.scale(7); }
        private int gap() { return UIScale.scale(8); }

        private int pillWidth(FontMetrics fm) {
            return count == null ? 0 : fm.stringWidth(String.valueOf(count)) + pillPadX() * 2;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(boldFont());
            FontMetrics cfm = getFontMetrics(countFont());
            int w = padX() * 2 + fm.stringWidth(label) + (count == null ? 0 : gap() + Math.max(pillWidth(cfm), UIScale.scale(22)));
            return new Dimension(w, UIScale.scale(38));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color accent = UIManager.getColor("Component.accentColor");
            if (accent == null) accent = UIManager.getColor("Actions.Blue");
            Color fg = UIManager.getColor("Label.foreground");
            Color muted = UIManager.getColor("Label.disabledForeground");
            boolean sel = isSelected();
            int h = getHeight();

            // Hover'da zemin belirir (düz satırlarla aynı dil).
            if (hover && !sel) {
                g2.setColor(withAlpha(fg, 10));
                g2.fillRoundRect(UIScale.scale(2), UIScale.scale(4), getWidth() - UIScale.scale(4), h - UIScale.scale(8),
                        UIScale.scale(8), UIScale.scale(8));
            }

            Font labelFont = sel ? boldFont() : getFont();
            g2.setFont(labelFont);
            FontMetrics fm = g2.getFontMetrics();
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(sel || hover ? fg : muted);
            g2.drawString(label, padX(), textY);

            if (count != null) {
                g2.setFont(countFont());
                FontMetrics cfm = g2.getFontMetrics();
                String txt = String.valueOf(count);
                int pillW = Math.max(pillWidth(cfm), UIScale.scale(22));
                int pillH = cfm.getHeight() + UIScale.scale(2);
                int px = padX() + getFontMetrics(boldFont()).stringWidth(label) + gap();
                int py = (h - pillH) / 2;
                g2.setColor(sel ? withAlpha(accent, FlatLaf.isLafDark() ? 60 : 32) : withAlpha(fg, 16));
                g2.fillRoundRect(px, py, pillW, pillH, pillH, pillH);
                g2.setColor(sel ? (FlatLaf.isLafDark() ? fg : accent) : muted);
                g2.drawString(txt, px + (pillW - cfm.stringWidth(txt)) / 2,
                        py + (pillH - cfm.getHeight()) / 2 + cfm.getAscent());
            }

            if (sel) {
                int t = Math.max(2, UIScale.scale(2));
                g2.setColor(accent);
                g2.fillRoundRect(padX() / 2, h - t, getWidth() - padX(), t, t, t);
            }
            if (isFocusOwner()) {
                g2.setColor(withAlpha(accent, 140));
                g2.setStroke(new BasicStroke(UIScale.scale(1f)));
                g2.drawRoundRect(1, UIScale.scale(4), getWidth() - 3, h - UIScale.scale(8), UIScale.scale(8), UIScale.scale(8));
            }
            g2.dispose();
        }
    }
}
