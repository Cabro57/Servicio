package tr.cabro.servicio.application.component;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;

/**
 * Uygulama açılış ekranı.
 * <p>
 * Progress bar kullanılmıyor — sadece mesaj metni güncelleniyor.
 * Güncelleme bildirim UI'si buradan kaldırıldı;
 * UpdateChecker non-blocking çalışır, sonuç MainUI tarafından işlenir.
 */
public class AppSplashScreen extends JWindow {

    private final JLabel lblMessage;

    public AppSplashScreen() {
        int windowWidth  = 640;
        int windowHeight = 360;
        int arcSize      = 20;

        URL   imgUrl  = getClass().getResource("/background.png");
        JPanel backgroundPanel = getBackgroundPanel(imgUrl, arcSize);

        // Sol alt köşe — mesaj etiketi
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 24, 14, 0));

        lblMessage = new JLabel("Başlatılıyor...");
        lblMessage.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblMessage.setForeground(Color.WHITE);
        lblMessage.setAlignmentX(Component.LEFT_ALIGNMENT);

        bottomPanel.add(lblMessage);
        backgroundPanel.add(bottomPanel, BorderLayout.SOUTH);

        setBackground(new Color(0, 0, 0, 0));
        getContentPane().setBackground(new Color(0, 0, 0, 0));
        ((JPanel) getContentPane()).setOpaque(false);
        getContentPane().add(backgroundPanel);

        setSize(windowWidth, windowHeight);
        setLocationRelativeTo(null);
        setShape(new RoundRectangle2D.Double(
                0, 0, windowWidth, windowHeight, arcSize, arcSize));
    }

    private JPanel getBackgroundPanel(URL imgUrl, int arcSize) {
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

                g2d.setClip(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), arcSize, arcSize));

                if (bgImage != null) {
                    g2d.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
                } else {
                    g2d.setColor(new Color(38, 40, 42));
                    g2d.fillRoundRect(0, 0, getWidth(), getHeight(), arcSize, arcSize);
                }
                g2d.dispose();
            }
        };

        backgroundPanel.setOpaque(false);
        backgroundPanel.setBorder(null);
        backgroundPanel.setLayout(new BorderLayout());
        return backgroundPanel;
    }

    /**
     * Splash mesajını günceller. Her thread'den güvenle çağrılabilir.
     *
     * @param message Gösterilecek mesaj.
     */
    public void updateMessage(final String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            lblMessage.setText(message);
        } else {
            SwingUtilities.invokeLater(() -> lblMessage.setText(message));
        }
    }
}