package tr.cabro.servicio.application.compenents;

import javax.swing.*;
import java.awt.*;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;

public class InfoBox extends JPanel {

    private final JLabel titleLabel;

    private JPanel content;
    private JLabel amountLabel;
    private JProgressBar progress;
    private JLabel progressDescLabel;

    public InfoBox(String iconPath, Color color) {
        setLayout(new MigLayout("insets 0", "[]0[]", "[]"));
        setBackground(color);

        // Icon
        JLabel iconLabel = new JLabel(new FlatSVGIcon(iconPath, 64, 64));
        iconLabel.setOpaque(true);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setBackground(darkenColor(color, 0.15f)); // panel renginden biraz daha koyu
        add(iconLabel, "w 100!, h 100!");

        content = new JPanel(new MigLayout("insets 0", "[grow, fill]", "[]2[]2[]2[]"));
        content.setBackground(null);

        // Title
        titleLabel = new JLabel("Title");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, UIScale.scale(16)));
        content.add(titleLabel, "gapleft 10, wrap");

        // Amount
        amountLabel = new JLabel("0 Adet");
        amountLabel.setFont(amountLabel.getFont().deriveFont(UIScale.scale(14f)));
        content.add(amountLabel, "gapleft 10, wrap");

        // Progress bar
        progress = new JProgressBar(0, 100);
        progress.setBackground(darkenColor(color, 0.15f));
        progress.setForeground(Color.WHITE);
        progress.setStringPainted(false);
        progress.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc: 0;" +
                "verticalSize: 1, 3;");
        content.add(progress, "growx, push, wrap");

        // Progress description
        progressDescLabel = new JLabel("0% tamamlandı");
        progressDescLabel.setFont(progressDescLabel.getFont().deriveFont(UIScale.scale(12f)));
        content.add(progressDescLabel, "gapleft 10, wrap");

        add(content, "h 100!, grow, push");
    }

    public void setContent(Content content, Value value) {
        int percent = value.MAX > 0 ? (int) ((value.CURRENT * 100.0f) / value.MAX) : 0;

        titleLabel.setText(replacePlaceholders(content.TITLE, value.CURRENT, percent));
        amountLabel.setText(replacePlaceholders(content.AMOUNT_TEXT, value.CURRENT, percent));
        progress.setValue(percent);
        progressDescLabel.setText(replacePlaceholders(content.DESCRIPTION, value.CURRENT, percent));
    }

    private String replacePlaceholders(String text, int amount, int percent) {
        if (text == null) return "";
        return text
                .replace("{AMOUNT}", String.valueOf(amount))
                .replace("{PERCENT}", String.valueOf(percent));
    }

    private Color darkenColor(Color color, float factor) {
        int r = Math.max((int) (color.getRed() * (1 - factor)), 0);
        int g = Math.max((int) (color.getGreen() * (1 - factor)), 0);
        int b = Math.max((int) (color.getBlue() * (1 - factor)), 0);
        return new Color(r, g, b);
    }

    public void updateContent(Content content, Value value) {
        int percent = value.MAX > 0 ? (int) ((value.CURRENT * 100.0f) / value.MAX) : 0;
        String amountText = value.CURRENT + "/" + value.MAX;

        // Placeholder değiştirme
        String titleText = replacePlaceholders(content.TITLE, percent, amountText);
        String amountStr = replacePlaceholders(content.AMOUNT_TEXT, percent, amountText);
        String descText = replacePlaceholders(content.DESCRIPTION, percent, amountText);

        titleLabel.setText(titleText);
        amountLabel.setText(amountStr);
        progress.setValue(percent);
        progressDescLabel.setText(descText);
    }

    private String replacePlaceholders(String text, int percent, String amount) {
        if (text == null) return "";
        return text
                .replace("{percent}", percent + "%")
                .replace("{amount}", amount);
    }

    // İçerik yapısı
    public static class Content {
        public String TITLE;
        public String AMOUNT_TEXT;
        public String DESCRIPTION;

        public Content(String title, String amountText, String description) {
            this.TITLE = title;
            this.AMOUNT_TEXT = amountText;
            this.DESCRIPTION = description;
        }
    }

    // Değer yapısı
    public static class Value {
        public int MAX;
        public int CURRENT;

        public Value(int max, int current) {
            this.MAX = max;
            this.CURRENT = current;
        }
    }
}
