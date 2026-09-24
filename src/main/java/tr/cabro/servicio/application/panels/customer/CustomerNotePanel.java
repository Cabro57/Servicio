package tr.cabro.servicio.application.panels.customer;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Müşteri detayının sol kolonundaki not kartı. Müşteri notu önceden yalnızca düzenleme
 * penceresinde görülebiliyordu; tezgahta müşteriyle konuşurken göz önünde durması gerekiyor.
 */
public class CustomerNotePanel extends JPanel {

    /** Uzun not kartı sonsuza uzatmasın: bu yükseklikten sonra kendi içinde kayar. */
    private static final int MAX_NOTE_HEIGHT = 220;

    private final JTextArea txtNote = new JTextArea();
    private final JLabel lblEmpty = new JLabel("Not eklenmemiş.");
    private final JButton btnEdit;
    private final JScrollPane noteScroll;

    public CustomerNotePanel(Runnable onEdit) {
        setLayout(new MigLayout("insets 14 16 14 12, fillx, wrap", "[grow, fill]", "[]8[]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        JLabel title = new JLabel("Notlar", new Ikon("icons/file-text.svg", 0.85f, "Label.disabledForeground"), SwingConstants.LEADING);
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold; iconTextGap: 8");

        btnEdit = new JButton(new Ikon("icons/pencil.svg", 0.75f));
        btnEdit.setToolTipText("Notu düzenle");
        btnEdit.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnEdit.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 3,3,3,3");
        btnEdit.addActionListener(e -> onEdit.run());

        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[center]"));
        header.setOpaque(false);
        header.add(title);
        header.add(btnEdit);
        add(header, "growx");

        txtNote.setEditable(false);
        txtNote.setLineWrap(true);
        txtNote.setWrapStyleWord(true);
        txtNote.setOpaque(false);
        txtNote.setFocusable(false);
        txtNote.setBorder(BorderFactory.createEmptyBorder());

        noteScroll = new JScrollPane(txtNote, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            /**
             * Sarılı metnin yüksekliği genişliğe bağlı; JTextArea tercih ettiği boyutu sarılmamış
             * tek satıra göre verdiği için kart notun yalnızca ilk satırını gösteriyordu. Yükseklik
             * burada kaydırma alanının gerçek genişliğinde sarılmış metinden hesaplanır.
             */
            @Override
            public Dimension getPreferredSize() {
                Dimension base = super.getPreferredSize();
                int width = getWidth();
                if (width <= 0) return base;
                txtNote.setSize(width, Short.MAX_VALUE);
                return new Dimension(base.width, Math.min(txtNote.getPreferredSize().height, MAX_NOTE_HEIGHT));
            }
        };
        noteScroll.setBorder(BorderFactory.createEmptyBorder());
        noteScroll.setOpaque(false);
        noteScroll.getViewport().setOpaque(false);
        // İlk yerleşimde genişlik henüz 0; genişlik belli olunca yükseklik yeniden hesaplansın.
        noteScroll.addComponentListener(new ComponentAdapter() {
            private int lastWidth = -1;

            @Override
            public void componentResized(ComponentEvent e) {
                if (noteScroll.getWidth() != lastWidth) {
                    lastWidth = noteScroll.getWidth();
                    CustomerNotePanel.this.revalidate();
                }
            }
        });
        add(noteScroll, "wmin 0, hidemode 3");

        lblEmpty.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        add(lblEmpty, "hidemode 3");

        setNote(null);
    }

    public void setNote(String note) {
        boolean has = note != null && !note.isBlank();
        txtNote.setText(has ? note.trim() : "");
        txtNote.setCaretPosition(0);
        noteScroll.setVisible(has);
        lblEmpty.setVisible(!has);
        btnEdit.setToolTipText(has ? "Notu düzenle" : "Not ekle");
        revalidate();
    }
}
