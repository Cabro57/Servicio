package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Popups;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.PhoneHelper.CountryCode;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;

/**
 * Telefon alanının ülke listesi: ortak açılır menü kabuğunda (bkz. DESIGN.md "Popup Menus") üstte
 * ortak arama alanı ({@link Inputs#search}), altında ~250 ülke. İşletmenin ülkesi en üstte ve altında ince bir çizgiyle
 * ayrılır; kalanlar alfabe sırasında. Ad, ISO kodu ya da alan koduyla aranır ("alm", "de", "49").
 * <p>
 * Odak arama satırındadır: ↑/↓ ve PgUp/PgDn gezer, Enter seçer, Esc kapatır. Fare satırın üzerine
 * gelince seçim onu izler; tıklamak seçer. Kapanınca odak telefon alanına döner.
 */
final class PhoneCountryPicker {

    private static final int VISIBLE_ROWS = 8;
    private static final int ROW_HEIGHT = 34;
    /** Alan çok darsa listenin inebileceği en dar genişlik; normalde liste alanla birebir aynı genişliktedir. */
    private static final int MIN_WIDTH = 220;

    private final JPopupMenu popup = new JPopupMenu();
    private final JTextField search = Inputs.search("Ülke ya da alan kodu ara");
    private final DefaultListModel<CountryCode> model = new DefaultListModel<>();
    private final JList<CountryCode> list = new JList<>(model);
    private final JLabel empty = new JLabel("Eşleşen ülke yok");
    private final JScrollPane scroll;
    private final String current;
    private final String home = PhoneHelper.homeRegion();
    private final Consumer<CountryCode> onPick;
    private boolean picked;

    private PhoneCountryPicker(String current, Consumer<CountryCode> onPick) {
        this.current = current;
        this.onPick = onPick;

        list.setCellRenderer(new Row());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setFixedCellHeight(UIScale.scale(ROW_HEIGHT));
        list.setVisibleRowCount(VISIBLE_ROWS);
        list.setBackground(UIManager.getColor("Table.background"));

        scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setFocusable(false);
        scroll.getVerticalScrollBar().setUnitIncrement(UIScale.scale(ROW_HEIGHT));

        empty.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        empty.setVisible(false);

        // Arama satırı uygulamadaki her arama alanıyla aynı (çerçeveli, 10px köşe); liste altında.
        JPanel content = new JPanel(new MigLayout("insets 2 2 0 2, gap 0, hidemode 3", "[grow, fill]", "[]6[]"));
        content.add(search, "wrap");
        content.add(scroll, "wrap");
        content.add(empty, "h " + UIScale.scale(ROW_HEIGHT * 2) + "!");
        popup.setLayout(new BorderLayout());
        popup.add(content);
        Popups.blend(content);

        wireKeys();
        wireMouse();
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        filter();
    }

