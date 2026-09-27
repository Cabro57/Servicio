package tr.cabro.servicio.application.utils;

import raven.modal.Toast;
import raven.modal.toast.option.ToastLocation;
import tr.cabro.servicio.util.SoundPlayer;

import java.awt.Component;

/**
 * {@link Toast#show} yerine kullanılır: uyarı ve hata bildirimlerine ses ekler (Ayarlar &gt; Ses ile
 * açılıp kapanır); başarı bildirimi sessizdir, ilgili eylemler kendi sesini çalar. Görsel davranış
 * ham {@code Toast.show} ile aynıdır.
 */
public final class Toasts {

    private Toasts() {}

    public static String show(Component parent, Toast.Type type, String message) {
        if (type == Toast.Type.ERROR) SoundPlayer.error();
        else if (type == Toast.Type.WARNING) SoundPlayer.warning();
        return Toast.show(parent, type, message);
    }

    /**
     * Kayıt tamamlandı bildirimi: başarı sesiyle birlikte ekranın üst ortasında çıkar. Operatörün
     * gözü formdayken kaydın gerçekten yazıldığını hem duyması hem görmesi gereken işler için
     * (ör. servis kaydı).
     */
    public static String saved(Component parent, String message) {
        SoundPlayer.success();
        return Toast.show(parent, Toast.Type.SUCCESS, message, ToastLocation.TOP_CENTER);
    }

    public static String show(Component parent, Toast.Type type, String message, raven.modal.toast.option.ToastOption option) {
        if (type == Toast.Type.ERROR) SoundPlayer.error();
        else if (type == Toast.Type.WARNING) SoundPlayer.warning();
        return Toast.show(parent, type, message, option);
    }
}
