import com.formdev.flatlaf.FlatDarkLaf;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class ProfileCardDemo {

    public static void main(String[] args) {
        FlatDarkLaf.setup();

        JFrame frame = new JFrame("Profil Kartı (FlatLaf Renkleri)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        ProfileCardPanel cardPanel = new ProfileCardPanel();
        frame.add(cardPanel);

        // Örnek kullanım: Verileri dışarıdan verme
        Map<String, String> data = new HashMap<>();
        data.put("name", "Brooklyn Simmons");
        data.put("email", "brooklynsim2@gmail.com");
        data.put("address", "4140 Parker Rd. Allentown, New Mexico 31134");
        data.put("phone", "+222 01 414 8447");
        data.put("transaction", "12 December, 2023");

        // Verileri metoda göndererek kartı doldur
        cardPanel.updateCardData(data);

        frame.setSize(380, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * Tüm profil kartını tutan ana panel.
     */
    static class ProfileCardPanel extends JPanel {

        // FlatLaf Renkleri ve Bileşenleri
        private final Color darkBg = UIManager.getColor("Panel.background");
        private final Color buttonBg = UIManager.getColor("Button.background");
        private final Color infoLabelColor = UIManager.getColor("Label.disabledForeground"); // Açıklama metinleri için
        private final Color greenColor = new Color(50, 215, 75); // Sadece "Active" butonu için yeşil renk sabit kaldı

        // Başlıkta kullanılan gradient için FlatLaf vurgu rengini taklit et
        private final Color flatAccent = UIManager.getColor("Component.accentColor");
        private final Color gradientStart = (flatAccent != null) ? flatAccent.brighter().brighter() : new Color(190, 100, 255);
        private final Color gradientEnd = (flatAccent != null) ? flatAccent.darker().darker() : new Color(100, 150, 255);

        private final int headerHeight = 100;
        private final int imageSize = 80;

        // Veri tutan JLabel'lar (Dışarıdan erişim için)
        private JLabel nameLabel;
        private final Map<String, JLabel> valueLabels = new HashMap<>();


        public ProfileCardPanel() {
            // Düzen ve boşluk ayarları aynı kalır
            setLayout(new MigLayout("wrap 1, fillx, insets 160 25 25 25", "[fill]", ""));
            setBackground(darkBg);

            initComponents();
        }

        /**
         * Karttaki tüm veri alanlarını dışarıdan gelen Map ile günceller.
         * @param data Güncellenecek verileri içeren Map.
         */
        public void updateCardData(Map<String, String> data) {
            // İsim güncelleme
            if (data.containsKey("name") && nameLabel != null) {
                nameLabel.setText(data.get("name"));
            }

            // Diğer bilgi alanlarını güncelleme
            valueLabels.forEach((key, label) -> {
                if (data.containsKey(key)) {
                    // Adres alanı için HTML kontrolü
                    String value = key.equals("address") ?
                            "<html>" + data.get("address") + "</html>" :
                            data.get("value");
                    label.setText(data.get(key));
                }
            });
            revalidate();
            repaint();
        }

        private void initComponents() {
            // 1. Profil Resmi (Konum aynı kalır)
            JLabel profilePicLabel = new JLabel(createPlaceholderIcon(imageSize));
            add(profilePicLabel, "pos (container.w/2 - comp.w/2) 50, w " + imageSize + "!, h " + imageSize + "!");

            // 2. Üst Kısım (İsim, Durum, Butonlar)
            add(createTopBlock(), "growx, wrap");

            // 3. Bilgi Alanları (Value Label'ları Map'e kaydet)
            add(createInfoPanel("Email address", "email", ""), "growx, wrap");
            add(createInfoPanel("Address", "address", ""), "growx, wrap");
            add(createInfoPanel("Phone number", "phone", ""), "growx, wrap");
            add(createInfoPanel("Last transaction", "transaction", ""), "growx, wrap");
        }

        // ... Diğer yardımcı metotlar ...

        private JPanel createTopBlock() {
            JPanel topBlock = new JPanel(new MigLayout("fillx, insets 0", "[grow][right]"));
            topBlock.setOpaque(false);

            JPanel nameStatusPanel = new JPanel(new MigLayout("wrap 1, insets 0"));
            nameStatusPanel.setOpaque(false);

            // İsim Label'ı başlatılıyor ve class değişkenine atanıyor
            nameLabel = new JLabel("");
            nameLabel.putClientProperty("FlatLaf.style", "font: bold +4");
            nameStatusPanel.add(nameLabel);

            JButton statusButton = new JButton("Active");
            statusButton.putClientProperty("FlatLaf.style",
                    "background: $" + Integer.toHexString(greenColor.getRGB()).substring(2) + ";" +
                            "foreground: #FFFFFF;" + "arc: 999;" + "font: bold;" + "borderWidth: 0;" + "focusWidth: 0;" + "innerFocusWidth: 0;"
            );
            nameStatusPanel.add(statusButton, "gapy 5");

            JPanel iconPanel = new JPanel(new MigLayout("insets 0", "[]5[]"));
            iconPanel.setOpaque(false);

            JButton callButton = createIconButton("📞");
            JButton msgButton = createIconButton("💬");

            iconPanel.add(callButton);
            iconPanel.add(msgButton);

            topBlock.add(nameStatusPanel, "growx");
            topBlock.add(iconPanel, "aligny top");

            return topBlock;
        }

        /**
         * Etiket ve değer içeren bilgi panelleri oluşturur ve Value Label'ı kaydeder.
         * @param labelText Açıklama metni (Email address).
         * @param key Map'teki anahtar (email).
         * @param initialValue Başlangıç değeri.
         */
        private JPanel createInfoPanel(String labelText, String key, String initialValue) {
            JPanel panel = new JPanel(new MigLayout("wrap 1, insets 0 0 15 0"));
            panel.setOpaque(false);

            JLabel labelLabel = new JLabel(labelText);
            labelLabel.setForeground(infoLabelColor);
            labelLabel.putClientProperty("FlatLaf.style", "font: -2");
            panel.add(labelLabel);

            JLabel valueLabel = new JLabel(initialValue);
            valueLabel.putClientProperty("FlatLaf.style", "font: +1");
            panel.add(valueLabel, "gapy 2");

            // Değer JLabel'ını Map'e kaydet
            valueLabels.put(key, valueLabel);

            return panel;
        }

        private JButton createIconButton(String text) {
            JButton button = new JButton(text);
            button.putClientProperty("FlatLaf.style",
                    "background: $" + Integer.toHexString(buttonBg.getRGB()).substring(2) + ";" +
                            "arc: 10;" + "font: +4;" + "minimumWidth: 36;" + "minimumHeight: 36;" + "borderWidth: 0;" + "focusWidth: 0;" + "innerFocusWidth: 0;"
            );
            return button;
        }

        private Icon createPlaceholderIcon(int size) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Dış daireyi FlatLaf'ın foreground rengiyle çiz
            g2.setColor(UIManager.getColor("Label.foreground"));
            g2.fillOval(0, 0, size, size);

            g2.setColor(darkBg);
            g2.setStroke(new BasicStroke(4));
            g2.drawOval(2, 2, size - 4, size - 4);

            g2.dispose();
            return new ImageIcon(image);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Gradient (FlatLaf vurgu rengi ile)
            GradientPaint gradient = new GradientPaint(
                    0, 0, gradientStart,
                    getWidth(), 0, gradientEnd
            );
            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, getWidth(), headerHeight);

            // Alt Düz Koyu Renk
            g2d.setColor(darkBg);
            g2d.fillRect(0, headerHeight, getWidth(), getHeight() - headerHeight);
        }
    }
}