package tr.cabro.servicio.util;

import raven.modal.ModalDialog;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Onay/hata/bilgi diyalogları için merkezi yardımcı. {@link Messages} üzerinden
 * aktif dile göre metin okur — çağıran kod her zaman bir i18n anahtarı verir,
 * ham metin değil. Diyaloglar {@code raven.modal} (bkz. {@link SimpleMessageModal},
 * {@code application/forms/FormSuppliers.java} gibi mevcut kullanım örnekleri) ile
 * gösterilir — proje genelinde {@code JOptionPane} artık kullanılmıyor.
 * <p>
 * {@code ModalDialog.showModal(...)} asenkron çalıştığı için (EDT'ye
 * {@code invokeLater} ile atılır), onay sonucu bir dönüş değeri yerine
 * {@code onConfirm} callback'i ile bildirilir — çağıran kod eskisi gibi
 * {@code if (DialogHelper.confirmDelete(...)) { ... }} değil,
 * {@code DialogHelper.confirmDelete(..., () -> { ... })} yazmalıdır.
 * <p>
 * Bu diyaloglar her çağrıda yeniden oluşturulduğu için, dil Ayarlar'dan
 * değiştirildiğinde bir sonraki çağrıda otomatik güncel dilde görünürler.
 */
public final class DialogHelper {

    private DialogHelper() {}

    /** Ortak "Silme Onayı" başlığıyla evet/hayır sorar; "Evet" seçilirse {@code onConfirm} çalışır. */
    public static void confirmDelete(Component parent, String messageKey, Runnable onConfirm, Object... args) {
        confirm(parent, "confirm.delete.title", messageKey, onConfirm, args);
    }

    /** Özel başlıklı bir onay diyaloğu (ör. çıkış onayı); "Evet" seçilirse {@code onConfirm} çalışır. */
    public static void confirm(Component parent, String titleKey, String messageKey, Runnable onConfirm, Object... args) {
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option(Messages.get("dialog.button.yes"), SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option(Messages.get("dialog.button.no"), SimpleModalBorder.NO_OPTION)
        };
        ModalDialog.showModal(parent, new SimpleMessageModal(SimpleMessageModal.Type.WARNING,
                messageComponent(Messages.get(messageKey, args)), Messages.get(titleKey), options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.YES_OPTION) onConfirm.run();
                }));
    }

    public static void error(Component parent, String messageKey, Object... args) {
        showMessage(parent, SimpleMessageModal.Type.ERROR, Messages.get("dialog.error.title"), messageKey, args);
    }

    public static void info(Component parent, String messageKey, Object... args) {
        showMessage(parent, SimpleMessageModal.Type.INFO, Messages.get("dialog.info.title"), messageKey, args);
    }

    /** Serbest metin girişi için tek alanlı bir modal gösterir; "Tamam" seçilirse girilen metin {@code onConfirm}'e iletilir. */
    public static void prompt(Component parent, String titleKey, String labelKey, String initialValue, Consumer<String> onConfirm, Object... labelArgs) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        panel.setOpaque(false);
        panel.add(new JLabel(Messages.get(labelKey, labelArgs)), BorderLayout.NORTH);
        JTextField field = new JTextField(initialValue != null ? initialValue : "");
        panel.add(field, BorderLayout.CENTER);

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option(Messages.get("dialog.button.ok"), SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option(Messages.get("dialog.button.cancel"), SimpleModalBorder.CANCEL_OPTION)
        };
        ModalDialog.showModal(parent, new SimpleMessageModal(SimpleMessageModal.Type.DEFAULT,
                panel, Messages.get(titleKey), options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OK_OPTION) onConfirm.accept(field.getText());
                }));
    }

    private static void showMessage(Component parent, SimpleMessageModal.Type type, String title, String messageKey, Object... args) {
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option(Messages.get("dialog.button.close"), SimpleModalBorder.CLOSE_OPTION)
        };
        ModalDialog.showModal(parent, new SimpleMessageModal(type,
                messageComponent(Messages.get(messageKey, args)), title, options, null));
    }

    private static JComponent messageComponent(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        return area;
    }
}
