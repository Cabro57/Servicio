package tr.cabro.servicio.application.component;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class AppSplashScreen extends JWindow {

    private final JProgressBar progressBar;
    private final JLabel lblMessage;

    public AppSplashScreen() {
        // 1. Hareketli Arka Planı (GIF) Yükleme
        URL gifUrl = getClass().getResource("/background.gif");
        boolean isGifLoaded = (gifUrl != null); // GIF'in yüklenip yüklenmediğini tutan bayrak

        JLabel backgroundLabel;

        if (isGifLoaded) {
            backgroundLabel = new JLabel(new ImageIcon(gifUrl));
        } else {
            backgroundLabel = new JLabel();
            backgroundLabel.setBackground(Color.WHITE);
            backgroundLabel.setOpaque(true);
        }

        backgroundLabel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));

        // 2. MİGLAYOUT AYARLARI
        backgroundLabel.setLayout(new MigLayout(
                "fill, insets 20 40 20 40",
                "[grow, fill]",
                "[grow, center]10[]5[]"
        ));

        // Başlık / Logo
        JLabel lblTitle = new JLabel("SERVICIO", SwingConstants.CENTER);
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 36));
        lblTitle.setForeground(new Color(255, 255, 255));

        // KONTROL: GIF yüklendiyse yazıyı gizle, yüklenemediyse (beyaz ekransa) göster.
        lblTitle.setVisible(!isGifLoaded);

        // Mesaj
        lblMessage = new JLabel("Başlatılıyor...");
        lblMessage.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblMessage.setForeground(Color.WHITE);

        // İlerleme Çubuğu
        progressBar = new JProgressBar(0, 100);
        progressBar.setBorderPainted(false);
        progressBar.setBackground(new Color(230, 230, 230));
        progressBar.setForeground(new Color(0, 120, 215));

        // 3. BİLEŞENLERİ EKLİYORUZ
        backgroundLabel.add(lblTitle, "align center, wrap");
        backgroundLabel.add(lblMessage, "wrap");
        backgroundLabel.add(progressBar, "h 6!");

        getContentPane().add(backgroundLabel);

        pack();
        setSize(640, 360);
        setLocationRelativeTo(null);
    }

    public void updateProgress(int percent, String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percent);
            lblMessage.setText(message);
        });
    }
}