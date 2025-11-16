package tr.cabro.servicio.util.searchableresult;

import raven.modal.ModalDialog;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import raven.modal.system.FormSearch;
import raven.modal.utils.DemoPreferences;
import tr.cabro.servicio.forms.FormService;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.ServiceManager;

public class ServiceSearchResult implements ISearchableResult {

    private final Service service;

    public ServiceSearchResult(Service service) {
        this.service = service;
    }

    @Override
    public String getDisplayName() {
        return String.format("%s %s", service.getDevice_brand(), service.getDevice_model());
    }

    @Override
    public String getDescription() {
        // Müşteriyi Optional nesnesi olarak çek
        return ServiceManager.getCustomerService().get(service.getCustomer_id())
                // Eğer müşteri varsa, formatla
                .map(customer -> String.format("%s adlı müşterinin servisi", customer.getName() + " " + customer.getSurname()))
                // Eğer Optional boşsa (Müşteri silinmişse veya bulunamazsa) güvenli bir varsayılan değer kullan
                .orElse("Silinmiş Müşterinin Servisi");
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
