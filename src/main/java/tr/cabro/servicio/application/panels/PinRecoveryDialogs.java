package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.panels.setting.SettingsDialog;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.UserService;
import tr.cabro.servicio.service.exception.ValidationException;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletionException;

/**
 * PIN kurtarma pencereleri.
 * <ul>
 *   <li>{@link #openForgot}: kilit ekranındaki "PIN'imi unuttum". Veri klasöründe sıfırlama dosyası
 *       varsa kodsuz, yoksa kurtarma koduyla yeni PIN belirlenir.</li>
 *   <li>{@link #showNewCode}: yeni kurtarma kodu üretip bir kez gösterir (kurulum ve Ayarlar &gt; Güvenlik).</li>
 * </ul>
 */
public final class PinRecoveryDialogs {

    private PinRecoveryDialogs() {}

    public static void openForgot(Component parent, Runnable onReset) {
        boolean byFile = UserService.isResetFilePresent();
        SettingsDialog d = new SettingsDialog("PIN'imi unuttum", 460);
        JTextField codeField = new JTextField();
        JLabel codeError = FormKit.errorLabel();
        JPasswordField pin1 = pinField();
        JPasswordField pin2 = pinField();
        JLabel pinError = FormKit.errorLabel();

        if (byFile) {
            d.lead("Veri klasöründe sıfırlama dosyası bulundu. Yeni PIN belirleyin; dosya kullanıldıktan sonra silinir.");
        } else {
            d.lead("Kurulumda ya da Ayarlar > Güvenlik'te aldığınız kurtarma kodunu girin. "
                    + "Kod da yoksa destek ile görüşün.");
            codeField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "XXXX-XXXX-XXXX");
            d.body().add(FormKit.cell("Kurtarma kodu", codeField, codeError), "span 2");
        }
        d.body().add(FormKit.cell("Yeni PIN (6 hane)", pin1, null));
        d.body().add(FormKit.cell("Yeni PIN tekrar", pin2, pinError));

        d.primary("PIN'i değiştir", false, () -> {
            FormKit.clear(codeField, codeError);
            FormKit.clear(pin1, pinError);
            String p1 = new String(pin1.getPassword()), p2 = new String(pin2.getPassword());
            if (!p1.matches("\\d{6}")) { FormKit.fail(pin1, pinError, "PIN 6 haneli rakam olmalı."); return; }
            if (!p1.equals(p2)) { FormKit.fail(pin2, pinError, "İki PIN aynı değil."); return; }
            if (!byFile && codeField.getText().replaceAll("[^A-Za-z0-9]", "").length() != 12) {
                FormKit.fail(codeField, codeError, "Kod 12 karakter olmalı."); return;
            }
            d.busy();
            UserService users = ServiceManager.getUserService();
            (byFile ? users.resetPinWithFile(p1) : users.resetPinWithRecoveryCode(codeField.getText(), p1))
                    .whenComplete((ok, ex) -> SwingUtilities.invokeLater(() -> {
                        d.idle();
                        if (ex != null) {
                            Throwable root = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                            if (root instanceof ValidationException) FormKit.fail(codeField, codeError, root.getMessage());
                            else ErrorHandler.handle(d, "PIN değiştirilemedi", root);
                            return;
                        }
                        if (!Boolean.TRUE.equals(ok)) {
                            FormKit.fail(codeField, codeError, byFile ? "Sıfırlama dosyası bulunamadı." : "Kurtarma kodu yanlış.");
                            return;
                        }
                        d.close();
                        Toasts.show(parent, Toast.Type.SUCCESS, "PIN değiştirildi. Yeni PIN ile giriş yapın.");
                        if (onReset != null) onReset.run();
                    }));
        });
        d.show(parent, byFile ? pin1 : codeField);
    }

    /** Yeni kod üretir ve gösterir; eski kod geçersiz olur. */
    public static void showNewCode(Component parent, Long userId, Runnable onDone) {
        ServiceManager.getUserService().generateRecoveryCode(userId).whenComplete((code, ex) -> SwingUtilities.invokeLater(() -> {
            if (ex != null) {
                ErrorHandler.handle(parent, "Kurtarma kodu oluşturulamadı", ex);
                return;
            }
            SettingsDialog d = new SettingsDialog("Kurtarma kodu", 460);
            d.lead("PIN'inizi unutursanız kilit ekranında \"PIN'imi unuttum\" ile bu kodu girip yeni PIN belirlersiniz. "
                    + "Kod yalnızca şimdi gösterilir: yazdırın ya da güvenli bir yere not edin. Önceki kod artık geçersiz.");
            JTextField field = new JTextField(code);
            field.setEditable(false);
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.putClientProperty(FlatClientProperties.STYLE, "font: bold +8 monospaced");
            d.body().add(field, "span 2, growx");
            JButton copy = new JButton("Panoya kopyala");
            copy.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(code), null);
                Toasts.show(d, Toast.Type.INFO, "Kod panoya kopyalandı");
            });
            d.body().add(copy, "span 2, growx 0");
            d.primary("Not ettim", false, () -> {
                d.close();
                if (onDone != null) onDone.run();
            });
            d.show(parent, null);
        }));
    }

    private static JPasswordField pinField() {
        JPasswordField f = new JPasswordField();
        f.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        return f;
    }
}
