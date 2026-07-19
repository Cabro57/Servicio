package tr.cabro.servicio.application.system;

import raven.modal.ModalDialog;
import raven.modal.component.Modal;
import raven.modal.option.Option;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tüm ekranların modal açma/kapatma çağrılarının geçtiği tek merkezi nokta.
 * {@link ModalDialog}'u doğrudan sarmalar (imzalar birebir aynı), ek olarak hangi
 * modalların açık olduğunu (parent + içerik + varsa üzerine push edilmiş alt-modaller)
 * kaydeder. Bu sayede otomatik kilit ekranı, kilit anında açık olan modalı gerçekten
 * kapatabilir ve kilit açılınca AYNI panel/nesne ile (yazılmış veriler dahil) yeniden
 * açabilir — çünkü bir bileşenin ekrandan kaldırılması onu Java nesnesi olarak yok etmez.
 */
public class AppModal {

    public static class ModalRecord {
        final String id;
        final Component parent;
        final Modal modal;
        final Option option;
        final List<Modal> pushedStack = new ArrayList<>();

        ModalRecord(String id, Component parent, Modal modal, Option option) {
            this.id = id;
            this.parent = parent;
            this.modal = modal;
            this.option = option;
        }
    }

    private static final Map<String, ModalRecord> OPEN = new LinkedHashMap<>();

    public static void showModal(Component parent, Modal modal, String id) {
        OPEN.put(id, new ModalRecord(id, parent, modal, null));
        ModalDialog.showModal(parent, modal, id);
    }

    public static void showModal(Component parent, Modal modal, Option option, String id) {
        OPEN.put(id, new ModalRecord(id, parent, modal, option));
        ModalDialog.showModal(parent, modal, option, id);
    }

    public static void pushModal(Modal modal, String rootId) {
        ModalRecord record = OPEN.get(rootId);
        if (record != null) record.pushedStack.add(modal);
        ModalDialog.pushModal(modal, rootId);
    }

    /**
     * Bir modalın içinden ikinci bir modal açarken kullanılır (ör. "Yeni Müşteri Ekle"
     * butonu bir hücre editörünü/butonu kapatırken aynı anda yeni panel kurup üzerine
     * modal push ediyor). Panel kurulumu + push işlemi tetikleyen olayla (hücre editörü
     * iptali, buton tıklaması) AYNI EDT event'inde yapılırsa, iki ağır Swing işlemi
     * arka arkaya biriktiği için ekran anlık donuyor. {@code modalSupplier} bir sonraki
     * EDT döngüsüne ertelenir; böylece tetikleyen olay önce tamamlanıp ekrana yansır,
     * ardından yeni panel kurulup modal push edilir.
     */
    public static void pushModalDeferred(Supplier<Modal> modalSupplier, String rootId) {
        SwingUtilities.invokeLater(() -> pushModal(modalSupplier.get(), rootId));
    }

    public static void popModal(String rootId) {
        ModalRecord record = OPEN.get(rootId);
        if (record != null && !record.pushedStack.isEmpty()) {
            record.pushedStack.remove(record.pushedStack.size() - 1);
        }
        ModalDialog.popModal(rootId);
    }

    public static void closeModal(String id) {
        OPEN.remove(id);
        ModalDialog.closeModal(id);
    }

    /**
     * Kilitlenirken çağrılır: kayıtlı id'lerden hâlâ gerçekten açık olanları
     * (kütüphaneye {@code isIdExist} ile sorularak) yakalar ve kendi kaydını temizler.
     * Kullanıcı bir modalı normal yoldan kapatıp gitmişse, o kayıt zaten burada
     * sessizce elenir (isIdExist false döner).
     */
    public static List<ModalRecord> captureOpenAndClear() {
        List<ModalRecord> stillOpen = new ArrayList<>();
        for (ModalRecord record : OPEN.values()) {
            if (ModalDialog.isIdExist(record.id)) {
                stillOpen.add(record);
            }
        }
        OPEN.clear();
        return stillOpen;
    }

    /**
     * Kilit açılınca çağrılır: yakalanan modalları aynı sırayla, aynı nesnelerle
     * (dolayısıyla kullanıcının yazdığı verilerle) yeniden açar.
     */
    public static void reopen(List<ModalRecord> records) {
        for (ModalRecord record : records) {
            if (record.option != null) {
                showModal(record.parent, record.modal, record.option, record.id);
            } else {
                showModal(record.parent, record.modal, record.id);
            }
            ModalRecord fresh = OPEN.get(record.id);
            for (Modal pushed : record.pushedStack) {
                if (fresh != null) fresh.pushedStack.add(pushed);
                ModalDialog.pushModal(pushed, record.id);
            }
        }
    }
}
