package tr.cabro.servicio.application.util;

import raven.modal.toast.ToastPromise;
import raven.modal.toast.option.ToastLocation;
import raven.modal.toast.option.ToastOption;

import java.awt.Component;

public class Toast {

    // Kütüphane Type'ını static import gibi kullanmak için alias
    public static final class Type {
        public static final raven.modal.Toast.Type DEFAULT = raven.modal.Toast.Type.DEFAULT;
        public static final raven.modal.Toast.Type SUCCESS = raven.modal.Toast.Type.SUCCESS;
        public static final raven.modal.Toast.Type INFO    = raven.modal.Toast.Type.INFO;
        public static final raven.modal.Toast.Type WARNING = raven.modal.Toast.Type.WARNING;
        public static final raven.modal.Toast.Type ERROR   = raven.modal.Toast.Type.ERROR;
    }

    public static String show(Component owner, raven.modal.Toast.Type type, String message) {
        SoundPlayer.getInstance().play(type);
        return raven.modal.Toast.show(owner, type, message);
    }

    public static String show(Component owner, raven.modal.Toast.Type type, String message, ToastLocation location) {
        SoundPlayer.getInstance().play(type);
        return raven.modal.Toast.show(owner, type, message, location);
    }

    public static String show(Component owner, raven.modal.Toast.Type type, String message, ToastOption option) {
        SoundPlayer.getInstance().play(type);
        return raven.modal.Toast.show(owner, type, message, option);
    }

    public static String showPromise(Component owner, String message, ToastPromise promise) {
        SoundPlayer.getInstance().play(raven.modal.Toast.Type.INFO);
        return raven.modal.Toast.showPromise(owner, message, promise);
    }

    public static String showPromise(Component owner, String message, ToastLocation location, ToastPromise promise) {
        SoundPlayer.getInstance().play(raven.modal.Toast.Type.INFO);
        return raven.modal.Toast.showPromise(owner, message, location, promise);
    }

    public static void close(String id)               { raven.modal.Toast.close(id); }
    public static void closeAll()                      { raven.modal.Toast.closeAll(); }
    public static boolean isToastAvailable(String id)  { return raven.modal.Toast.isToastAvailable(id); }
    public static void setSoundEnabled(boolean enabled){ SoundPlayer.getInstance().setEnabled(enabled); }
}