package tr.cabro.servicio.application.listeners;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;

public class InactivityMonitor implements AWTEventListener {

    private final Timer timer;
    private final Action timeoutAction;

    /**
     * @param timeoutAction Süre dolduğunda çalışacak eylem (Örn: Şifre ekranını açma)
     */
    public InactivityMonitor(Action timeoutAction) {
        this.timeoutAction = timeoutAction;

        // Timer'ı oluşturuyoruz. Süre dolduğunda handleTimeout metodunu çağıracak.
        this.timer = new Timer(1000, e -> handleTimeout());
        this.timer.setRepeats(false); // Sadece bir kere tetiklensin, sürekli tekrarlamasın
    }

    public void start() {
        // Dinlemeye başla: Klavye tuşlamaları, fare tıklamaları ve fare hareketleri
        Toolkit.getDefaultToolkit().addAWTEventListener(this,
                AWTEvent.KEY_EVENT_MASK |
                        AWTEvent.MOUSE_EVENT_MASK |
                        AWTEvent.MOUSE_MOTION_EVENT_MASK);

        timer.start();
    }

    public void stop() {
        // Dinlemeyi bırak ve sayacı durdur (Şifre ekranındayken arkada çalışmasına gerek yok)
        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
        timer.stop();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        // Kullanıcı uygulamada herhangi bir şey yaptı! Sayacı hemen sıfırla.
        if (timer.isRunning()) {
            timer.restart();
        }
    }

    private void handleTimeout() {
        stop(); // Yeni eylemleri dinlemeyi durdur
        if (timeoutAction != null) {
            timeoutAction.actionPerformed(null); // Şifre ekranını getir
        }
    }

    public void setTimeout(int minutes) {
        if (minutes <= 0) {
            stop(); // Süre 0 veya altındaysa kilitlemeyi tamamen iptal et
        } else {
            int timeoutMillis = minutes * 60 * 1000;
            timer.setInitialDelay(timeoutMillis);
            timer.setDelay(timeoutMillis);

            if (timer.isRunning()) {
                timer.restart(); // Sayacı yeni süreyle baştan başlat
            } else {
                start(); // Kapalıysa uyandır
            }
        }
    }
}