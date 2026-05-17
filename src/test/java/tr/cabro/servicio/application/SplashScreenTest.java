package tr.cabro.servicio.application;

import tr.cabro.servicio.application.component.AppSplashScreen;
import javax.swing.*;

public class SplashScreenTest {

    public static void main(String[] args) {
        // Swing arayüz bileşenlerini güvenli bir şekilde başlatmak için invokeLater kullanıyoruz
        SwingUtilities.invokeLater(() -> {
            AppSplashScreen splashScreen = new AppSplashScreen();
            splashScreen.setVisible(true);

            // Arka planda çalışacak yükleme simülasyonu
            new Thread(() -> {
                try {
                    // Sahte yükleme aşamaları
                    String[] loadingSteps = {
                            "Sistem yapılandırması okunuyor...",
                            "Veritabanı bağlantısı kuruluyor...",
                            "Modüller belleğe yükleniyor...",
                            "Kullanıcı arayüzü hazırlanıyor...",
                            "Son kontroller yapılıyor..."
                    };

                    for (String step : loadingSteps) {
                        // Splash screen'i güncelle (Java 8 lambda'ları sayesinde oldukça temiz)
                        splashScreen.updateMessage(step);

                        // Yükleme süresini simüle etmek için beklet (örneğin her %1 için 40ms)
                        Thread.sleep(40);
                    }

                    // Yükleme bittiğinde Splash ekranını kapat ve ana uygulamayı aç
                    SwingUtilities.invokeLater(() -> {
                        splashScreen.dispose(); // Splash ekranını bellekten sil

                        // Örnek bir Ana Ekran gösterimi
                        JOptionPane.showMessageDialog(null,
                                "Servicio uygulaması başarıyla başlatıldı!",
                                "Ana Ekran",
                                JOptionPane.INFORMATION_MESSAGE);

                        System.exit(0);
                    });

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }
}