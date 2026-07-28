package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;

/**
 * Uygulama genelinde tekrarlanan tablo görsel stillerini tek yerde toplar.
 * Önceden {@code AbstractTableForm} ve dashboard mini-tabloları (ActiveServiceTable,
 * PendingPaymentsTable) aynı FlatLaf STYLE bloklarını birebir kopyalıyordu.
 */
public final class TableStyler {

    private TableStyler() {}

    /** Liste ekranlarındaki (Servis Kayıtları, Müşteriler vb.) tam-boy tablo stili. */
    public static void applyStandardStyle(JTable table) {
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:40; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font: $large.font;");
        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:52; showHorizontalLines:true; intercellSpacing:0,1; " +
                        "cellFocusColor:$TableHeader.hoverBackground; selectionBackground:$TableHeader.hoverBackground; " +
                        "selectionForeground:$Table.foreground; font: $large.font;");
    }

    /** Dashboard'daki küçük özet tabloları (Aktif Servisler, Bekleyen Tahsilatlar) için şeffaf/kompakt stil. */
    public static void applyDashboardStyle(JTable table) {
        table.setRowHeight(40);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setOpaque(false);
        ((JComponent) table.getDefaultRenderer(Object.class)).setOpaque(false);
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:40; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font:bold -1;");
        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:37; showHorizontalLines:true; intercellSpacing:0,1; " +
                        "cellFocusColor:null; selectionBackground:$TableHeader.hoverBackground; " +
                        "selectionForeground:$Table.foreground;");
    }
}
