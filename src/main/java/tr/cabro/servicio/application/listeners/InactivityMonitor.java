package tr.cabro.servicio.application.listeners;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.util.concurrent.TimeUnit;

/**
 * Kullanıcı belirli bir süre hiçbir şey yapmazsa kilit eylemini çalıştırır.
 * <p>
 * Her fare/klavye olayında yalnızca son etkinlik zamanı yazılır; saniyede bir çalışan tek bir Timer
 * sürenin dolup dolmadığına bakar. Olay başına {@code Timer.restart()} çağırmak fare hareketinde
 * saniyede yüzlerce kez TimerQueue kilidi alıp kuyruğu yeniden sıralıyordu.
 */
public class InactivityMonitor implements AWTEventListener {

    private static final int CHECK_MS = 1000;

    private final Timer ticker;
    private final Action timeoutAction;

    /** 0: otomatik kilit kapalı. */
    private long timeoutNanos;
    private volatile long lastActivity = System.nanoTime();
    private boolean active;

    /**
     * @param timeoutAction Süre dolduğunda çalışacak eylem (Örn: Şifre ekranını açma)
     */
    public InactivityMonitor(Action timeoutAction) {
        this.timeoutAction = timeoutAction;
        this.ticker = new Timer(CHECK_MS, e -> check());
    }

    public void start() {
        // Kilit kapalıyken dinlemeye gerek yok; eskiden burada Timer kurucudaki 1 sn ile başlıyordu.
        if (timeoutNanos <= 0) return;
        lastActivity = System.nanoTime();
        if (!active) {
            // Dinlemeye başla: Klavye tuşlamaları, fare tıklamaları ve fare hareketleri
            Toolkit.getDefaultToolkit().addAWTEventListener(this,
                    AWTEvent.KEY_EVENT_MASK |
                            AWTEvent.MOUSE_EVENT_MASK |
                            AWTEvent.MOUSE_MOTION_EVENT_MASK);
            active = true;
        }
        ticker.start();
    }

    public void stop() {
        // Dinlemeyi bırak ve sayacı durdur (Şifre ekranındayken arkada çalışmasına gerek yok)
        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
        active = false;
        ticker.stop();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        lastActivity = System.nanoTime();
    }

    private void check() {
        if (timeoutNanos > 0 && System.nanoTime() - lastActivity >= timeoutNanos) {
            handleTimeout();
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
            timeoutNanos = 0;
            stop(); // Süre 0 veya altındaysa kilitlemeyi tamamen iptal et
        } else {
            timeoutNanos = TimeUnit.MINUTES.toNanos(minutes);
            if (active) {
                lastActivity = System.nanoTime(); // Sayacı yeni süreyle baştan başlat
            } else {
                start(); // Kapalıysa uyandır
            }
        }
    }
}
