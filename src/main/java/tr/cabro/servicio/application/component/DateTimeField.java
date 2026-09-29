package tr.cabro.servicio.application.component;

import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import raven.datetime.TimePicker;

import javax.swing.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Tarih + saat girişi: solda takvimli tarih alanı, sağda saat alanı (24 saat). Durum tarihleri
 * gibi "ne zaman oldu" sorusu içindir; bu yüzden takvimde yarından sonrası seçilemez ve
 * isteğe bağlı bir alt sınır ({@link #setEarliest}) verilebilir. Sıra kuralının kesin kontrolü
 * service katmanındadır; burada yalnızca yanlış günü seçmek zorlaştırılır.
 */
public class DateTimeField extends JPanel {

    private final JFormattedTextField dateEditor = new JFormattedTextField();
    private final JFormattedTextField timeEditor = new JFormattedTextField();
    private final DatePicker datePicker = new DatePicker();
    private final TimePicker timePicker = new TimePicker();
    private LocalDate earliest;

    private final boolean withTime;

    public DateTimeField() {
        this(true);
    }

    /**
     * @param withTime false ise yalnızca tarih seçilir (saat alanı gösterilmez); {@link #getValue}
     *                 günün başını döner, saati çağıran belirler (ör. tahsilat: bugünse şu an).
     */
    public DateTimeField(boolean withTime) {
        super(new MigLayout("insets 0, gap 8, fillx", withTime ? "[grow 65, fill][grow 35, fill]" : "[grow, fill]", "[]"));
        this.withTime = withTime;
        setOpaque(false);

        datePicker.setDateFormat("dd/MM/yyyy");
        datePicker.setCloseAfterSelected(true);
        datePicker.setEditor(dateEditor);
        datePicker.setDateSelectionAble(date -> !date.isAfter(LocalDate.now())
                && (earliest == null || !date.isBefore(earliest)));
        dateEditor.getAccessibleContext().setAccessibleName("Tarih");

        timePicker.set24HourView(true);
        timePicker.setEditor(timeEditor);
        timeEditor.getAccessibleContext().setAccessibleName("Saat");

        add(dateEditor, "wmin 0");
        if (withTime) add(timeEditor, "wmin 0");
        setValue(LocalDateTime.now());
    }

    /** Dakikaya yuvarlanır; saniye girişi yok. */
    public void setValue(LocalDateTime value) {
        LocalDateTime v = value != null ? value.truncatedTo(ChronoUnit.MINUTES) : LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        datePicker.setSelectedDate(v.toLocalDate());
        timePicker.setSelectedTime(v.toLocalTime());
    }

    /** Tarih seçilmediyse {@code null}; saat boşsa gün başı kabul edilir. */
    public LocalDateTime getValue() {
        LocalDate date = datePicker.getSelectedDate();
        if (date == null) return null;
        LocalTime time = withTime && timePicker.isTimeSelected() ? timePicker.getSelectedTime() : LocalTime.MIDNIGHT;
        return LocalDateTime.of(date, time.truncatedTo(ChronoUnit.MINUTES));
    }

    /** Takvimde bu günden önceki günler seçilemez; {@code null} sınırı kaldırır. */
    public void setEarliest(LocalDateTime earliest) {
        this.earliest = earliest != null ? earliest.toLocalDate() : null;
    }

    /** Pencere açılınca odak tarih alanına. */
    public JComponent focusTarget() {
        return dateEditor;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        datePicker.setEnabled(enabled);
        timePicker.setEnabled(enabled);
        dateEditor.setEnabled(enabled);
        timeEditor.setEnabled(enabled);
    }
}
