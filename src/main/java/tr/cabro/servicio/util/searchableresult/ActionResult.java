package tr.cabro.servicio.util.searchableresult;

import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.FormSearch;
import tr.cabro.servicio.application.system.QuickAction;

/** Komut paletinde bir işlem satırı (Yeni Servis, Tahsilat Al...). Son aramalara eklenmez; işlemler her zaman listededir. */
public class ActionResult implements ISearchableResult {

    private final QuickAction action;

    public ActionResult(QuickAction action) {
        this.action = action;
    }

    @Override
    public String getDisplayName() { return action.getLabel(); }

    @Override
    public String getDescription() { return action.getDescription(); }

    @Override
    public String getUniqueId() { return "ACTION:" + action.name(); }

    @Override
    public String getIconPath() { return action.getIconPath(); }

    @Override
    public String getShortcutText() { return action.getShortcutText(); }

    @Override
    public void executeAction() {
        AppModal.closeModal(FormSearch.ID);
        action.run();
    }
}
