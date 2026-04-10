package tr.cabro.servicio.util.searchableresult;

import raven.modal.ModalDialog;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import raven.modal.system.FormSearch;
import raven.modal.utils.DemoPreferences;
import tr.cabro.servicio.application.forms.FormService;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.ServiceManager;

import java.util.Optional;

public class ServiceSearchResult implements ISearchableResult {

    private final Service service;

    public ServiceSearchResult(Service service) {
        this.service = service;
    }

    @Override
    public String getDisplayName() {
        return String.format("%s %s", service.getDeviceBrand(), service.getDeviceModel());
    }

    @Override
    public String getDescription() {
        try {
            // join() metodu asenkron işlemin tamamlanmasını bekler ve sonucu döndürür.
            Optional<Customer> customerOpt = ServiceManager.getCustomerService()
                    .get(service.getCustomerId())
                    .join();

            return customerOpt
                    .map(customer -> String.format("%s %s adlı müşterinin servisi",
                            customer.getName(),
                            customer.getSurname()))
                    .orElse("Silinmiş Müşterinin Servisi");

        } catch (Exception e) {
            // Veritabanı hatası veya zaman aşımı durumunda güvenli bir dönüş
            return "Servis Bilgisi Alınamadı";
        }
    }

    @Override
    public String getUniqueId() {
        return "SERVICE:"+service.getId();
    }

    @Override
    public void executeAction() {
        ModalDialog.closeModal(FormSearch.ID); // Arama panelini kapat

        // SİZİN MİMARİNİZDEKİ DOĞRU ÇAĞRI:
        // Yeni, veri odaklı formu 'new' ile oluştur
        Form formInstance = new FormService(service);
        // FormManager'a göster komutu ver
        FormManager.showForm(formInstance);

        // DemoPreferences'a bu dinamik sonucu ekle
        DemoPreferences.addRecentSearch(getUniqueId(), false);
    }
}
