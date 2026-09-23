package tr.cabro.servicio.util.searchableresult;

import tr.cabro.servicio.application.forms.FormPart;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.FormSearch;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.util.Format;

/** Komut paletinde parça/stok sonucu — parça detayını açar. */
public class PartSearchResult implements ISearchableResult {

    private final Part part;

    public PartSearchResult(Part part) {
        this.part = part;
    }

    @Override
    public String getDisplayName() { return part.getName(); }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        if (part.getBarcode() != null && !part.getBarcode().isEmpty()) sb.append(part.getBarcode()).append("  ·  ");
        sb.append("Stok: ").append(part.getStockQuantity() != null ? part.getStockQuantity() : 0);
        if (part.getSalePrice() != null) sb.append("  ·  ").append(Format.formatPrice(part.getSalePrice()));
        return sb.toString();
    }

    @Override
    public String getUniqueId() { return "PART:" + part.getId(); }

    @Override
    public String getIconPath() { return "icons/package-check.svg"; }

    @Override
    public void executeAction() {
        AppModal.closeModal(FormSearch.ID);
        FormManager.showForm(new FormPart(part));
    }
}
