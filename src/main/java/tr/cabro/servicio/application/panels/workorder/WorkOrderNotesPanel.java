package tr.cabro.servicio.application.panels.workorder;

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
 * `FormWorkOrder`'ın sağ kolonundaki "Teknisyen Notları" kartı — eskiden
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
        putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); arc: 15;");
        setLayout(new MigLayout("insets 20, fillx", "[grow]", "[]15[]15[]"));

        JLabel title = new JLabel("Teknisyen Notları");
        title.setIcon(new Ikon("icons/file-text.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        add(title, "wrap");

        notesListPanel = new JPanel(new MigLayout("insets 0, fillx", "[grow]", "[]"));
        notesListPanel.setOpaque(false);
        populateNotesList();
        add(notesListPanel, "growx, wrap");

        JTextArea txtNewNote = new JTextArea(3, 20);
        txtNewNote.setLineWrap(true);
        txtNewNote.setWrapStyleWord(true);
        txtNewNote.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Servis süreciyle ilgili notlarınızı buraya yazın...");
        txtNewNote.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); border: 10,10,10,10;");

        JScrollPane scrollNote = new JScrollPane(txtNewNote);
        add(scrollNote, "wrap, growx, h 80!");

        JButton btnAddNote = new JButton("+ Not Ekle");
        btnAddNote.putClientProperty(FlatClientProperties.STYLE,
                "background: #1e3a8a; foreground: #3498db; arc: 10; font: bold; borderWidth: 0");
        btnAddNote.addActionListener(e -> {
            String text = txtNewNote.getText().trim();
            if (text.isEmpty()) {
                Toast.show(this, Toast.Type.WARNING, Messages.get("toast.note.empty"));
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
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.note.added"));
            })).exceptionally(ex -> ErrorHandler.handle(this, "Not eklenemedi", ex));
        });

        add(btnAddNote, "align right");
    }

    private void populateNotesList() {
        notesListPanel.removeAll();
        List<WorkOrderNote> notes = workOrder.getTechnicianNotes();
        if (notes == null || notes.isEmpty()) {
            notesListPanel.add(WorkOrderPanelSupport.createMutedLabel("Henüz teknisyen notu eklenmedi."), "wrap");
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

    private void addNoteRowToPanel(WorkOrderNote note) {
        DateTimeFormatter df = DateFormats.dateTime();

        JPanel noteRow = new JPanel(new MigLayout("insets 10 12 10 12, fillx", "[grow][]", "[]6[]"));
        noteRow.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 4%); arc: 12; border: 1,1,1,1,$Component.borderColor");

        JTextArea lblNote = new JTextArea(note.getNote());
        lblNote.setEditable(false);
        lblNote.setOpaque(false);
        lblNote.setLineWrap(true);
        lblNote.setWrapStyleWord(true);
        lblNote.setFocusable(false);
        lblNote.putClientProperty(FlatClientProperties.STYLE, "font: +1; border: 0,0,0,0");

        JButton btnDeleteNote = new JButton(new Ikon("icons/x.svg", 0.75f));
        btnDeleteNote.putClientProperty(FlatClientProperties.BUTTON_TYPE, "toolBarButton");
        btnDeleteNote.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDeleteNote.setToolTipText("Notu Sil");
        btnDeleteNote.addActionListener(e -> confirmDeleteNote(note, noteRow));

        JLabel lblAuthor = WorkOrderPanelSupport.createMutedLabel(note.getTechnicianId() == null ? "Sistem" : "Teknisyen");
        JLabel lblDate = WorkOrderPanelSupport.createMutedLabel(note.getCreatedAt() != null ? note.getCreatedAt().format(df) : "-");

        noteRow.add(lblNote, "growx, wmin 0, aligny top");
        noteRow.add(btnDeleteNote, "top, right, w 28!, h 28!, wrap");
        noteRow.add(lblAuthor, "aligny bottom");
        noteRow.add(lblDate, "align right, aligny bottom");

        notesListPanel.add(noteRow, "wrap, growx, gapy 0 8");
    }

    private void confirmDeleteNote(WorkOrderNote note, JPanel noteRow) {
        DialogHelper.confirmDelete(this, "confirm.delete.note", () ->
                workOrderService.deleteNote(note.getId()).thenRun(() -> SwingUtilities.invokeLater(() -> {
                    workOrder.getTechnicianNotes().remove(note);
                    notesListPanel.remove(noteRow);
                    if (workOrder.getTechnicianNotes().isEmpty()) {
                        notesListPanel.removeAll();
                        notesListPanel.add(WorkOrderPanelSupport.createMutedLabel("Henüz teknisyen notu eklenmedi."), "wrap");
                    }
                    notesListPanel.revalidate();
                    notesListPanel.repaint();
                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.note.deleted"));
                })).exceptionally(ex -> ErrorHandler.handle(this, "Not silinemedi", ex)));
    }
}
