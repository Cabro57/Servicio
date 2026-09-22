package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Telefon ekran kilidindeki gibi 3x3 desen çizme alanı.
 * <p>
 * Eski ızgara 1-9 numaralı düğmelerden oluşuyordu; müşterinin elinde gördüğü şekille eşleştirmek
 * zordu ve sıra yalnızca alttaki metinden okunabiliyordu. Burada noktalar arasına çizgi çekilir,
 * her noktada kaçıncı sırada seçildiği yazar. Fareyle sürükleyerek ya da tek tek tıklayarak girilir.
 * <p>
 * Düğüm numaraları 1-9 (soldan sağa, yukarıdan aşağıya); değer "1-2-5-9" biçimindedir ve
 * eski kayıtlarla aynıdır.
 */
public class PatternLockPad extends JComponent {

    private static final int CELL = 46;
    private static final int DOT = 14;
    private static final int HIT = 17;

    private final List<Integer> sequence = new ArrayList<>();
    private Point dragPoint;
    private Runnable onChange;
    private int focusedNode = 5;

    public PatternLockPad() {
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Deseni sürükleyerek çizin ya da noktalara sırayla tıklayın");

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                // Sürüklemeye boş bir desenle başlamak telefondaki davranış; tek tıklamalar ise
                // mevcut desene eklenir (sürükleme olmadan bir noktaya basmak = sona ekle).
                int node = nodeAt(e.getPoint());
                if (node > 0) addNode(node);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                dragPoint = e.getPoint();
                int node = nodeAt(e.getPoint());
                if (node > 0) addNode(node);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragPoint = null;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        // Klavye: oklar odaktaki noktayı gezdirir, Boşluk/Enter ekler, Backspace son noktayı siler.
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int row = (focusedNode - 1) / 3, col = (focusedNode - 1) % 3;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> col = Math.max(0, col - 1);
                    case KeyEvent.VK_RIGHT -> col = Math.min(2, col + 1);
                    case KeyEvent.VK_UP -> row = Math.max(0, row - 1);
                    case KeyEvent.VK_DOWN -> row = Math.min(2, row + 1);
                    case KeyEvent.VK_SPACE, KeyEvent.VK_ENTER -> {
                        if (e.getModifiersEx() == 0) { addNode(focusedNode); e.consume(); }
                        return;
                    }
                    case KeyEvent.VK_BACK_SPACE -> { removeLast(); e.consume(); return; }
                    default -> { return; }
                }
                focusedNode = row * 3 + col + 1;
                e.consume();
                repaint();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { repaint(); }
            @Override public void focusLost(FocusEvent e) { repaint(); }
        });
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    private void addNode(int node) {
        if (sequence.contains(node)) return;
        sequence.add(node);
        focusedNode = node;
        fireChange();
    }

    public void removeLast() {
        if (sequence.isEmpty()) return;
        sequence.remove(sequence.size() - 1);
        fireChange();
    }

    private void fireChange() {
        repaint();
        if (onChange != null) onChange.run();
    }

    public String getSequence() {
        StringBuilder sb = new StringBuilder();
        for (int node : sequence) {
            if (sb.length() > 0) sb.append('-');
            sb.append(node);
        }
        return sb.toString();
    }

    public void setSequence(String value) {
        sequence.clear();
        if (value != null && !value.isBlank()) {
            for (String part : value.split("-")) {
                try {
                    int node = Integer.parseInt(part.trim());
                    if (node >= 1 && node <= 9 && !sequence.contains(node)) sequence.add(node);
                } catch (NumberFormatException ignored) {
                    // Bozuk parça yok sayılır; kalan geçerli düğümler gösterilir.
                }
            }
        }
        fireChange();
    }

    public boolean isEmpty() {
        return sequence.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Geometri
    // -------------------------------------------------------------------------

    private Point center(int node) {
        int cell = UIScale.scale(CELL);
        Insets in = getInsets();
        int gridW = cell * 3, gridH = cell * 3;
        int x0 = in.left + (getWidth() - in.left - in.right - gridW) / 2;
        int y0 = in.top + (getHeight() - in.top - in.bottom - gridH) / 2;
        int col = (node - 1) % 3, row = (node - 1) / 3;
        return new Point(x0 + col * cell + cell / 2, y0 + row * cell + cell / 2);
    }

    private int nodeAt(Point p) {
        int hit = UIScale.scale(HIT);
        for (int n = 1; n <= 9; n++) {
            if (center(n).distance(p) <= hit) return n;
        }
        return 0;
    }

    @Override
    public Dimension getPreferredSize() {
        int s = UIScale.scale(CELL) * 3 + UIScale.scale(8);
        return new Dimension(s, s);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    // -------------------------------------------------------------------------
    // Çizim
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            FlatUIUtils.setRenderingHints(g2);
            Color accent = UIManager.getColor("Component.accentColor");
            Color idle = UIManager.getColor("Component.borderColor");
            Color text = UIManager.getColor("Button.default.foreground");
            if (accent == null) accent = new Color(0x2675BF);
            if (idle == null) idle = Color.GRAY;
            if (text == null) text = Color.WHITE;

            int dot = UIScale.scale(DOT);
            int active = UIScale.scale(22);

            // Çizgi: seçilen sırayla, son noktadan imlece kadar (sürüklerken)
            if (!sequence.isEmpty()) {
                Path2D path = new Path2D.Float();
                Point first = center(sequence.get(0));
                path.moveTo(first.x, first.y);
                for (int i = 1; i < sequence.size(); i++) {
                    Point p = center(sequence.get(i));
                    path.lineTo(p.x, p.y);
                }
                if (dragPoint != null) path.lineTo(dragPoint.x, dragPoint.y);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150));
                g2.setStroke(new BasicStroke(UIScale.scale(4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(path);
            }

            Font numFont = getFont() != null ? getFont().deriveFont(Font.BOLD, UIScale.scale(11f)) : null;
            for (int n = 1; n <= 9; n++) {
                Point c = center(n);
                int order = sequence.indexOf(n);
                if (order >= 0) {
                    g2.setColor(accent);
                    g2.fill(new Ellipse2D.Float(c.x - active / 2f, c.y - active / 2f, active, active));
                    // Kaçıncı sırada seçildiği noktanın içinde; başlangıç noktası böylece ayırt edilir.
                    if (numFont != null) {
                        g2.setFont(numFont);
                        g2.setColor(text);
                        String s = String.valueOf(order + 1);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(s, c.x - fm.stringWidth(s) / 2f, c.y + (fm.getAscent() - fm.getDescent()) / 2f);
                    }
                } else {
                    g2.setColor(idle);
                    g2.fill(new Ellipse2D.Float(c.x - dot / 2f, c.y - dot / 2f, dot, dot));
                }
                if (isFocusOwner() && n == focusedNode) {
                    g2.setColor(accent);
                    g2.setStroke(new BasicStroke(UIScale.scale(1.5f)));
                    int r = active + UIScale.scale(8);
                    g2.draw(new Ellipse2D.Float(c.x - r / 2f, c.y - r / 2f, r, r));
                }
            }
        } finally {
            g2.dispose();
        }
    }
}
