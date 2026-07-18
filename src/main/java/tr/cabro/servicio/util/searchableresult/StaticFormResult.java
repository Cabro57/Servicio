package tr.cabro.servicio.util.searchableresult;

import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.FormSearch;
import tr.cabro.servicio.application.utils.DemoPreferences;
import tr.cabro.servicio.application.utils.SystemForm;

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
        AppModal.closeModal(FormSearch.ID); // Arama panelini kapat

        // SİZİN MİMARİNİZDEKİ DOĞRU ÇAĞRI:
        // Önce AllForms'dan singleton nesneyi al
        Form formInstance = AllForms.getForm(formClass);
        // Sonra FormManager ile göster
        FormManager.showForm(formInstance);

        DemoPreferences.addRecentSearch(getUniqueId(), false);
    }
}
