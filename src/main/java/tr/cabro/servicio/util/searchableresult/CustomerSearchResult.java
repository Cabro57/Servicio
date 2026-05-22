package tr.cabro.servicio.util.searchableresult;

import raven.modal.ModalDialog;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.FormSearch;
import tr.cabro.servicio.application.forms.FormCustomer;
import tr.cabro.servicio.application.utils.DemoPreferences;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.util.PhoneHelper;

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
        String phoneNumber = (customer.getPhoneNumber1() != null && !customer.getPhoneNumber1().isEmpty())
                ? PhoneHelper.formatForDisplay(customer.getPhoneNumber1())
                : null;

        String idNo = (customer.getIdentityNo() != null && !customer.getIdentityNo().isEmpty())
                ? customer.getIdentityNo()
                : null;

        // Verilerin durumuna göre dinamik yapı
        if (phoneNumber != null && idNo != null) {
            return phoneNumber + " • " + idNo; // Daha şık bir ayraç
        } else if (phoneNumber != null) {
            return phoneNumber;
        } else if (idNo != null) {
            return "Kimlik No: " + idNo;
        } else {
            return "Bilgi tanımlı değil"; // Veya boş döndürebilirsiniz: ""
        }
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
