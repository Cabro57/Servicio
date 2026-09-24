package tr.cabro.servicio.application.component.table;

import tr.cabro.servicio.application.tablemodal.GenericTableModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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
        // Liste sayfalarında düğmeler ayrı kolonda değil, satırın üstüne binen şeritte (ListTable).
        if (table instanceof ListTable) {
            ListTable lt = (ListTable) table;
            java.util.function.Function<Integer, T> at = row -> tableModel.getItemAt(table.convertRowIndexToModel(row));
            lt.setRowActions(actionColumnIndex, java.util.List.of(
                    new ListTable.RowAction("icons/eye.svg", tr.cabro.servicio.application.themes.SemanticColor.info(),
                            "Detayı aç (satıra tıklayın ya da Enter)", row -> handlers.onView(at.apply(row))),
                    new ListTable.RowAction("icons/pencil.svg", tr.cabro.servicio.application.themes.SemanticColor.warning(),
                            "Düzenle", row -> handlers.onEdit(at.apply(row))),
                    new ListTable.RowAction("icons/trash-2.svg", tr.cabro.servicio.application.themes.SemanticColor.danger(),
                            "Sil", row -> handlers.onDelete(at.apply(row)))));
            installRowActivation(table, actionColumnIndex, tableModel, handlers);
            return;
        }
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

        installRowActivation(table, actionColumnIndex, tableModel, handlers);
    }

    /**
     * Satırı açmanın klavye ve çift tık yolu. Tek açma yolu "İşlem" kolonundaki ~20px göz
     * ikonuna nişan almaktı; sıra sıra kayıt işleyen bir kullanıcı için bu fare hızına
     * mahkûmiyet demekti. Ortak katmana konur, böylece bu sarmalayıcıyı kullanan tüm liste
     * formları davranışı tek seferde alır.
     * <p>
     * {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT} kapsamı bilinçli: Enter yalnızca odak
     * tablodayken satırı açar, formun başka bir alanındayken (ör. arama kutusu) karışmaz.
     * JTable'ın kendi Enter davranışı (bir alt satıra geç) bu formlarda zaten bir işe
     * yaramıyordu, üzerine yazılması bir şey kaybettirmez.
     */
    private static <T> void installRowActivation(JTable table, int actionColumnIndex,
                                                 GenericTableModel<T> tableModel, Handlers<T> handlers) {
        // Liste sayfalarında satırın tamamı hedeftir: tek tık ve Enter ListTable'da.
        if (table instanceof ListTable) {
            ((ListTable) table).setRowOpener(row -> {
                T item = tableModel.getItemAt(table.convertRowIndexToModel(row));
                if (item != null) handlers.onView(item);
            }, actionColumnIndex);
            return;
        }
        Runnable openSelected = () -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            if (table.isEditing()) {
                table.getCellEditor().cancelCellEditing();
            }
            T item = tableModel.getItemAt(table.convertRowIndexToModel(row));
            if (item != null) handlers.onView(item);
        };

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                // İşlem kolonundaki çift tık butonların kendi işine ait, satırı açmamalı.
                int column = table.columnAtPoint(e.getPoint());
                if (column == actionColumnIndex) return;
                openSelected.run();
            }
        });

        String actionKey = "servicio.openSelectedRow";
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), actionKey);
        table.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelected.run();
            }
        });
    }

    public interface Handlers<T> {
        void onView(T item);

        void onEdit(T item);

        void onDelete(T item);
    }
}
