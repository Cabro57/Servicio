package tr.cabro.servicio.application.renderer.list;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.dictionary.DeviceBrand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class BrandListCellRenderer extends JPanel implements ListCellRenderer<DeviceBrand> {

    private final JLabel  dragHandle = new JLabel("⠿");
    private final JLabel  nameLabel  = new JLabel();
    private final JButton deleteBtn;

    private final Consumer<DeviceBrand> onDelete;
    private DeviceBrand current;
    private int hoveredIndex = -1;

    public BrandListCellRenderer(JList<DeviceBrand> list, Consumer<DeviceBrand> onDelete) {
        this.onDelete = onDelete;

        setLayout(new BorderLayout(8, 0));
        setBorder(new EmptyBorder(4, 10, 4, 10));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        setOpaque(true);

        dragHandle.putClientProperty(FlatClientProperties.STYLE, "font: $semibold.font");
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        dragHandle.setPreferredSize(new Dimension(20, 20));
        dragHandle.setHorizontalAlignment(SwingConstants.CENTER);

        nameLabel.putClientProperty(FlatClientProperties.STYLE, "font: $semibold.font");

        deleteBtn = new JButton();
        deleteBtn.setIcon(new Ikon("icons/trash-2.svg", deleteBtn.getFont().getSize()));
        deleteBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "toolBarButton");

        add(dragHandle, BorderLayout.WEST);
        add(nameLabel,  BorderLayout.CENTER);
        add(deleteBtn,  BorderLayout.EAST);

        installListeners(list);
    }

    private void installListeners(JList<DeviceBrand> list) {
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

                DeviceBrand value = list.getModel().getElementAt(index);
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
                if (hit instanceof JButton) {
                    onDelete.accept(value);
                }
            }
        });
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends DeviceBrand> list,
                                                  DeviceBrand value, int index, boolean selected, boolean cellHasFocus) {

        if (value == null) return this;
        this.current = value;

        nameLabel.setText(value.getName());

        boolean hovered = index == hoveredIndex;
        deleteBtn.setVisible(selected || hovered);

        if (selected) {
            setBackground(list.getSelectionBackground());
            nameLabel.setForeground(list.getSelectionForeground());
            dragHandle.setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            nameLabel.setForeground(list.getForeground());
            dragHandle.setForeground(UIManager.getColor("Label.disabledForeground"));
        }

        return this;
    }
}