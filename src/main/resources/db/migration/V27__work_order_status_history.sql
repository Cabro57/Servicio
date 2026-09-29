-- =========================================================================
-- V27__work_order_status_history.sql
-- Servis durum tarihleri artık tek kaynaktan, durum geçmişinden okunur:
--   * Teslim alma   = ACCEPTED ("Kabul Edildi") satırı
--   * Tamire başlama = ilk UNDER_REPAIR satırı
--   * Hazır olma     = son READY satırı
--   * Teslim/iade    = son DELIVERED/RETURN satırı
--   * Son durum değişikliği = en son satır
-- work_orders.delivery_date ve status_changed_at bu yüzden kaldırılır; aynı tarihin iki yerde
-- tutulması senkron hatalarına açıktı (eski akış başka duruma geçince teslim tarihini siliyordu).
-- Okuma tarafı türetilmiş tarihleri v_work_orders view'ından alır.
--
-- Mevcut kayıtlarda ara geçişler bilinmiyor: her kayda bir ACCEPTED satırı (created_at) ve
-- mevcut durumu için bir satır (teslim tarihi > son durum değişikliği > created_at) yazılır.
-- =========================================================================

CREATE TABLE work_order_status_history (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    work_order_id INTEGER     NOT NULL,
    status        VARCHAR(20) NOT NULL,
    changed_at    TIMESTAMP   NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (work_order_id) REFERENCES work_orders(id) ON DELETE CASCADE
);

CREATE INDEX idx_work_order_status_history_lookup
    ON work_order_status_history (work_order_id, status, changed_at);

-- Eski sürümlerden kalma kayıtlarda created_at NULL olabiliyor: teslim alma anı bilinen ilk
-- tarihten alınır. Tarihler karşılaştırılırken metin değil julianday() kullanılır; kayıtlarda
-- hem "2026-07-18T14:43:02" hem "2026-07-18 14:43:02" biçimi var (MAX() ayrıca NULL'da NULL döner).
INSERT INTO work_order_status_history (work_order_id, status, changed_at, created_at)
SELECT id,
       'ACCEPTED',
       COALESCE(created_at, status_changed_at, delivery_date, updated_at, CURRENT_TIMESTAMP),
       CURRENT_TIMESTAMP
FROM work_orders;

INSERT INTO work_order_status_history (work_order_id, status, changed_at, created_at)
SELECT id,
       service_status,
       -- Geçmiş satırı teslim alma anından önce olamaz.
       CASE WHEN julianday(changed) < julianday(received) THEN received ELSE changed END,
       CURRENT_TIMESTAMP
FROM (SELECT id,
             service_status,
             COALESCE(created_at, status_changed_at, delivery_date, updated_at, CURRENT_TIMESTAMP) AS received,
             COALESCE(CASE WHEN service_status IN ('DELIVERED', 'RETURN') THEN delivery_date END,
                      status_changed_at, created_at, updated_at, CURRENT_TIMESTAMP)          AS changed
      FROM work_orders
      WHERE service_status IS NOT NULL
        AND service_status <> 'ACCEPTED');

ALTER TABLE work_orders DROP COLUMN delivery_date;
ALTER TABLE work_orders DROP COLUMN status_changed_at;

-- Servis kaydı + geçmişten türetilen tarihler. Yazma her zaman work_orders ve
-- work_order_status_history tablolarına yapılır; bu view yalnızca okuma içindir.
CREATE VIEW v_work_orders AS
SELECT wo.*,
       COALESCE((SELECT MIN(h.changed_at) FROM work_order_status_history h
                 WHERE h.work_order_id = wo.id AND h.status = 'ACCEPTED'), wo.created_at) AS received_at,
       (SELECT MIN(h.changed_at) FROM work_order_status_history h
        WHERE h.work_order_id = wo.id AND h.status = 'UNDER_REPAIR') AS repair_started_at,
       (SELECT MAX(h.changed_at) FROM work_order_status_history h
        WHERE h.work_order_id = wo.id AND h.status = 'READY') AS ready_at,
       CASE WHEN wo.service_status IN ('DELIVERED', 'RETURN')
            THEN (SELECT MAX(h.changed_at) FROM work_order_status_history h
                  WHERE h.work_order_id = wo.id AND h.status = wo.service_status)
       END AS delivery_date,
       (SELECT MAX(h.changed_at) FROM work_order_status_history h
        WHERE h.work_order_id = wo.id) AS status_changed_at
FROM work_orders wo;
