package tr.cabro.servicio.application.panels.customer;

import tr.cabro.servicio.application.component.detail.DetailListSection;
import tr.cabro.servicio.application.tablemodal.ColumnDef;

import java.util.List;

/** Müşteri detayındaki liste bölümü; ortak iskelet {@link DetailListSection}'da. */
public class CustomerListSection<T> extends DetailListSection<T> {

    public CustomerListSection(String title, List<ColumnDef<T>> columns,
                               String emptyTitle, String emptyDescription, boolean compact) {
        super(title, columns, emptyTitle, emptyDescription, compact);
    }
}
