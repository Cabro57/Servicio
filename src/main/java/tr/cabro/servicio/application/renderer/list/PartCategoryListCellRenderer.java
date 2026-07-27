package tr.cabro.servicio.application.renderer.list;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.dictionary.PartCategory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class PartCategoryListCellRenderer extends JPanel implements ListCellRenderer<PartCategory> {

    private final JLabel  iconLabel  = new JLabel();
    private final JLabel  nameLabel  = new JLabel();
    private final JLabel  badgeLabel = new JLabel();
    private final JButton editBtn;
    private final JButton deleteBtn;

    private final Consumer<PartCategory> onEdit;
    private final Consumer<PartCategory> onDelete;
    private int hoveredIndex = -1;

    public PartCategoryListCellRenderer(JList<PartCategory> list, Consumer<PartCategory> onEdit, Consumer<PartCategory> onDelete) {
        this.onEdit = onEdit;
        this.onDelete = onDelete;

        setLayout(new BorderLayout(8, 0));
        setBorder(new EmptyBorder(4, 10, 4, 10));
        setOpaque(true);

        iconLabel.setPreferredSize(new Dimension(20, 20));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        nameLabel.putClientProperty(FlatClientProperties.STYLE, "font: $semibold.font");

        badgeLabel.setFont(badgeLabel.getFont().deriveFont(Font.BOLD, 11f));
        badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        badgeLabel.setPreferredSize(new Dimension(26, 20));
        badgeLabel.setOpaque(true);

        editBtn = new JButton();
        editBtn.setIcon(new Ikon("icons/pen-line.svg", editBtn.getFont().getSize()));
        editBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "toolBarButton");

        deleteBtn = new JButton();
        deleteBtn.setIcon(new Ikon("icons/trash-2.svg", deleteBtn.getFont().getSize()));
        deleteBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "toolBarButton");

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(badgeLabel);
        right.add(editBtn);
        right.add(deleteBtn);

        add(iconLabel, BorderLayout.WEST);
        add(nameLabel, BorderLayout.CENTER);
        add(right,     BorderLayout.EAST);

        installListeners(list);
    }

    private void installListeners(JList<PartCategory> list) {
        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoveredIndex = list.locationToIndex(e.getPoint());
                list.repaint();
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index < 0) return;

                Rectangle cellBounds = list.getCellBounds(index, index);
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) return;

                PartCategory value = list.getModel().getElementAt(index);
                if (value == null) return;

                Point localPoint = new Point(
                        e.getPoint().x - cellBounds.x,
                        e.getPoint().y - cellBounds.y
                );

                Component renderer = list.getCellRenderer()
                        .getListCellRendererComponent(list, value, index, true, false);
                renderer.setSize(cellBounds.getSize());
                renderer.doLayout();

                Component hit = SwingUtilities.getDeepestComponentAt(renderer, localPoint.x, localPoint.y);
                if (hit == editBtn) {
                    onEdit.accept(value);
                } else if (hit == deleteBtn) {
                    onDelete.accept(value);
                }
            }
        });
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends PartCategory> list,
                                                    PartCategory value, int index, boolean selected, boolean cellHasFocus) {

        if (value == null) return this;

        nameLabel.setText(value.getName());
        badgeLabel.setText(String.valueOf(value.getPartCount()));

        boolean hovered = index == hoveredIndex;

        if (hovered) {
            putClientProperty(FlatClientProperties.STYLE_CLASS, "hovered");
            repaint();
        }

        if (selected) {
            setBackground(list.getSelectionBackground());
            nameLabel.setForeground(list.getSelectionForeground());
            iconLabel.setForeground(list.getSelectionForeground());
            badgeLabel.setBackground(UIManager.getColor("Component.accentColor") != null
                    ? UIManager.getColor("Component.accentColor").darker() : new Color(0x1e3a6e));
            badgeLabel.setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            nameLabel.setForeground(list.getForeground());
            iconLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            badgeLabel.setBackground(UIManager.getColor("Button.background") != null
                    ? UIManager.getColor("Button.background") : new Color(0x2a3450));
            badgeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        }

        iconLabel.setText("■");
        return this;
    }
}
