package tr.cabro.servicio.application.utils;

import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.service.exception.ValidationException;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * {@code CompletableFuture.exceptionally(...)} bloklarında kullanılan tek merkezi hata
 * gösterme/loglama noktası. Amaç: her çağrı sitesinde tekrarlanan "logla + EDT'ye geç +
 * toast göster" üçlüsünü tek satıra indirmek ve teknik (SQL/IO vb.) hataların ham haliyle
 * kullanıcıya sızmasını engellemek — sadece {@link ValidationException} mesajı kullanıcı
 * dostu kabul edilip doğrudan gösterilir, diğerleri genel bir mesajla gösterilir ve tam
 * stack trace log'a yazılır.
 */
public final class ErrorHandler {

    private ErrorHandler() {
    }

    public static Void handle(Component owner, String logContext, Throwable ex) {
        Throwable root = unwrap(ex);
        Servicio.getLogger().error(logContext, root);

        String userMessage = (root instanceof ValidationException)
                ? root.getMessage()
                : "Beklenmeyen bir hata oluştu. Ayrıntılar için log dosyasına bakın.";

        SwingUtilities.invokeLater(() -> Toast.show(owner, Toast.Type.ERROR, userMessage));
        return null;
    }

    private static Throwable unwrap(Throwable ex) {
        Throwable t = ex;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
