package tr.cabro.servicio.application.component.detail;

import tr.cabro.servicio.application.component.table.ListTable;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableStatePanel;
import tr.cabro.servicio.application.component.table.TableStyler;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Detay sayfalarındaki bir liste bölümü: başlık + tablo, veri yokken tablonun yerine boş durum.
 * Müşteri, cihaz, parça, tedarikçi detaylarındaki geçmiş listeleri aynı iskeleti paylaşır; her
 * bölüm yalnızca kolonlarını ve boş durum metnini tanımlar. Satır tek tıkla (ya da Enter) açılır.
 */
public class DetailListSection<T> extends JPanel {

    private static final String CARD_TABLE = "table";
    private static final String CARD_STATE = "state";

    private final GenericTableModel<T> tableModel;
    private final ListTable table;
    private final TableStatePanel statePanel;
    private final JPanel body = new JPanel(new CardLayout());
    private final JPanel headerActions = new JPanel(new MigLayout("insets 0, gap 8", "", "[]"));

    private final String emptyTitle;
    private final String emptyDescription;
    private String emptyActionText;
    private Runnable emptyAction;
    private Consumer<T> onOpen;

    /**
     * @param compact Genel Bakış içindeki küçük listeler için: daha alçak satır ve dar boş durum.
     */
    public DetailListSection(String title, List<ColumnDef<T>> columns,
                               String emptyTitle, String emptyDescription, boolean compact) {
        this.emptyTitle = emptyTitle;
        this.emptyDescription = emptyDescription;

        setLayout(new MigLayout("insets " + (compact ? "14 16 10 16" : "18 20 14 20") + ", fill, wmin 0",
                "[grow, fill]", "[]" + (compact ? "8" : "12") + "[grow, fill]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        JLabel lblTitle = new JLabel(title);
        lblTitle.putClientProperty(FlatClientProperties.STYLE, compact ? "font: bold +1" : "font: $h3.font");
        headerActions.setOpaque(false);

        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[]"));
        header.setOpaque(false);
        header.add(lblTitle, "wmin 0");
        header.add(headerActions);
        add(header, "growx, wrap");

        tableModel = new GenericTableModel<>(columns);
        table = new ListTable();
        table.setModel(tableModel);
        TableStyler.applyStandardStyle(table);
        if (compact) {
            // Dashboard stili seçimi şeffaf çiziyor; burada Enter ile açılan satırın görünmesi gerekiyor.
            table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                    "height:34; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font:bold -1;");
            table.putClientProperty(FlatClientProperties.STYLE,
                    "rowHeight:40; showHorizontalLines:true; intercellSpacing:0,1; " +
                            "cellFocusColor:$TableHeader.hoverBackground; selectionBackground:$TableHeader.hoverBackground; " +
                            "selectionForeground:$Table.foreground;");
        }
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        installOpenGestures();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        statePanel = new TableStatePanel(compact);
        body.setOpaque(false);
        body.add(scroll, CARD_TABLE);
        body.add(statePanel, CARD_STATE);
        add(body, "grow, wmin 0, hmin 0");

        showLoading();
    }

    public ListTable getTable() {
        return table;
    }

    public GenericTableModel<T> getTableModel() {
        return tableModel;
    }

    /** Başlığın sağına bölüme özgü bir kontrol (buton, özet etiketi) ekler. */
    public void addHeaderAction(JComponent component) {
        headerActions.add(component);
    }

    /** Boş durumda gösterilecek davet butonu — ör. "Yeni servis kaydı". */
    public void setEmptyAction(String actionText, Runnable action) {
        this.emptyActionText = actionText;
        this.emptyAction = action;
    }

    /** Satıra çift tıklama ya da Enter ile kaydı açar. */
    public void setOnOpen(Consumer<T> onOpen) {
        this.onOpen = onOpen;
        table.setRowOpener(onOpen != null ? this::openRow : null, actionColumn);
    }

    public void showLoading() {
        statePanel.showLoading();
        showCard(CARD_STATE);
    }

    public void setData(List<T> items) {
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
        tableModel.setData(items);
        if (items == null || items.isEmpty()) {
            if (emptyAction != null) {
                statePanel.showEmpty(emptyTitle, emptyDescription, emptyActionText, emptyAction);
            } else {
                statePanel.showMessage("icons/info.svg", emptyTitle, emptyDescription);
            }
            showCard(CARD_STATE);
        } else {
            showCard(CARD_TABLE);
        }
    }

    public void showError(String message) {
        statePanel.showMessage("icons/circle-alert.svg", "Yüklenemedi", message);
        showCard(CARD_STATE);
    }

    private void showCard(String card) {
        ((CardLayout) body.getLayout()).show(body, card);
    }

    /** Satır tıklamasının açmadığı işlem kolonu (varsa). */
    private int actionColumn = -1;

    public void setActionColumn(int column) {
        this.actionColumn = column;
        if (onOpen != null) table.setRowOpener(this::openRow, column);
    }

    private void installOpenGestures() {
        // Tek tık ve Enter ListTable'da (setOnOpen); burada ek bir şey gerekmiyor.
    }

    private void installLegacyOpenGestures() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onOpen == null || e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) openRow(row);
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openRow");
        table.getActionMap().put("openRow", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onOpen != null && table.getSelectedRow() >= 0) openRow(table.getSelectedRow());
            }
        });
    }

    private void openRow(int viewRow) {
        T item = tableModel.getItemAt(table.convertRowIndexToModel(viewRow));
        if (item != null) onOpen.accept(item);
    }
}
