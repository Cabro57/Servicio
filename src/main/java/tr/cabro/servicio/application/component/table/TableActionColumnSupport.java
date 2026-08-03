package tr.cabro.servicio.application.component.table;

import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;

import javax.swing.*;

/**
 * Liste formlarındaki "İşlem" (detay/düzenle/sil) kolonunun kurulumunu sarmalar: her formda birebir
 * tekrarlanan {@code table.isEditing() -> cancelCellEditing() -> convertRowIndexToModel(row) ->
 * tableModel.getItemAt(modelRow)} çözümlemesini ve {@link ActionButtonEditor} kurulumunu tek yerde
 * toplar. Silme onayı gibi form'a özgü davranışlar (JOptionPane/ModalDialog farkı formlar arasında
 * gerçekten değişiyor) kasıtlı olarak burada değil, çağıran formun {@link Handlers#onDelete} içinde
 * kalır — davranış değişmez.
 */
public final class TableActionColumnSupport {

    private TableActionColumnSupport() {
    }

    public static <T> void install(JTable table, int actionColumnIndex,
                                    GenericTableModel<T> tableModel, Handlers<T> handlers) {
        table.getColumnModel().getColumn(actionColumnIndex).setCellEditor(
                new ActionButtonEditor(new TableActionEvent() {
                    @Override
                    public void onView(int row) {
                        handlers.onView(resolve(row));
                    }

                    @Override
                    public void onEdit(int row) {
                        handlers.onEdit(resolve(row));
                    }

                    @Override
                    public void onDelete(int row) {
                        handlers.onDelete(resolve(row));
                    }

                    private T resolve(int row) {
                        if (table.isEditing()) {
                            table.getCellEditor().cancelCellEditing();
                        }
                        int modelRow = table.convertRowIndexToModel(row);
                        return tableModel.getItemAt(modelRow);
                    }
                }));
    }

    public interface Handlers<T> {
        void onView(T item);

        void onEdit(T item);

        void onDelete(T item);
    }
}
