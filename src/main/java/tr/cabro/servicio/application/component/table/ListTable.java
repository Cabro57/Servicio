package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Liste sayfalarının tablosu. Satırın tamamı hedeftir: fare üstündeki satır hafifçe boyanır,
 * tek tık (ya da Enter) kaydı açar.
 * <p>
 * Satır işlemleri (detay/düzenle/sil, fiş, sat…) ayrı bir kolonda durmaz: {@link #setRowActions}
 * ile verilen düğmeler fare üstündeki (yoksa seçili) satırın sağ ucunda, satırın ÜSTÜNE binen
 * küçük bir şeritte görünür. Eskiden bu düğmeler için her satırda ~100px'lik bir kolon ayrılıyor,
 * fare satırda değilken boş duruyor ve dar ekranda asıl bilgiden (cihaz, şikâyet) yer çalıyordu.
 * İşlem kolonu modelde kalır (kolon indeksleri değişmesin diye) ama genişliği sıfırda tutulur.
 * <p>
 * Hover ve seçim zemini renderer'lardan bağımsız olarak {@link #prepareRenderer} içinde uygulanır;
 * formlara özel her renderer'a ayrı ayrı dokunmaya gerek kalmaz.
 */
public class ListTable extends JTable {

    /** Yüzen şeritteki bir düğme; tıklanınca görünüm (view) satır indeksini alır. */
    public static final class RowAction {
        final String iconPath;
        /**
         * Üstüne gelindiğindeki ikon rengi (tema anahtarı). Dinlenmede ikon her zaman soluk yazı rengindedir;
         * yalnızca anlamı olan eylemler renk alır: sil (danger), para girişi (success). Diğerleri (göz,
         * kalem, fiş…) hover'da da nötr kalır — renk yalnızca anlam taşır.
         */
        final String colorKey;
        final String tooltip;
        final IntConsumer onRow;

        public RowAction(String iconPath, String colorKey, String tooltip, IntConsumer onRow) {
            this.iconPath = iconPath;
            this.colorKey = colorKey;
            this.tooltip = tooltip;
            this.onRow = onRow;
        }

        /** Anlamsal renk verilirse karşılık gelen tema anahtarına çevrilir; tanınmazsa normal yazı rengi. */
        public RowAction(String iconPath, Color semantic, String tooltip, IntConsumer onRow) {
            this(iconPath, keyFor(semantic), tooltip, onRow);
        }

        private static String keyFor(Color c) {
            if (c == null) return "Label.foreground";
            if (c.equals(SemanticColor.success())) return "Servicio.successColor";
            if (c.equals(SemanticColor.danger())) return "Servicio.dangerColor";
            if (c.equals(SemanticColor.warning())) return "Servicio.warningColor";
            if (c.equals(SemanticColor.info())) return "Servicio.infoColor";
            if (c.equals(SemanticColor.action())) return "Servicio.actionColor";
            return "Label.foreground";
        }

        /** Hover rengi: yalnızca danger/success korunur, geri kalanı nötr. */
        String hoverKey() {
            return "Servicio.dangerColor".equals(colorKey) || "Servicio.successColor".equals(colorKey)
                    ? colorKey : "Label.foreground";
        }
    }

    private int hoverRow = -1;
    private IntConsumer rowOpener;
    /** Satır tıklamasının kaydı açmadığı kolon (işlem düğmeleri kendi işini yapar). */
    private int actionColumn = -1;

    private final ActionStrip strip = new ActionStrip();
    /** Şeridin şu an bağlı olduğu satır (hover, yoksa seçim). */
    private int stripRow = -1;

    public ListTable() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHoverRow(rowAtPoint(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Fare şeride geçtiyse satır hâlâ "üstünde" sayılır; yoksa şerit kaybolup titrerdi.
                Point p = e.getPoint();
                if (strip.isVisible() && strip.getBounds().contains(p)) return;
                setHoverRow(-1);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (rowOpener == null || !SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1) return;
                int row = rowAtPoint(e.getPoint());
                int column = columnAtPoint(e.getPoint());
                if (row < 0 || column == actionColumn) return;
                openRow(row);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        setLayout(null);
        strip.setVisible(false);
        add(strip);
        getSelectionModel().addListSelectionListener(e -> updateStrip());

        String actionKey = "servicio.openSelectedRow";
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), actionKey);
        getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int row = getSelectedRow();
                if (row >= 0) openRow(row);
            }
        });
    }

    /** Satırı açan eylem; görünüm (view) satır indeksini alır. */
    public void setRowOpener(IntConsumer opener, int actionColumn) {
        this.rowOpener = opener;
        if (actionColumn >= 0) this.actionColumn = actionColumn;
        setCursor(opener != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }

    public boolean hasRowOpener() {
        return rowOpener != null;
    }

    /**
     * Satır işlemlerini yüzen şeride bağlar ve {@code hiddenColumn} işlem kolonunu sıfır genişlikte
     * tutar (formlar sonradan genişlik verse bile geri sıfırlanır).
     */
    public void setRowActions(int hiddenColumn, List<RowAction> actions) {
        this.actionColumn = hiddenColumn;
        strip.setActions(actions);
        if (hiddenColumn >= 0 && hiddenColumn < getColumnModel().getColumnCount()) {
            TableColumn col = getColumnModel().getColumn(hiddenColumn);
            collapse(col);
            PropertyChangeListener keepCollapsed = e -> {
                String name = e.getPropertyName();
                if (("maxWidth".equals(name) || "minWidth".equals(name) || "preferredWidth".equals(name))
                        && (col.getMaxWidth() != 0 || col.getMinWidth() != 0 || col.getPreferredWidth() != 0)) {
                    SwingUtilities.invokeLater(() -> collapse(col));
                }
            };
            col.addPropertyChangeListener(keepCollapsed);
        }
    }

    private static void collapse(TableColumn col) {
        col.setMinWidth(0);
        col.setPreferredWidth(0);
        col.setMaxWidth(0);
        col.setResizable(false);
        col.setHeaderValue("");
    }

    private void openRow(int row) {
        if (isEditing()) getCellEditor().cancelCellEditing();
        rowOpener.accept(row);
    }

    private void setHoverRow(int row) {
        if (row == hoverRow) return;
        int old = hoverRow;
        hoverRow = row;
        repaintRow(old);
        repaintRow(row);
        updateStrip();
    }

    private void repaintRow(int row) {
        if (row < 0 || row >= getRowCount()) return;
        Rectangle r = getCellRect(row, 0, true);
        r.width = getWidth();
        repaint(r);
    }

    /** İşlem düğmeleri bu satırda görünmeli mi? (fare üstünde ya da seçili) */
    public boolean isRowActive(int row) {
        return row == hoverRow || isRowSelected(row);
    }

    /** Şeridi fare üstündeki (yoksa seçili) satırın sağ ucuna, görünür alanın içine yerleştirir. */
    private void updateStrip() {
        if (strip == null || !strip.hasActions()) return;
        int row = hoverRow >= 0 ? hoverRow : getSelectedRow();
        if (row < 0 || row >= getRowCount()) {
            stripRow = -1;
            strip.setVisible(false);
            return;
        }
        stripRow = row;
        Rectangle cell = getCellRect(row, 0, true);
        Rectangle visible = getVisibleRect();
        Dimension size = strip.getPreferredSize();
        int pad = UIScale.scale(8);
        int x = visible.x + visible.width - size.width - pad;
        int y = cell.y + (cell.height - size.height) / 2;
        strip.setBackground(isRowSelected(row) ? UIManager.getColor("Servicio.rowSelectedBackground")
                : UIManager.getColor("Servicio.rowHoverBackground"));
        strip.setBounds(x, y, size.width, size.height);
        strip.setVisible(true);
        strip.repaint();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        updateStrip();
    }

    @Override
    public void tableChanged(javax.swing.event.TableModelEvent e) {
        super.tableChanged(e);
        if (strip != null) {
            hoverRow = -1;
            SwingUtilities.invokeLater(this::updateStrip);
        }
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        // Zemin her satırda açıkça atanır: DefaultTableCellRenderer.setBackground rengi kalıcı
        // saklar, yalnızca hover satırında atansaydı renk sonraki satırlara sızardı.
        Color bg = null;
        if (isRowSelected(row)) bg = UIManager.getColor("Servicio.rowSelectedBackground");
        else if (row == hoverRow) bg = UIManager.getColor("Servicio.rowHoverBackground");
        if (bg == null) bg = getBackground();
        c.setBackground(bg);
        if (c instanceof JComponent) ((JComponent) c).setOpaque(true);
        return c;
    }

    /**
     * Satırın sağ ucuna binen düğme şeridi. Zemini satırın hover/seçim tonudur; sol kenarındaki
     * kısa geçiş, altındaki metnin keskin bir blokla kesilmiş gibi görünmesini engeller.
     */
    private final class ActionStrip extends JPanel {
        private List<RowAction> actions = List.of();

        ActionStrip() {
            super(new MigLayout("insets 2 14 2 4, gap 2", "", "[center]"));
            setOpaque(false);
        }

        boolean hasActions() {
            return !actions.isEmpty();
        }

        void setActions(List<RowAction> list) {
            this.actions = list;
            removeAll();
            for (RowAction a : list) {
                // Dinlenmede soluk nötr ikon; üstüne gelince tam yazı rengi (sil/para girişi anlam rengi).
                JButton b = new JButton(new Ikon(a.iconPath, 0.8f, "Label.foreground", 0.6f));
                b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
                b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,4,4,4; focusWidth: 0;"
                        + " toolbar.hoverBackground: fade($Label.foreground,10%);"
                        + " toolbar.pressedBackground: fade($Label.foreground,18%)");
                b.addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) {
                        b.setIcon(new Ikon(a.iconPath, 0.8f, a.hoverKey(), 1f));
                    }
                    @Override public void mouseExited(MouseEvent e) {
                        b.setIcon(new Ikon(a.iconPath, 0.8f, "Label.foreground", 0.6f));
                    }
                });
                b.setFocusable(false);
                b.setToolTipText(a.tooltip);
                b.getAccessibleContext().setAccessibleName(a.tooltip);
                b.addActionListener(e -> {
                    if (stripRow >= 0) a.onRow.accept(stripRow);
                });
                b.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseExited(MouseEvent e) {
                        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), ListTable.this);
                        if (!strip.getBounds().contains(p)) setHoverRow(rowAtPoint(p));
                    }
                });
                add(b, "w 30!, h 30!");
            }
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = getBackground();
            int fade = UIScale.scale(12);
            Color clear = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 0);
            g2.setPaint(new GradientPaint(0, 0, clear, fade, 0, bg));
            g2.fillRect(0, 0, fade, getHeight());
            g2.setColor(bg);
            g2.fillRect(fade, 0, getWidth() - fade, getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
