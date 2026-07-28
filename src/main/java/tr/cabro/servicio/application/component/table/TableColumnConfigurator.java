package tr.cabro.servicio.application.component.table;

import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.tablemodal.ColumnDef;

import javax.swing.*;
import java.util.List;

/**
 * {@link ColumnDef} üzerinde tanımlı renderer/alignment bilgisini otomatik olarak tabloya uygular.
 * Önceden her form kendi {@code configureTableColumns()} metodunda paralel bir
 * {@code Integer[] columnAlignments} dizisi tutuyor ve para/rozet/işlem kolonları için
 * tekrar tekrar aynı renderer'ları elle atıyordu.
 * <p>
 * Sadece {@link ColumnDef}'te renderer/alignment tanımlı olan kolonlara dokunur; forma özel
 * (bespoke) renderer gereken kolonlar (ör. çok satırlı hücreler) bu metottan sonra formun kendi
 * kodunda elle atanmaya devam edebilir — sonraki {@code setCellRenderer} çağrısı önceliklidir.
 */
public final class TableColumnConfigurator {

    private TableColumnConfigurator() {}

    public static <T> void applyColumnRenderers(JTable table, List<ColumnDef<T>> columns) {
        Integer[] alignments = new Integer[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            alignments[i] = columns.get(i).getAlignment();
        }
        // Header hizalama + varsayılan hücre dolgusu — mevcut renderer'ları yakalayıp sarmalar,
        // bu yüzden kolon bazlı setCellRenderer'lardan ÖNCE kurulmalı (bkz. TableHeaderAlignment).
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, alignments));

        for (int i = 0; i < columns.size(); i++) {
            var renderer = columns.get(i).getRenderer();
            if (renderer != null) {
                table.getColumnModel().getColumn(i).setCellRenderer(renderer);
            }
        }
    }
}
