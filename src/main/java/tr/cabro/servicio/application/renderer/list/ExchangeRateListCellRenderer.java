package tr.cabro.servicio.application.renderer.list;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.dictionary.ExchangeRate;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;

public class ExchangeRateListCellRenderer extends JPanel implements ListCellRenderer<ExchangeRate> {

    private static final Map<String, String> ICON_BY_CODE = Map.of(
            "USD", "icons/dollar-sign.svg",
            "EUR", "icons/euro.svg"
    );
    private static final DecimalFormat RATE_FORMAT = new DecimalFormat("#,##0.0000");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final JLabel iconLabel = new JLabel();
    private final JLabel nameLabel = new JLabel();
    private final JLabel rateLabel = new JLabel();
    private final JLabel metaLabel = new JLabel();
    private final JButton editBtn;

    private final Consumer<ExchangeRate> onEdit;
    private int hoveredIndex = -1;

    public ExchangeRateListCellRenderer(JList<ExchangeRate> list, Consumer<ExchangeRate> onEdit) {
        this.onEdit = onEdit;

        setLayout(new BorderLayout(10, 0));
        setBorder(new EmptyBorder(6, 10, 6, 10));
        setOpaque(true);

        iconLabel.setPreferredSize(new Dimension(24, 24));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        nameLabel.putClientProperty(FlatClientProperties.STYLE, "font: $semibold.font");
        metaLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -2");

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(nameLabel);
        textPanel.add(metaLabel);

        rateLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");

        editBtn = new JButton();
        editBtn.setIcon(new Ikon("icons/pen-line.svg", editBtn.getFont().getSize()));
        editBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "toolBarButton");

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(rateLabel);
        right.add(editBtn);

        add(iconLabel, BorderLayout.WEST);
        add(textPanel, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        installListeners(list);
    }

    private void installListeners(JList<ExchangeRate> list) {
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

                ExchangeRate value = list.getModel().getElementAt(index);
                if (value == null) return;

                Point localPoint = new Point(e.getPoint().x - cellBounds.x, e.getPoint().y - cellBounds.y);

                Component renderer = list.getCellRenderer()
                        .getListCellRendererComponent(list, value, index, true, false);
                renderer.setSize(cellBounds.getSize());
                renderer.doLayout();

                Component hit = SwingUtilities.getDeepestComponentAt(renderer, localPoint.x, localPoint.y);
                if (hit == editBtn) {
                    onEdit.accept(value);
                }
            }
        });
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ExchangeRate> list, ExchangeRate value,
                                                    int index, boolean selected, boolean cellHasFocus) {
        if (value == null) return this;

        iconLabel.setIcon(new Ikon(ICON_BY_CODE.getOrDefault(value.getCurrencyCode(), "icons/banknote.svg"), 16));
        nameLabel.setText(value.getCurrencyCode() + " — " + value.getCurrencyName());

        String meta = value.getUpdatedAt() == null
                ? "Henüz güncellenmedi"
                : (value.getSource() != null ? value.getSource() : "Bilinmeyen kaynak") + " · " + value.getUpdatedAt().format(DATE_FORMAT);
        metaLabel.setText(meta);

        rateLabel.setText(value.getRate() != null && value.getRate().signum() > 0
                ? RATE_FORMAT.format(value.getRate()) + " ₺"
                : "Kur tanımlı değil");

        boolean hovered = index == hoveredIndex;
        if (hovered) {
            putClientProperty(FlatClientProperties.STYLE_CLASS, "hovered");
        }

        if (selected) {
            setBackground(list.getSelectionBackground());
            nameLabel.setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            nameLabel.setForeground(list.getForeground());
        }

        return this;
    }
}
