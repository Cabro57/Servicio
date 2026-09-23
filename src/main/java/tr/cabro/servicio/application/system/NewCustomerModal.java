package tr.cabro.servicio.application.system;

import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Açık bir modalın (servis kaydı, 2.el alım/satım) içinden "Yeni Müşteri" alt modalını açar.
 * <p>
 * Daha önce bu akış dört formda kopyalanmıştı ve üç kusuru vardı:
 * <ul>
 *   <li>Kayıt hatası (DB kısıtı, servis doğrulaması) hiçbir yerde yakalanmıyordu; "Kaydet"e basınca
 *       sessizce hiçbir şey olmuyor, müşteri eklenmiyordu.</li>
 *   <li>"No"/X seçeneği consume edilmediği için kütüphane tüm modal zincirini kapatıyor, üst
 *       formda girilmiş veriler kayboluyordu. Artık yalnızca bu alt modal kapanır.</li>
 *   <li>Çift tıklamada aynı müşteri iki kez ekleniyordu.</li>
 * </ul>
 */
public final class NewCustomerModal {

    private NewCustomerModal() {
    }

    /**
     * @param rootId  üzerine push edilecek açık modalın id'si
     * @param onSaved müşteri kaydedildikten sonra EDT'de çağrılır (alt modal kapanmadan hemen önce)
     */
    public static void push(String rootId, Consumer<Customer> onSaved) {
        AppModal.pushModalDeferred(() -> {
            CustomerEditPanel panel = new CustomerEditPanel(new Customer());
            boolean[] saving = {false};
            SimpleModalBorder.Option[] options = {
                    new SimpleModalBorder.Option("Müşteriyi Kaydet", SimpleModalBorder.YES_OPTION),
                    new SimpleModalBorder.Option("Vazgeç", SimpleModalBorder.CANCEL_OPTION)
            };
            return new SimpleModalBorder(panel, "Yeni Müşteri", options, (controller, action) -> {
                if (action == SimpleModalBorder.OPENED) return;
                // Kütüphanenin kapatma davranışı tüm zinciri kapatır; burada hep kendimiz yönetiyoruz.
                controller.consume();
                if (action != SimpleModalBorder.YES_OPTION) {
                    if (!saving[0]) AppModal.popModal(rootId);
                    return;
                }
                if (saving[0]) return;
                Customer customer = panel.getData();
                if (customer == null) return;

                saving[0] = true;
                customer.setCreatedAt(LocalDateTime.now());
                try {
                    ServiceManager.getCustomerService().save(customer, false)
                            .thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                                onSaved.accept(saved);
                                AppModal.popModal(rootId);
                            }))
                            .exceptionally(ex -> {
                                SwingUtilities.invokeLater(() -> saving[0] = false);
                                return ErrorHandler.handle(panel, "Müşteri eklenemedi", ex);
                            });
                } catch (RuntimeException ex) {
                    // CustomerService.save doğrulamayı future'dan önce, senkron yapıyor.
                    saving[0] = false;
                    ErrorHandler.handle(panel, "Müşteri eklenemedi", ex);
                }
            });
        }, rootId);
    }
}
