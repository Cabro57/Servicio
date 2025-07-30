package tr.cabro.servicio.application.compenents.table;

public interface TableActionEvent {

    void onAdd(int row);

    void onRemove(int row);
}
