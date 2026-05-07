package tr.cabro.servicio.application.component;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;

public class AppSplashScreen extends JWindow {

    private final JLabel lblMessage;

    public AppSplashScreen() {
        int windowWidth = 640;
        int windowHeight = 360;
        int arcSize = 20;

        URL imgUrl = getClass().getResource("/background.png");
        Image bgImage = (imgUrl != null) ? new ImageIcon(imgUrl).getImage() : null;

        JPanel backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);

                // Yuvarlak köşeli clip uygula
                g2d.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arcSize, arcSize));

                if (bgImage != null) {
                    g2d.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
                } else {
                    g2d.setColor(Color.WHITE);
                    g2d.fillRoundRect(0, 0, getWidth(), getHeight(), arcSize, arcSize);
                }

                g2d.dispose();
            }
        };

        backgroundPanel.setOpaque(false);
        backgroundPanel.setBorder(null);
        backgroundPanel.setLayout(new BorderLayout());

        // Sol alt köşe için panel
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 24, 10, 0));

        lblMessage = new JLabel("Başlatılıyor...");
        lblMessage.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblMessage.setForeground(Color.WHITE);
        lblMessage.setAlignmentX(Component.LEFT_ALIGNMENT);

        bottomPanel.add(lblMessage);
        backgroundPanel.add(bottomPanel, BorderLayout.SOUTH);

        // JWindow arka planını şeffaf yap
        setBackground(new Color(0, 0, 0, 0));
        getContentPane().setBackground(new Color(0, 0, 0, 0));
        ((JPanel) getContentPane()).setOpaque(false);

        getContentPane().add(backgroundPanel);

        setSize(windowWidth, windowHeight);
        setLocationRelativeTo(null);

        // Pencere şeklini yuvarlak köşeli yap
        setShape(new RoundRectangle2D.Double(0, 0, windowWidth, windowHeight, arcSize, arcSize));
    }

    public void updateProgress(int percent, String message) {
        SwingUtilities.invokeLater(() -> lblMessage.setText(message));
    }
}