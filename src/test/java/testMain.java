import tr.cabro.servicio.application.forms.FormService;
import tr.cabro.servicio.application.forms.example.ServiceRecordsPanel;
import tr.cabro.servicio.application.panels.PinPanel;
import tr.cabro.servicio.application.panels.SetupPanel;
import tr.cabro.servicio.model.Service;

import javax.swing.*;
import java.awt.*;

public class testMain extends JFrame {

    public static void main(String[] args) {
        // Swing arayüz bileşenlerini her zaman Event Dispatch Thread (EDT) üzerinde çalıştırın
        SwingUtilities.invokeLater(() -> {

            // Eğer projenizde FlatLaf kullanıyorsanız (FlatLightLaf veya FlatDarkLaf),
            // görünümün temanızla nasıl durduğunu test etmek için aşağıdaki yorum satırlarını kaldırın:

            try {
                UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarculaLaf());
                // veya UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
            } catch (Exception ex) {
                System.err.println("Tema yüklenemedi: " + ex.getMessage());
            }


            // Ana test çerçevesini (Frame) oluştur
            JFrame frame = new JFrame("Ekran Tasarımları Testi");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 550); // Genişlik ve Yükseklik
            frame.setLocationRelativeTo(null); // Ekranın tam ortasında açılması için

            // Panelleri kolayca inceleyebilmek için sekmeli bir yapı kullanalım
            JTabbedPane tabbedPane = new JTabbedPane();

            // 1. Sekmeye Kurulum Panelini ekle
            tabbedPane.addTab("İlk Kurulum Ekranı", new SetupPanel());

            // 2. Sekmeye PIN Giriş Panelini ekle
            tabbedPane.addTab("PIN Giriş Ekranı", new PinPanel());

            tabbedPane.add("Servis Kayıtları", new ServiceRecordsPanel());

            tabbedPane.add("Servis Dashboard", new FormService(new Service()));
            // Sekmeleri ana frame'e ekle
            frame.add(tabbedPane, BorderLayout.CENTER);

            // Pencereyi görünür yap
            frame.setVisible(true);
        });
    }
}