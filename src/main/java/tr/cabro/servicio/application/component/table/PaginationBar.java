package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.swingpack.JPagination;

import javax.swing.*;
import java.util.function.IntConsumer;

/**
 * Sayfalama + sayfa boyutu seçimi için ortak bileşen.
 * Önceden her DB-tabanlı liste ekranı ({@code createPaginationComponent()}) bu kurulumu
 * (JPagination + sayfa-boyutu combo + Settings'e kaydetme çağrısı hariç) birebir kopyalıyordu.
 * Sayfa boyutunun kalıcı hale getirilmesi (Settings) çağıran forma bırakılmıştır — her ekranın
 * kendi ayar alanı farklıdır (workOrderPageSize, partPageSize vb.).
 */
public class PaginationBar extends JPanel {

    private final JPagination pagination;
    private final JComboBox<Integer> pageSizeCombo;

    public PaginationBar(int visibleButtons, Integer[] pageSizeOptions, int initialPageSize,
                          IntConsumer onPageChanged, IntConsumer onPageSizeChanged) {
        setLayout(new MigLayout("insets 0, gapx 10", "[][]", "[]"));
        setOpaque(false);

        pagination = new JPagination(visibleButtons, 1, 1);
        pagination.setBackground(null);
        pagination.addChangeListener(e -> onPageChanged.accept(pagination.getSelectedPage()));

        pageSizeCombo = new JComboBox<>(pageSizeOptions);
        pageSizeCombo.setSelectedItem(initialPageSize);
        pageSizeCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        pageSizeCombo.addActionListener(e -> onPageSizeChanged.accept((Integer) pageSizeCombo.getSelectedItem()));

        add(new JLabel("Sayfa başına:"));
        add(pageSizeCombo);
        add(pagination);
    }

    public void setPageRange(int page, int totalPages) {
        pagination.setPageRange(page, totalPages);
    }
}
