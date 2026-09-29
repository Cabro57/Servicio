package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Tek satırlık çip dizisi: başta isteğe bağlı bir başlık, ardından sığdığı kadar çip. Sığmayanlar
 * alt satıra kaymaz, satır sonundaki "Diğer" düğmesinin açılır menüsüne girer (liste sayfalarındaki
 * {@link tr.cabro.servicio.application.component.table.ViewTabs} ile aynı davranış).
 */
public class ChipRow extends JPanel {

    private record Chip(JButton button, String label, Runnable action) {
    }

    private final JComponent leading;
    private final List<Chip> chips = new ArrayList<>();
    private final JButton more;
    private final List<Chip> hidden = new ArrayList<>();

    /** @param leading satır başındaki sabit öğe (grup başlığı); {@code null} olabilir */
    public ChipRow(JComponent leading) {
        super(null);
        setLayout(new OverflowLayout());
        setOpaque(false);
        this.leading = leading;
        if (leading != null) add(leading);

        more = chipButton("Diğer");
        more.setIcon(new Ikon("icons/chevron-down.svg", 12));
        more.setHorizontalTextPosition(SwingConstants.LEADING);
        more.setVisible(false);
        more.addActionListener(e -> showOverflowMenu());
        add(more);
    }

    public void addChip(String label, String tooltip, Runnable action) {
        JButton button = chipButton(label);
        button.setToolTipText(tooltip);
        button.addActionListener(e -> action.run());
        chips.add(new Chip(button, label, action));
        add(button);
        revalidate();
    }

    private static JButton chipButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 2,10,2,10; font: -1; iconTextGap: 4");
        b.setFocusable(false);
        return b;
    }

    private void showOverflowMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (Chip chip : hidden) {
            JMenuItem item = new JMenuItem(chip.label());
            item.setToolTipText(chip.button().getToolTipText());
            item.addActionListener(e -> chip.action().run());
            menu.add(item);
        }
        menu.show(more, 0, more.getHeight());
    }

    /** Öğeleri soldan dizer; sığmayan çipleri gizleyip "Diğer" menüsüne bırakır. */
    private final class OverflowLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component comp) {}
        @Override public void removeLayoutComponent(Component comp) {}

        private int gap() { return UIScale.scale(6); }

        private int leadingWidth() {
            return leading != null ? leading.getPreferredSize().width + gap() : 0;
        }

        private int rowHeight() {
            int h = more.getPreferredSize().height;
            if (leading != null) h = Math.max(h, leading.getPreferredSize().height);
            for (Chip chip : chips) h = Math.max(h, chip.button().getPreferredSize().height);
            return h;
        }

        /**
         * Tercih edilen genişlik bilerek küçük tutulur (başlık + "Diğer"): satır, kapsayıcı kolonun
         * verdiği genişliği kullanır; tüm çiplerin toplamını isteseydi ayarlar penceresini genişletirdi.
         */
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            return new Dimension(leadingWidth() + more.getPreferredSize().width + in.left + in.right,
                    rowHeight() + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int avail = parent.getWidth() - in.left - in.right;
            int h = rowHeight();
            int x = in.left;

            if (leading != null) {
                Dimension d = leading.getPreferredSize();
                leading.setBounds(x, in.top + (h - d.height) / 2, d.width, d.height);
                x += d.width + gap();
            }

            int total = x - in.left;
            for (Chip chip : chips) total += chip.button().getPreferredSize().width + gap();
            boolean overflow = total - gap() > avail;
            int limit = in.left + avail - (overflow ? more.getPreferredSize().width + gap() : 0);

            hidden.clear();
            for (Chip chip : chips) {
                JButton b = chip.button();
                Dimension d = b.getPreferredSize();
                if (hidden.isEmpty() && x + d.width <= limit) {
                    b.setVisible(true);
                    b.setBounds(x, in.top + (h - d.height) / 2, d.width, d.height);
                    x += d.width + gap();
                } else {
                    // Sıra korunur: bir çip sığmadıysa arkasındakiler de menüye gider.
                    b.setVisible(false);
                    hidden.add(chip);
                }
            }

            more.setVisible(!hidden.isEmpty());
            if (!hidden.isEmpty()) {
                Dimension d = more.getPreferredSize();
                more.setBounds(x, in.top + (h - d.height) / 2, d.width, d.height);
            }
        }
    }
}
