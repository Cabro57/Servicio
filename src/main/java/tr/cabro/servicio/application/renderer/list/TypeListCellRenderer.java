package tr.cabro.servicio.application.renderer.list;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.dictionary.DeviceType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class TypeListCellRenderer extends JPanel implements ListCellRenderer<DeviceType> {

    private final JLabel  iconLabel  = new JLabel();
    private final JLabel  nameLabel  = new JLabel();
    private final JLabel  badgeLabel = new JLabel();
    private final JButton deleteBtn;

    private final Consumer<DeviceType> onDelete;
    private DeviceType current;
    private int hoveredIndex = -1;

    public TypeListCellRenderer(JList<DeviceType> list, Consumer<DeviceType> onDelete) {
        this.onDelete = onDelete;

        setLayout(new BorderLayout(8, 0));
        setBorder(new EmptyBorder(4, 10, 4, 10));
        setOpaque(true);

        iconLabel.setPreferredSize(new Dimension(20, 20));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        nameLabel.putClientProperty(FlatClientProperties.STYLE, "font: $semibold.font");

        badgeLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        badgeLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        badgeLabel.setOpaque(false);

        deleteBtn = new JButton();
        deleteBtn.setIcon(new Ikon("icons/trash-2.svg", deleteBtn.getFont().getSize()));
        deleteBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "toolBarButton");

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(badgeLabel);
        right.add(deleteBtn);

        add(iconLabel, BorderLayout.WEST);
        add(nameLabel, BorderLayout.CENTER);
        add(right,     BorderLayout.EAST);

        installListeners(list);
    }

    private void installListeners(JList<DeviceType> list) {
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

                DeviceType value = list.getModel().getElementAt(index);
                if (value == null) return;

                // Tıklanan noktayı hücrenin yerel koordinatına çevir
                Point localPoint = new Point(
                        e.getPoint().x - cellBounds.x,
                        e.getPoint().y - cellBounds.y
                );

                // Renderer'ı geçici olarak boyutlandır ve layout yap
                Component renderer = list.getCellRenderer()
                        .getListCellRendererComponent(list, value, index, true, false);
                renderer.setSize(cellBounds.getSize());
                renderer.doLayout();

                // Tıklanan bileşen delete butonu mu?
                Component hit = SwingUtilities.getDeepestComponentAt(renderer, localPoint.x, localPoint.y);
                if (hit instanceof JButton) {
                    onDelete.accept(value);
                }
            }
        });
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends DeviceType> list,
                                                  DeviceType value, int index, boolean selected, boolean cellHasFocus) {

        if (value == null) return this;
        this.current = value;

        nameLabel.setText(value.getName());
        badgeLabel.setText(String.valueOf(value.getBrandCount()));

        boolean hovered = index == hoveredIndex;

        if (hovered) {
            putClientProperty(FlatClientProperties.STYLE_CLASS, "hovered");
            repaint();
        }


        // Seçim tablolardaki seçim tonuyla; yazı rengi değişmez, sayı soluk metin (rozet değil).
        setBackground(selected ? UIManager.getColor("Servicio.rowSelectedBackground") : list.getBackground());
        nameLabel.setForeground(list.getForeground());
        badgeLabel.setOpaque(false);
        badgeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        badgeLabel.setText(value.getBrandCount() == null || value.getBrandCount() == 0 ? "marka yok" : value.getBrandCount() + " marka");

        iconLabel.setText("");
        iconLabel.setVisible(false);
        return this;
    }
}