package tr.cabro.servicio.application.system;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.documents.DocumentFormat;
import tr.cabro.servicio.documents.DocumentRequest;
import tr.cabro.servicio.documents.DocumentText;
import tr.cabro.servicio.documents.DocumentTexts;
import tr.cabro.servicio.documents.PdfDocumentBuilder;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.DialogHelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Belge oluşturma penceresi: servis ve 2.el "Form" menülerinden açılır.
 * <p>
 * Pencerede belgenin bu seferlik girdileri düzenlenir: imza isimleri, garanti günü ve
 * {@link DocumentText} metinleri (varsayılanlar Ayarlar &gt; Belge Metinleri'nden gelir).
 * "Varsayılan olarak kaydet" işaretlenirse değişiklikler sonraki belgeler için de saklanır.
 * <p>
 * Çıktı: "Aç / Yazdır" geçici bir PDF açar; "Farklı Kaydet…" dosya seçtirip seçilen filtreye
 * göre PDF, Word ya da RTF yazar (termal fişlerde yalnızca PDF).
 */
public final class DocumentExportModal {

    private static final String MODAL_ID = "document_export_modal";

    /** Son kaydedilen klasör; oturum boyunca hatırlanır. */
    private static File lastDirectory;

    /** Belgeyi verilen biçimde verilen dosyaya yazar (arka planda çağrılır). */
    @FunctionalInterface
    public interface Producer {
        File produce(DocumentRequest request, DocumentFormat format, File outFile) throws Exception;
    }

    /**
     * Pencerenin içeriği.
     *
     * @param title          pencere başlığı ve belge adı
     * @param fileBaseName   önerilen dosya adı, uzantısız ("servis-teslim-SRV12")
     * @param leftLabel      sol imza etiketi; {@code null} ise imza alanı sorulmaz
     * @param warrantyDays   garanti günü ve garanti notu alanı gösterilsin mi
     * @param texts          düzenlenebilir metinler
     * @param formats        kaydedilebilen biçimler
     */
    public record Spec(String title, String fileBaseName,
                       String leftLabel, String leftDefault, String rightLabel, String rightDefault,
                       boolean warrantyDays, List<DocumentText> texts, Set<DocumentFormat> formats) {
    }

    private DocumentExportModal() {
    }

    public static void show(Component owner, Spec spec, Producer producer) {
        Form form = new Form(spec);
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Aç / Yazdır", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("Farklı Kaydet…", SimpleModalBorder.NO_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
        AppModal.showModal(owner, new SimpleModalBorder(form, spec.title(), options, (controller, action) -> {
            if (action == SimpleModalBorder.YES_OPTION) {
                DocumentRequest request = form.collect();
                produceAsync(owner, producer, request, DocumentFormat.PDF, null);
            } else if (action == SimpleModalBorder.NO_OPTION) {
                File target = chooseFile(owner, spec);
                if (target == null) {
                    controller.consume(); // Dosya seçilmedi: pencere açık kalsın.
                    return;
                }
                DocumentRequest request = form.collect();
                DocumentFormat format = formatOf(target, spec.formats());
                if (target.exists()) {
                    DialogHelper.confirm(owner, "confirm.overwrite.title", "confirm.overwrite.message",
                            () -> produceAsync(owner, producer, request, format, target), target.getName());
                } else {
                    produceAsync(owner, producer, request, format, target);
                }
            }
        }), MODAL_ID);
    }

    // -------------------------------------------------------------------------
    // Üretim
    // -------------------------------------------------------------------------

    /** {@code target} null ise geçici PDF üretip açar, değilse dosyaya kaydeder. */
    private static void produceAsync(Component owner, Producer producer, DocumentRequest request,
                                     DocumentFormat format, File target) {
        CompletableFuture.runAsync(() -> {
            try {
                File out = target != null ? target : PdfDocumentBuilder.tempFile("belge");
                File result = producer.produce(request, format, out);
                SwingUtilities.invokeLater(() -> {
                    if (target != null) {
                        Toasts.show(owner, Toast.Type.SUCCESS, Messages.get("toast.document.saved", result.getAbsolutePath()));
                    } else if (DesktopHelper.openFile(result)) {
                        Toasts.show(owner, Toast.Type.SUCCESS, Messages.get("toast.document.created"));
                    } else {
                        Toasts.show(owner, Toast.Type.WARNING, Messages.get("toast.document.created.openFailed", result.getAbsolutePath()));
                    }
                });
            } catch (Exception ex) {
                Servicio.getLogger().error("Belge oluşturma hatası", ex);
                SwingUtilities.invokeLater(() -> Toasts.show(owner, Toast.Type.ERROR,
                        Messages.get("toast.document.failed", String.valueOf(ex.getMessage()))));
            }
        });
    }

    private static File chooseFile(Component owner, Spec spec) {
        JFileChooser chooser = new JFileChooser(lastDirectory != null ? lastDirectory
                : FileSystemView.getFileSystemView().getDefaultDirectory());
        chooser.setDialogTitle(spec.title() + " — Farklı Kaydet");
        chooser.setAcceptAllFileFilterUsed(false);
        Map<FileNameExtensionFilter, DocumentFormat> filters = new java.util.LinkedHashMap<>();
        for (DocumentFormat format : spec.formats()) {
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    format.getDisplayName() + " (*." + format.getExtension() + ")", format.getExtension());
            filters.put(filter, format);
            chooser.addChoosableFileFilter(filter);
        }
        chooser.setFileFilter(filters.keySet().iterator().next());
        chooser.setSelectedFile(new File(spec.fileBaseName() + "." + DocumentFormat.PDF.getExtension()));

        // Biçim değişince önerilen dosya adının uzantısı da değişsin.
        chooser.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, e -> {
            DocumentFormat format = filters.get(chooser.getFileFilter());
            if (format == null) return;
            File current = chooser.getSelectedFile();
            String name = current != null ? stripExtension(current.getName()) : spec.fileBaseName();
            chooser.setSelectedFile(new File(name + "." + format.getExtension()));
        });

        if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(owner)) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        DocumentFormat format = filters.getOrDefault(chooser.getFileFilter(), DocumentFormat.PDF);
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + format.getExtension())) {
            file = new File(file.getParentFile(), file.getName() + "." + format.getExtension());
        }
        lastDirectory = file.getParentFile();
        return file;
    }

    private static DocumentFormat formatOf(File file, Set<DocumentFormat> allowed) {
        String name = file.getName().toLowerCase();
        for (DocumentFormat format : allowed) {
            if (name.endsWith("." + format.getExtension())) return format;
        }
        return DocumentFormat.PDF;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // -------------------------------------------------------------------------
    // Form
    // -------------------------------------------------------------------------

    private static final class Form extends JPanel {

        private final Spec spec;
        private JTextField leftSigner;
        private JTextField rightSigner;
        private JSpinner warrantySpinner;
        private JTextArea warrantyNote;
        private boolean warrantyNoteEdited;
        private boolean updatingWarrantyNote;
        private final Map<DocumentText, JTextComponent> textFields = new EnumMap<>(DocumentText.class);
        private JCheckBox saveAsDefault;

        Form(Spec spec) {
            this.spec = spec;
            setLayout(new MigLayout("wrap, insets 14 18 8 18, fillx, width 560:560:", "[grow, fill]", ""));

            boolean hasEditables = false;
            if (spec.leftLabel() != null) {
                JPanel signers = new JPanel(new MigLayout("insets 0, gapx 14, wrap 2", "[grow, fill, sg s][grow, fill, sg s]", "[]3[]"));
                signers.setOpaque(false);
                signers.add(label(spec.leftLabel()));
                signers.add(label(spec.rightLabel()));
                leftSigner = new JTextField(nvl(spec.leftDefault()));
                rightSigner = new JTextField(nvl(spec.rightDefault()));
                signers.add(leftSigner);
                signers.add(rightSigner);
                add(signers, "gapbottom 10");
            }

            if (spec.warrantyDays()) {
                hasEditables = true;
                addWarrantySection();
            }

            for (DocumentText text : spec.texts()) {
                hasEditables = true;
                add(label(text.getLabel()), "gaptop 6");
                if (text.isMultiline()) {
                    JTextArea area = textArea(DocumentTexts.get(text), 3);
                    add(scroll(area), "hmin 70");
                    textFields.put(text, area);
                } else {
                    JTextField field = new JTextField(DocumentTexts.get(text));
                    add(field);
                    textFields.put(text, field);
                }
            }

            if (hasEditables) {
                saveAsDefault = new JCheckBox("Bu metinleri sonraki belgeler için varsayılan yap");
                saveAsDefault.setToolTipText("İşaretlemezseniz değişiklik yalnızca bu belgeye uygulanır.");
                add(saveAsDefault, "gaptop 10");
            } else if (spec.leftLabel() == null) {
                JLabel none = new JLabel("Bu belgede düzenlenecek alan yok. Açabilir ya da farklı kaydedebilirsiniz.");
                none.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
                add(none);
            }
        }

        private void addWarrantySection() {
            add(label("Garanti Süresi"));
            JPanel row = new JPanel(new MigLayout("insets 0, gapx 8", "[80!][]", "[]"));
            row.setOpaque(false);
            warrantySpinner = new JSpinner(new SpinnerNumberModel(DocumentTexts.getWarrantyDays(), 0, 3650, 15));
            row.add(warrantySpinner);
            JLabel hint = new JLabel("gün  ·  0 girerseniz garanti verilmez");
            hint.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
            row.add(hint);
            add(row);

            add(label("Garanti Notu"), "gaptop 6");
            warrantyNote = textArea("", 3);
            add(scroll(warrantyNote), "hmin 70");
            refreshWarrantyNote();

            // Gün değişince not yeniden yazılır; kullanıcı notu elle değiştirdiyse dokunulmaz.
            warrantySpinner.addChangeListener(e -> {
                if (!warrantyNoteEdited) refreshWarrantyNote();
            });
            warrantyNote.getDocument().addDocumentListener(new DocumentListener() {
                private void edited() {
                    if (!updatingWarrantyNote) warrantyNoteEdited = true;
                }
                @Override public void insertUpdate(DocumentEvent e) { edited(); }
                @Override public void removeUpdate(DocumentEvent e) { edited(); }
                @Override public void changedUpdate(DocumentEvent e) { }
            });
        }

        private void refreshWarrantyNote() {
            updatingWarrantyNote = true;
            try {
                warrantyNote.setText(DocumentTexts.warrantyNote(days(),
                        DocumentTexts.get(DocumentText.DELIVERY_WARRANTY_NOTE),
                        DocumentTexts.get(DocumentText.DELIVERY_NO_WARRANTY_NOTE)));
                warrantyNote.setCaretPosition(0);
            } finally {
                updatingWarrantyNote = false;
            }
        }

        private int days() {
            return ((Number) warrantySpinner.getValue()).intValue();
        }

        DocumentRequest collect() {
            DocumentRequest request = DocumentRequest.defaults();
            if (leftSigner != null) {
                request.setLeftSignerName(leftSigner.getText().trim());
                request.setRightSignerName(rightSigner.getText().trim());
            }
            for (Map.Entry<DocumentText, JTextComponent> entry : textFields.entrySet()) {
                request.setText(entry.getKey(), entry.getValue().getText().strip());
            }
            if (warrantySpinner != null) {
                request.setWarrantyDays(days());
                request.setText(DocumentText.DELIVERY_WARRANTY_NOTE, warrantyNote.getText().strip());
            }
            if (saveAsDefault != null && saveAsDefault.isSelected()) {
                saveDefaults();
            }
            return request;
        }

        private void saveDefaults() {
            for (Map.Entry<DocumentText, JTextComponent> entry : textFields.entrySet()) {
                DocumentTexts.set(entry.getKey(), entry.getValue().getText());
            }
            if (warrantySpinner != null) {
                int days = days();
                DocumentTexts.setWarrantyDays(days);
                if (warrantyNoteEdited) {
                    String note = warrantyNote.getText().strip();
                    if (days > 0) {
                        // Notu şablona çevir: gün sayısı yer tutucuya döner, sonraki belgede güne göre dolar.
                        Matcher m = Pattern.compile("\\b" + days + "\\b").matcher(note);
                        DocumentTexts.set(DocumentText.DELIVERY_WARRANTY_NOTE, m.find() ? m.replaceFirst("{gün}") : note);
                    } else {
                        DocumentTexts.set(DocumentText.DELIVERY_NO_WARRANTY_NOTE, note);
                    }
                }
            }
            Toasts.show(this, Toast.Type.INFO, Messages.get("toast.document.textsSaved"));
        }

        private static JLabel label(String text) {
            JLabel l = new JLabel(text);
            l.putClientProperty(FlatClientProperties.STYLE, "font: -1 bold");
            return l;
        }

        private static JTextArea textArea(String text, int rows) {
            JTextArea area = new JTextArea(text, rows, 40);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
            area.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
            area.setCaretPosition(0);
            return area;
        }

        private static JScrollPane scroll(JTextArea area) {
            JScrollPane sp = new JScrollPane(area);
            sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            return sp;
        }

        private static String nvl(String s) {
            return s != null ? s : "";
        }
    }
}
