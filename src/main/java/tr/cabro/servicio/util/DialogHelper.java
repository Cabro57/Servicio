package tr.cabro.servicio.util;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.component.MessageModal;
import tr.cabro.servicio.application.component.MessageModal.Tone;
import tr.cabro.servicio.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Onay/hata/bilgi diyalogları için merkezi yardımcı. {@link Messages} üzerinden
 * aktif dile göre metin okur — çağıran kod her zaman bir i18n anahtarı verir,
 * ham metin değil. Diyaloglar {@link MessageModal} ("karar kartı", {@code raven.modal}
 * üzerinde) ile gösterilir — proje genelinde {@code JOptionPane} kullanılmıyor.
 * <p>
 * Modal asenkron açıldığı için onay sonucu bir dönüş değeri yerine
 * {@code onConfirm} callback'i ile bildirilir — çağıran kod
 * {@code if (DialogHelper.confirmDelete(...)) { ... }} değil,
 * {@code DialogHelper.confirmDelete(..., () -> { ... })} yazmalıdır.
 * <p>
 * Bu diyaloglar her çağrıda yeniden oluşturulduğu için, dil Ayarlar'dan
 * değiştirildiğinde bir sonraki çağrıda otomatik güncel dilde görünürler.
 */
public final class DialogHelper {

    private DialogHelper() {}

    /** Ortak "Silme Onayı" başlığıyla sorar; tehlike tonunda, birincil eylem "Sil". */
    public static void confirmDelete(Component parent, String messageKey, Runnable onConfirm, Object... args) {
        MessageModal.of(Tone.DANGER, Messages.get("confirm.delete.title"), Messages.get(messageKey, args))
                .primary(Messages.get("dialog.button.delete"), onConfirm)
                .show(parent);
    }

    /** Özel başlıklı bir onay (uyarı tonu); birincil eylem "Devam et". */
    public static void confirm(Component parent, String titleKey, String messageKey, Runnable onConfirm, Object... args) {
        confirm(parent, titleKey, messageKey, "dialog.button.continue", onConfirm, args);
    }

    /** Özel başlıklı ve özel eylem adlı bir onay (ör. "Üzerine yaz", "Yeniden başlat"). */
    public static void confirm(Component parent, String titleKey, String messageKey, String actionKey,
                               Runnable onConfirm, Object... args) {
        MessageModal.of(Tone.WARNING, Messages.get(titleKey), Messages.get(messageKey, args))
                .primary(Messages.get(actionKey), onConfirm)
                .show(parent);
    }

    public static void error(Component parent, String messageKey, Object... args) {
        MessageModal.of(Tone.ERROR, Messages.get("dialog.error.title"), Messages.get(messageKey, args))
                .single()
                .show(parent);
    }

    public static void info(Component parent, String messageKey, Object... args) {
        MessageModal.of(Tone.INFO, Messages.get("dialog.info.title"), Messages.get(messageKey, args))
                .single()
                .show(parent);
    }

    /** Serbest metin girişi için tek alanlı bir modal gösterir; "Tamam" seçilirse girilen metin {@code onConfirm}'e iletilir. */
    public static void prompt(Component parent, String titleKey, String labelKey, String initialValue, Consumer<String> onConfirm, Object... labelArgs) {
        JTextField field = new JTextField(initialValue != null ? initialValue : "");
        field.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 3,8,3,8");
        MessageModal.of(Tone.INPUT, Messages.get(titleKey), Messages.get(labelKey, labelArgs))
                .extra(field)
                .focus(field)
                .primary(Messages.get("dialog.button.ok"), () -> onConfirm.accept(field.getText()))
                .show(parent);
    }
}
