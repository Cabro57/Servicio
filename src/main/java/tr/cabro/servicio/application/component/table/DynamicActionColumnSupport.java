package tr.cabro.servicio.application.component.table;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.ActionButton;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tabloda "kaç buton, hangi ikon, hangi kod" tamamen çağıran tarafın belirlediği genel bir
 * aksiyon-kolonu kurulumu. {@link TableActionColumnSupport}'un sabit view/edit/delete üçlüsünden
 * farklı olarak buton listesi {@link ButtonSpec} ile serbestçe tanımlanır — ör. bir kolonda tek
 * "sil" butonu, başka bir kolonda tek "fiş yazdır" butonu, ikisi de aynı mekanizmayla kurulur.
 */
public final class DynamicActionColumnSupport {

    private DynamicActionColumnSupport() {
    }

    public static <T> void install(JTable table, int columnIndex, GenericTableModel<T> tableModel, List<ButtonSpec<T>> buttons) {
        table.getColumnModel().getColumn(columnIndex).setCellRenderer(new BarRenderer<>(buttons));
        table.getColumnModel().getColumn(columnIndex).setCellEditor(new BarEditor<>(tableModel, buttons));
    }

    public static <T> ButtonSpec<T> button(String iconPath, Color hoverColor, String tooltip, Consumer<T> onClick) {
        return new ButtonSpec<>(iconPath, hoverColor, tooltip, onClick);
    }

    public static class ButtonSpec<T> {
        private final String iconPath;
        private final Color hoverColor;
        private final String tooltip;
        private final Consumer<T> onClick;

        private ButtonSpec(String iconPath, Color hoverColor, String tooltip, Consumer<T> onClick) {
            this.iconPath = iconPath;
            this.hoverColor = hoverColor;
            this.tooltip = tooltip;
            this.onClick = onClick;
        }
    }

    private static String columnTemplate(int count) {
        return "[grow, center]".repeat(count);
    }

    private static <T> ActionButton createButton(ButtonSpec<T> spec) {
        ActionButton btn = new ActionButton(new Ikon(spec.iconPath, 0.7f), spec.hoverColor);
        if (spec.tooltip != null) btn.setToolTipText(spec.tooltip);
        return btn;
    }

    private static class BarRenderer<T> extends DefaultTableCellRenderer {
        private final JPanel panel;

        BarRenderer(List<ButtonSpec<T>> buttons) {
            panel = new JPanel(new MigLayout("insets 0, fill", columnTemplate(buttons.size()), "[center]"));
            panel.setFocusable(false);
            for (ButtonSpec<T> spec : buttons) {
                panel.add(createButton(spec));
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component com = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setVerticalAlignment(SwingConstants.CENTER);
            setFocusable(false);
            panel.setBackground(com.getBackground());
            return panel;
        }
    }

    private static class BarEditor<T> extends DefaultCellEditor {
        private final GenericTableModel<T> tableModel;
        private final List<ButtonSpec<T>> buttons;

        BarEditor(GenericTableModel<T> tableModel, List<ButtonSpec<T>> buttons) {
            super(new JCheckBox());
            this.tableModel = tableModel;
            this.buttons = buttons;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            Component com = super.getTableCellEditorComponent(table, value, isSelected, row, column);

            JPanel panel = new JPanel(new MigLayout("insets 0, fill", columnTemplate(buttons.size()), "[center]"));
            panel.setFocusable(false);
            for (ButtonSpec<T> spec : buttons) {
                ActionButton btn = createButton(spec);
                btn.addActionListener(e -> {
                    if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                    int modelRow = table.convertRowIndexToModel(row);
                    T item = tableModel.getItemAt(modelRow);
                    spec.onClick.accept(item);
                });
                panel.add(btn);
            }
            panel.setBackground(com.getBackground());
            return panel;
        }
    }
}
