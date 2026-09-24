package tr.cabro.servicio.application.panels.workorder;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * {@code FormWorkOrder} bölünmeden önce {@code styleTable}/{@code createEmptyStatePanel}/
 * {@code createMutedLabel}/{@code createCardPanel} olarak tek sınıfta duran, dört panele
 * ({@link WorkOrderItemsPanel}, {@link WorkOrderPaymentsPanel}, {@link WorkOrderNotesPanel},
 * {@link WorkOrderInfoPanel}) ve orkestratör {@code FormWorkOrder}'a ortak yardımcılar.
 */
public final class WorkOrderPanelSupport {

    private WorkOrderPanelSupport() {
    }

    /** Kart içi gömülü tablolar: liste sayfalarıyla aynı soluk başlık ve çizgi dili, daha alçak satır. */
    public static void styleTable(JTable table) {
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:30; hoverBackground:null; pressedBackground:null; separatorColor:$Table.background;"
                        + " bottomSeparatorColor:$Component.borderColor; background:$Table.background;"
                        + " foreground:$Label.disabledForeground; font:-1");
        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:42; showHorizontalLines:true; gridColor:$Component.borderColor; cellFocusColor:null;"
                        + " selectionBackground:$Servicio.rowSelectedBackground; selectionForeground:$Table.foreground");
    }

    public static JPanel createEmptyStatePanel(String message) {
        JPanel p = new JPanel(new MigLayout("insets 10, fillx", "[grow]", "[]"));
        p.setOpaque(false);
        p.add(createMutedLabel(message), "align center");
        return p;
    }

    public static JLabel createMutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return label;
    }

    /** Detay kartı: liste sayfalarıyla aynı beyaz zemin, ince çizgi, 15px köşe. */
    public static JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        return panel;
    }

    /** Kart başlığı ({@code $h3.font}); ikon kullanılmaz, başlık kendi ağırlığıyla okunur. */
    public static JLabel createTitle(String text) {
        JLabel title = new JLabel(text);
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
        return title;
    }

    /**
     * Boşken soluk ipucu yazan çok satırlı alan. FlatLaf yer tutucusu kaydırma panelindeki
     * JTextArea'da görünmüyordu; ipucu burada metinle aynı kenar boşluğunda elle çizilir.
     */
    public static JTextArea createHintArea(String hint) {
        JTextArea area = new JTextArea() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!getText().isEmpty() || hint == null) return;
                Graphics2D g2 = (Graphics2D) g.create();
                Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
                if (hints instanceof java.util.Map) g2.addRenderingHints((java.util.Map<?, ?>) hints);
                g2.setColor(UIManager.getColor("Label.disabledForeground"));
                g2.setFont(getFont());
                Insets in = getInsets();
                g2.drawString(hint, in.left, in.top + g2.getFontMetrics().getAscent());
                g2.dispose();
            }
        };
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.putClientProperty(FlatClientProperties.STYLE, "border: 8,10,8,10");
        area.getAccessibleContext().setAccessibleDescription(hint);
        return area;
    }

    /** Kart içi alt başlık (soluk, bir adım küçük): "Müşteri şikâyeti", "Notlar" gibi. */
    public static JLabel createCaption(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        return label;
    }

    /**
     * Adım kartı başlığı: numara işareti + başlık, sağda isteğe bağlı bileşen. İş emri sayfası
     * işin sırasını izler (1 arıza/tespit, 2 kalemler, 3 ödeme); işaret adım bitince onay olur.
     */
    public static JPanel createStepHeader(StepMarker marker, String title, JComponent right) {
        JPanel header = new JPanel(new MigLayout("insets 0, fillx, gap 10", "[][grow][]", "[center]"));
        header.setOpaque(false);
        header.add(marker);
        header.add(createTitle(title), "wmin 0");
        if (right != null) header.add(right);
        return header;
    }

    /**
     * Adım numarası: bekleyen adımda ince çerçeveli daire içinde soluk numara, tamamlanan adımda
     * başarı renginde onay. Renk yalnızca "bitti" anlamı taşır.
     */
    public static final class StepMarker extends JLabel {
        private static final int SIZE = 22;
        private final int number;
        private final String doneTip;
        private final String pendingTip;
        private boolean done;

        public StepMarker(int number, String doneTip, String pendingTip) {
            this.number = number;
            this.doneTip = doneTip;
            this.pendingTip = pendingTip;
            setHorizontalAlignment(SwingConstants.CENTER);
            putClientProperty(FlatClientProperties.STYLE, "font: bold -1; foreground: $Label.disabledForeground");
            setDone(false);
        }

        public void setDone(boolean done) {
            this.done = done;
            setText(done ? null : String.valueOf(number));
            setIcon(done ? new tr.cabro.servicio.application.utils.Ikon("icons/circle-check.svg", SIZE, "Servicio.successColor") : null);
            setToolTipText(done ? doneTip : pendingTip);
            getAccessibleContext().setAccessibleName("Adım " + number + (done ? ", tamamlandı" : ", bekliyor"));
            repaint();
        }

        public boolean isDone() {
            return done;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(SIZE, SIZE);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!done) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.setColor(UIManager.getColor("Component.borderColor"));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new java.awt.geom.Ellipse2D.Float(1f, 1f, getWidth() - 2f, getHeight() - 2f));
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