    /**
     * Listeyi {@code field}'in altında, sol kenarına hizalı açar; seçim {@code onPick}'e gider,
     * liste hangi yolla kapanırsa kapansın {@code onClose} çalışır ve odak alana döner.
     */
    static void show(JComponent field, String currentRegion, Consumer<CountryCode> onPick, Runnable onClose) {
        PhoneCountryPicker picker = new PhoneCountryPicker(currentRegion, onPick);
        // Açılır menü kendi çerçevesini ve iç boşluğunu ekler; liste o pay düşülerek boyutlanır ki
        // menünün dış kenarı alanın kenarlarıyla çakışsın.
        Insets frame = picker.popup.getInsets();
        int width = Math.max(field.getWidth(), UIScale.scale(MIN_WIDTH)) - frame.left - frame.right - UIScale.scale(4);
        picker.scroll.setPreferredSize(new Dimension(width, UIScale.scale(ROW_HEIGHT * VISIBLE_ROWS)));
        picker.popup.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                onClose.run();
                field.requestFocusInWindow();
            }
            @Override public void popupMenuCanceled(PopupMenuEvent e) { }
        });
        Popups.showBelow(picker.popup, field);
        SwingUtilities.invokeLater(picker.search::requestFocusInWindow);
    }

    private void filter() {
        String query = search.getText();
        List<CountryCode> all = PhoneHelper.getSupportedCountries();
        model.clear();
        model.addAll(all.stream().filter(c -> c.matches(query)).toList());

        boolean any = !model.isEmpty();
        scroll.setVisible(any);
        empty.setVisible(!any);
        if (any) {
            int idx = query.isBlank() ? indexOf(current) : 0;
            select(Math.max(idx, 0), query.isBlank());
        }
        popup.revalidate();
    }

    private int indexOf(String region) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).getRegionCode().equals(region)) return i;
        }
        return -1;
    }

    /** Seçimi taşır; {@code center} ise satırı listenin ortasına getirir (açılışta seçili ülke). */
    private void select(int index, boolean center) {
        if (index < 0 || index >= model.size()) return;
        list.setSelectedIndex(index);
        if (!center) {
            list.ensureIndexIsVisible(index);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Rectangle cell = list.getCellBounds(index, index);
            if (cell == null) return;
            int viewport = scroll.getViewport().getExtentSize().height;
            cell.y = Math.max(0, cell.y - (viewport - cell.height) / 2);
            cell.height = viewport;
            list.scrollRectToVisible(cell);
        });
    }

    private void pick(CountryCode country) {
        if (country == null || picked) return;
        picked = true;
        popup.setVisible(false);
        onPick.accept(country);
    }

    private void wireKeys() {
        InputMap im = search.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = search.getActionMap();
        bind(im, am, KeyEvent.VK_DOWN, "next", () -> select(list.getSelectedIndex() + 1, false));
        bind(im, am, KeyEvent.VK_UP, "prev", () -> select(list.getSelectedIndex() - 1, false));
        bind(im, am, KeyEvent.VK_PAGE_DOWN, "pageDown",
                () -> select(Math.min(model.size() - 1, list.getSelectedIndex() + VISIBLE_ROWS - 1), false));
        bind(im, am, KeyEvent.VK_PAGE_UP, "pageUp",
                () -> select(Math.max(0, list.getSelectedIndex() - VISIBLE_ROWS + 1), false));
        bind(im, am, KeyEvent.VK_ENTER, "pick", () -> pick(list.getSelectedValue()));
        bind(im, am, KeyEvent.VK_ESCAPE, "close", () -> popup.setVisible(false));
    }

    private static void bind(InputMap im, ActionMap am, int key, String name, Runnable action) {
        im.put(KeyStroke.getKeyStroke(key, 0), name);
        am.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void wireMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && i != list.getSelectedIndex()) list.setSelectedIndex(i);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && SwingUtilities.isLeftMouseButton(e)) pick(model.get(i));
            }
        };
        list.addMouseListener(mouse);
        list.addMouseMotionListener(mouse);
        list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Satır: bayrak, ad, sağda soluk alan kodu. Klavye/fare seçimi açılır menülerin nötr seçim tonu,
     * 8px köşe. O an seçili ülkenin adı kalın. Arama boşken işletmenin ülkesinin altına ince çizgi.
     */
    private final class Row extends JPanel implements ListCellRenderer<CountryCode> {
        private final JLabel flag = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel code = new JLabel();
        private final Font plain;
        private final Font bold;
        private boolean highlighted;
        private boolean dividerBelow;

        Row() {
            super(new MigLayout("insets 0 12 0 12, gap 10, aligny center", "[][grow, fill][]", "[center]"));
            setOpaque(false);
            code.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
            plain = name.getFont();
            bold = plain.deriveFont(Font.BOLD);
            add(flag);
            add(name, "wmin 0");
            add(code);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CountryCode> l, CountryCode c, int index,
                                                      boolean selected, boolean focus) {
            flag.setIcon(CountryFlagIcon.of(c.getFlagPath(), 20));
            name.setText(c.getName());
            name.setFont(c.getRegionCode().equals(current) ? bold : plain);
            name.setForeground(UIManager.getColor("Label.foreground"));
            code.setText("+" + c.getPhoneCode());
            highlighted = selected;
            dividerBelow = index == 0 && c.getRegionCode().equals(home) && search.getText().isBlank();
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int inset = UIScale.scale(2);
                if (highlighted) {
                    float arc = UIScale.scale(8f);
                    g2.setColor(UIManager.getColor("Servicio.menuSelectionBackground"));
                    g2.fill(new RoundRectangle2D.Float(0, inset, getWidth(), getHeight() - 2 * inset - (dividerBelow ? inset : 0), arc, arc));
                }
                if (dividerBelow) {
                    g2.setColor(UIManager.getColor("Component.borderColor"));
                    int y = getHeight() - UIScale.scale(1);
                    g2.fillRect(UIScale.scale(12), y, getWidth() - UIScale.scale(24), UIScale.scale(1));
                }
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
