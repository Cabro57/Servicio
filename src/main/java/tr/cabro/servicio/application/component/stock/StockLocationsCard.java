package tr.cabro.servicio.application.component.stock;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.model.StockLevel;
import tr.cabro.servicio.model.enums.StockItemKind;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.util.List;

/**
 * Detay rayındaki "Depolar" kartı: kalemin hangi depoda kaç adet olduğu. Varsayılan depo önce,
 * boş depolar soluk. Başlıktaki "Transfer" bağlantısı hareket penceresini transfer modunda açar.
 */
public final class StockLocationsCard {

    private final JPanel card;
    private final JPanel rows = new JPanel(new MigLayout("insets 0, fillx, gap 12 8, hidemode 3", "[grow, fill][right]", ""));
    private final JButton transferLink;

    public StockLocationsCard(Runnable onTransfer) {
        transferLink = DetailKit.link("Transfer", onTransfer);
        card = DetailKit.card("Depolar", transferLink);
        rows.setOpaque(false);
        card.add(rows);
    }

    public JPanel component() {
        return card;
    }

    public void load(StockItemKind kind, Long itemId) {
        ServiceManager.getStockService().getLevels(kind, itemId)
                .thenAccept(levels -> SwingUtilities.invokeLater(() -> setLevels(levels)))
                .exceptionally(ex -> ErrorHandler.handle(card, "Depo bakiyeleri okunamadı", ex));
    }

    private void setLevels(List<StockLevel> levels) {
        rows.removeAll();
        for (StockLevel l : levels) {
            boolean empty = l.getQuantity() == 0;
            JLabel name = new JLabel(l.getWarehouseName());
            name.putClientProperty(FlatClientProperties.STYLE, empty ? "foreground: $Label.disabledForeground" : "");
            JLabel tag = DetailKit.small(l.isDefaultWarehouse() ? "varsayılan" : !l.isActiveWarehouse() ? "pasif" : "");
            JPanel left = new JPanel(new MigLayout("insets 0, gap 6", "[][]", "[baseline]"));
            left.setOpaque(false);
            left.add(name, "wmin 0");
            left.add(tag);

            JLabel qty = new JLabel(l.getQuantity() + " adet");
            qty.putClientProperty(FlatClientProperties.STYLE, empty ? "foreground: $Label.disabledForeground"
                    : l.getQuantity() < 0 ? "font: bold; foreground: $Servicio.dangerColor" : "font: bold");
            if (l.getQuantity() < 0) qty.setToolTipText("Satış stoktan fazla yapılmış; sayım ya da giriş ile düzeltin.");
            rows.add(left, "wmin 0");
            rows.add(qty, "wrap");
        }
        // Tek depoda transfer anlamsız.
        transferLink.setVisible(levels.stream().filter(StockLevel::isActiveWarehouse).count() > 1);
        rows.revalidate();
        rows.repaint();
    }
}
