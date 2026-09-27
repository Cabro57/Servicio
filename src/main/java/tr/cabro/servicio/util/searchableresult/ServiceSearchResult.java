package tr.cabro.servicio.util.searchableresult;

import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.FormSearch;
import tr.cabro.servicio.application.utils.RecentSearchStore;
import tr.cabro.servicio.application.forms.FormWorkOrder;
import tr.cabro.servicio.model.WorkOrder;


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
        // Hydrate edilmiş kayıtta müşteri zaten var; EDT'de veritabanı beklemeyelim.
        if (workOrder.getCustomer() != null) {
            return "SRV-" + workOrder.getId() + "  ·  " + workOrder.getCustomer().getFullName()
                    + (workOrder.getServiceStatus() != null ? "  ·  " + workOrder.getServiceStatus().getDisplayName() : "");
        }
        // Kayıt hydrate edilmişken müşteri yoksa silinmiştir; burada tekrar sorgulamak her çizimde
        // EDT'yi veritabanı için bekletiyordu.
        return "SRV-" + workOrder.getId() + "  ·  Silinmiş Müşterinin Servisi";
    }

    @Override
    public String getIconPath() { return "icons/wrench.svg"; }

    @Override
    public String getUniqueId() {
        return "SERVICE:"+ workOrder.getId();
    }

    @Override
    public void executeAction() {
        AppModal.closeModal(FormSearch.ID); // Arama panelini kapat

        // SİZİN MİMARİNİZDEKİ DOĞRU ÇAĞRI:
        // Yeni, veri odaklı formu 'new' ile oluştur
        Form formInstance = new FormWorkOrder(workOrder);
        // FormManager'a göster komutu ver
        FormManager.showForm(formInstance);

        // Bu dinamik sonucu "son aramalar"a ekle
        RecentSearchStore.add(getUniqueId(), false);
    }
}
