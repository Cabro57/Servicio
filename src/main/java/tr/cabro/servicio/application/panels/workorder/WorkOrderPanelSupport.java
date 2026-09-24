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
}
