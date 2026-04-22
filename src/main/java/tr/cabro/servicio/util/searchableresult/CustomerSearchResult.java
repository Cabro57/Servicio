package tr.cabro.servicio.util.searchableresult;

import raven.modal.ModalDialog;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import raven.modal.system.FormSearch;
import raven.modal.utils.DemoPreferences;
import tr.cabro.servicio.application.forms.FormCustomer;
import tr.cabro.servicio.model.Customer;

public class CustomerSearchResult implements ISearchableResult {

    private final Customer customer;

    public CustomerSearchResult(Customer customer) {
        this.customer = customer;
    }

    @Override
    public String getDisplayName() {
        return customer.getFullName();
    }

    @Override
    public String getDescription() {
        return "Müşteri hakkında bilgilere bakarsın";
    }

    @Override
    public String getUniqueId() {
        return "CUSTOMER:"+customer.getId();
    }

    @Override
    public void executeAction() {
        ModalDialog.closeModal(FormSearch.ID); // Arama panelini kapat

        // SİZİN MİMARİNİZDEKİ DOĞRU ÇAĞRI:
        // Yeni, veri odaklı formu 'new' ile oluştur
        Form formInstance = new FormCustomer(customer);
        // FormManager'a göster komutu ver
        FormManager.showForm(formInstance);

        // DemoPreferences'a bu dinamik sonucu ekle
        DemoPreferences.addRecentSearch(getUniqueId(), false);
    }
}
