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

    public static void styleTable(JTable table) {
        table.setRowHeight(38);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setReorderingAllowed(false);
        table.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 2%); selectionBackground: lighten($Panel.background, 5%)");
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

    public static JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); arc: 15;");
        return panel;
    }
}
