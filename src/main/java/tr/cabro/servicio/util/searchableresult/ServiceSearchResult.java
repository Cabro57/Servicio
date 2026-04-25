package tr.cabro.servicio.util.searchableresult;

import raven.modal.ModalDialog;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import raven.modal.system.FormSearch;
import raven.modal.utils.DemoPreferences;
import tr.cabro.servicio.application.forms.FormWorkOrder;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.service.ServiceManager;

import java.util.Optional;

public class ServiceSearchResult implements ISearchableResult {

    private final WorkOrder workOrder;

    public ServiceSearchResult(WorkOrder workOrder) {
        this.workOrder = workOrder;
    }

    @Override
    public String getDisplayName() {
        // FIX: device_brand/model artık ayrı Device entity'si üzerinden geliyor
        if (workOrder.getDevice() != null) {
            return String.format("%s %s", workOrder.getDevice().getBrand(), workOrder.getDevice().getModel());
        }
        return "Servis #" + workOrder.getId();
    }

    @Override
    public String getDescription() {
        try {
            // join() metodu asenkron işlemin tamamlanmasını bekler ve sonucu döndürür.
            Optional<Customer> customerOpt = ServiceManager.getCustomerService()
                    .get(workOrder.getCustomerId())
                    .join();

            return customerOpt
                    .map(customer -> String.format("%s adlı müşterinin servisi",
                            customer.getFullName()))
                    .orElse("Silinmiş Müşterinin Servisi");

        } catch (Exception e) {
            // Veritabanı hatası veya zaman aşımı durumunda güvenli bir dönüş
            return "Servis Bilgisi Alınamadı";
        }
    }

    @Override
    public String getUniqueId() {
        return "SERVICE:"+ workOrder.getId();
    }

    @Override
    public void executeAction() {
        ModalDialog.closeModal(FormSearch.ID); // Arama panelini kapat

        // SİZİN MİMARİNİZDEKİ DOĞRU ÇAĞRI:
        // Yeni, veri odaklı formu 'new' ile oluştur
        Form formInstance = new FormWorkOrder(workOrder);
        // FormManager'a göster komutu ver
        FormManager.showForm(formInstance);

        // DemoPreferences'a bu dinamik sonucu ekle
        DemoPreferences.addRecentSearch(getUniqueId(), false);
    }
}
