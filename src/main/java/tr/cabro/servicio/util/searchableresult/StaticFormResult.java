package tr.cabro.servicio.util.searchableresult;

import raven.modal.ModalDialog;
import raven.modal.system.AllForms;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import raven.modal.system.FormSearch;
import raven.modal.utils.DemoPreferences;
import raven.modal.utils.SystemForm;

// BU SINIF YENİ OLUŞTURULACAK
public class StaticFormResult implements ISearchableResult {
    private final SystemForm data;
    private final Class<? extends Form> formClass;

    public StaticFormResult(SystemForm data, Class<? extends Form> formClass) {
        this.data = data;
        this.formClass = formClass;
    }

    @Override
    public String getDisplayName() { return data.name(); }

    @Override
    public String getDescription() { return data.description(); }

    @Override
    public String getUniqueId() {
        return "STATIC:"+data.name();
    }

    @Override
    public void executeAction() {
        ModalDialog.closeModal(FormSearch.ID); // Arama panelini kapat

        // SİZİN MİMARİNİZDEKİ DOĞRU ÇAĞRI:
        // Önce AllForms'dan singleton nesneyi al
        Form formInstance = AllForms.getForm(formClass);
        // Sonra FormManager ile göster
        FormManager.showForm(formInstance);

        DemoPreferences.addRecentSearch(getUniqueId(), false);
    }
}
