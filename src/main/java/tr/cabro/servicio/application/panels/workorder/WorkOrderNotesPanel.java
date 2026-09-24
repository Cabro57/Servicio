package tr.cabro.servicio.application.panels.workorder;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.WorkOrderNote;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;

import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.util.DialogHelper;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * İş emri "1 Arıza ve tespit" adımının sağ yarısındaki teknisyen notları (kendi kartı yok; adım
 * kartının içinde durur, kart içinde kart olmasın diye). Eskiden
 * {@code FormWorkOrder.buildNotesCard()}/{@code populateNotesList()}/{@code appendNoteRow()}/
 * {@code addNoteRowToPanel()}/{@code confirmDeleteNote()} olarak tek sınıfta duruyordu. Notlar
 * kalan bakiyeyi etkilemediği için (item/payment panellerinin aksine) dışa açık bir refresh
 * callback'ine ihtiyaç duymaz.
 */
public class WorkOrderNotesPanel extends JPanel {

    private final WorkOrder workOrder;
    private final WorkOrderService workOrderService;

    private JPanel notesListPanel;

    public WorkOrderNotesPanel(WorkOrder workOrder) {
        this.workOrder = workOrder;
        this.workOrderService = ServiceManager.getWorkOrderService();
        build();
    }

    private void build() {
        setOpaque(false);
        setLayout(new MigLayout("insets 0, fillx, wrap, hidemode 3", "[grow, fill]", "[]6[]8[]8[]"));

        add(WorkOrderPanelSupport.createCaption("Teknisyen notları"));

        notesListPanel = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0", "[grow, fill]", ""));
        notesListPanel.setOpaque(false);
        populateNotesList();
        add(notesListPanel, "wmin 0");

        JTextArea txtNewNote = WorkOrderPanelSupport.createHintArea("Not yazın…");
        txtNewNote.setLineWrap(true);
        txtNewNote.setWrapStyleWord(true);
        txtNewNote.putClientProperty(FlatClientProperties.STYLE, "border: 8,10,8,10");
        txtNewNote.getAccessibleContext().setAccessibleName("Yeni teknisyen notu");

        JScrollPane inputBox = new JScrollPane(txtNewNote);
        inputBox.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        add(inputBox, "h 84!, wmin 0");

        JButton btnAddNote = new JButton("Not ekle", new Ikon("icons/plus.svg", 14, "Label.foreground"));
        btnAddNote.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10; iconTextGap: 4");
        btnAddNote.addActionListener(e -> {
            String text = txtNewNote.getText().trim();
            if (text.isEmpty()) {
                Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.note.empty"));
                return;
            }
            WorkOrderNote n = new WorkOrderNote();
            n.setServiceId(workOrder.getId());
            n.setNote(text);
            n.setCreatedAt(LocalDateTime.now());

            workOrderService.addNote(n).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                workOrder.getTechnicianNotes().add(saved);
                txtNewNote.setText("");
                appendNoteRow(saved);
                Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.note.added"));
            })).exceptionally(ex -> ErrorHandler.handle(this, "Not eklenemedi", ex));
        });

        add(btnAddNote, "growx 0, al right");
    }

    private void populateNotesList() {
        notesListPanel.removeAll();
        List<WorkOrderNote> notes = workOrder.getTechnicianNotes();
        if (notes == null || notes.isEmpty()) {
            notesListPanel.add(emptyLabel());
        } else {
            for (WorkOrderNote n : notes) {
                addNoteRowToPanel(n);
            }
        }
        notesListPanel.revalidate();
        notesListPanel.repaint();
    }

    private void appendNoteRow(WorkOrderNote note) {
        if (notesListPanel.getComponentCount() == 1
                && notesListPanel.getComponent(0) instanceof JLabel) {
            notesListPanel.removeAll();
        }
        addNoteRowToPanel(note);
        notesListPanel.revalidate();
        notesListPanel.repaint();
    }

    private static JLabel emptyLabel() {
        JLabel l = WorkOrderPanelSupport.createMutedLabel("Henüz not yok.");
        l.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return l;
    }

    /** Not satırı: metin, altında soluk "Teknisyen · tarih", sağda sil; satırlar ince çizgiyle ayrılır. */
    private void addNoteRowToPanel(WorkOrderNote note) {
        DateTimeFormatter df = DateFormats.dateTime();

        // Üst ayraç çizimde okunur ki tema değişince renk de değişsin.
        JPanel noteRow = new JPanel(new MigLayout("insets 8 0 8 0, fillx, gap 8 2", "[grow][]", "[][]")) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(UIManager.getColor("Component.borderColor"));
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        noteRow.setOpaque(false);

        JTextArea lblNote = new JTextArea(note.getNote());
        lblNote.setEditable(false);
        lblNote.setOpaque(false);
        lblNote.setLineWrap(true);
        lblNote.setWrapStyleWord(true);
        lblNote.setFocusable(false);
        lblNote.putClientProperty(FlatClientProperties.STYLE, "border: 0,0,0,0");

        JButton btnDeleteNote = new JButton(new Ikon("icons/x.svg", 14, "Label.disabledForeground"));
        btnDeleteNote.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnDeleteNote.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 3,3,3,3");
        btnDeleteNote.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDeleteNote.setToolTipText("Notu sil");
        btnDeleteNote.getAccessibleContext().setAccessibleName("Notu sil");
        btnDeleteNote.addActionListener(e -> confirmDeleteNote(note, noteRow));

        String author = note.getTechnicianId() == null ? "Sistem" : "Teknisyen";
        String when = note.getCreatedAt() != null ? note.getCreatedAt().format(df) : "—";

        noteRow.add(lblNote, "growx, wmin 0, aligny top");
        noteRow.add(btnDeleteNote, "top, spany 2, wrap");
        noteRow.add(WorkOrderPanelSupport.createCaption(author + "  ·  " + when), "wmin 0");

        notesListPanel.add(noteRow, "growx, wmin 0");
    }

    private void confirmDeleteNote(WorkOrderNote note, JPanel noteRow) {
        DialogHelper.confirmDelete(this, "confirm.delete.note", () ->
                workOrderService.deleteNote(note.getId()).thenRun(() -> SwingUtilities.invokeLater(() -> {
                    workOrder.getTechnicianNotes().remove(note);
                    notesListPanel.remove(noteRow);
                    if (workOrder.getTechnicianNotes().isEmpty()) {
                        notesListPanel.removeAll();
                        notesListPanel.add(emptyLabel());
                    }
                    notesListPanel.revalidate();
                    notesListPanel.repaint();
                    Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.note.deleted"));
                })).exceptionally(ex -> ErrorHandler.handle(this, "Not silinemedi", ex)));
    }
}
