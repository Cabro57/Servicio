package tr.cabro.servicio.application;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.AppSplashScreen;
import tr.cabro.servicio.application.themes.LafService;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.util.AppLock;
import tr.cabro.servicio.util.DataDirResolver;

import javax.swing.*;
import java.io.File;

/**
 * Uygulama başlatma akışını yöneten bootstrap sınıfı.
 * <p>
 * Servicio.main() bu sınıfı başlatır; karmaşık başlatma mantığı buraya taşındı.
 * <p>
 * Akış:
 *   1. AppLock kontrolü
 *   2. Splash ekranını göster (EDT)
 *   3. Arka planda Servicio'yu başlat (non-EDT)
 *      a. Servicio constructor → init işlemleri + güncelleme kontrolü (non-blocking)
 *      b. Servicio.run() → UI kurulumu (EDT'ye geçer)
 *   4. UI açılınca güncel güncelleme kontrolü sonucunu işle
 */
public final class ApplicationBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ApplicationBootstrap.class);

    private ApplicationBootstrap() {}

    /**
     * Uygulamayı başlatır. Servicio.main() buradan çağırır.
     */
    public static void launch() {
        // 1. Tek örnek kontrolü
        // NOT: Burada ve aşağıdaki hata durumunda JOptionPane kasıtlı olarak kullanılıyor —
        // raven.modal.ModalDialog bir RootPaneContainer (görünür pencere) gerektirir, ama bu iki
        // hata henüz hiçbir pencere kurulmadan (null parent ile) oluşuyor. Projenin geri kalanı
        // raven.modal kullanıyor, bu ikisi tek istisna.
        if (!AppLock.acquireLock()) {
            JOptionPane.showMessageDialog(null,
                    Messages.get("app.already.running"),
                    Messages.get("app.already.running.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2. Splash'i hemen göster (EDT)
        final AppSplashScreen splash = new AppSplashScreen();
        SwingUtilities.invokeLater(() -> splash.setVisible(true));

        // 3. Ağır işlemleri arka plana at
        Thread startupThread = new Thread(() -> {
            try {
                Servicio app = new Servicio(resolveBaseFolder(), splash);
                app.run(splash);
            } catch (Exception e) {
                log.error("Başlatma sırasında kritik hata!", e);
                JOptionPane.showMessageDialog(null,
                        Messages.get("app.startup.failed", e.getMessage()),
                        Messages.get("app.startup.failed.title"), JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        }, "servicio-startup");
        startupThread.setDaemon(false); // Ana thread olarak çalışsın
        startupThread.start();
    }

    /**
     * Veri klasörünün ({@code .servicio/}) oluşturulacağı kök dizini döner.
     * <p>
     * Normalde {@link Launcher#main} bunu çok daha erken (logback konfigüre olmadan önce)
     * {@link DataDirResolver} ile hesaplayıp {@code servicio.baseDir} sistem özelliğine yazar —
     * burada aynı değeri okuyup tekrar kullanıyoruz (logback'in kullandığı yolla tutarlı kalması için).
     * Özellik yoksa (ör. IDE'den doğrudan {@code Servicio.main} çalıştırılırsa) yeniden hesaplanır.
     */
    private static File resolveBaseFolder() {
        String resolved = System.getProperty("servicio.baseDir");
        return (resolved != null) ? new File(resolved) : DataDirResolver.resolveBaseFolder();
    }

    /**
     * FlatLaf ve font kurulumu — EDT'de çağrılmalıdır.
     * Servicio.run() içinden çağrılır.
     */
    public static void setupLookAndFeel() {
        FlatRobotoFont.install();
        FlatLaf.registerCustomDefaultsSource("themes");
        FlatLaf.setPreferredFontFamily(FlatRobotoFont.FAMILY);
        FlatLaf.setPreferredLightFontFamily(FlatRobotoFont.FAMILY_LIGHT);
        FlatLaf.setPreferredSemiboldFontFamily(FlatRobotoFont.FAMILY_SEMIBOLD);
        LafService.setup();
    }
}