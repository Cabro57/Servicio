-- =========================================================================
-- V23__work_order_status_changed_at.sql
-- Ana sayfadaki "N gündür hazır / bekliyor" bilgisi updated_at'ten hesaplanıyordu; updated_at
-- her düzenlemede (arıza notu, şikâyet vb.) değiştiği için durumun ne zamandan beri sürdüğünü
-- göstermiyordu. Durumun son değiştiği an artık ayrı tutulur.
-- Mevcut kayıtlarda bu an bilinmiyor: yalnızca teslim/iade tarihi olanlar doldurulur, diğerleri
-- NULL kalır (arayüz bu durumda geliş tarihinden "gündür serviste" gösterir).
-- =========================================================================

ALTER TABLE work_orders ADD COLUMN status_changed_at TIMESTAMP NULL;

UPDATE work_orders
SET status_changed_at = delivery_date
WHERE delivery_date IS NOT NULL
  AND service_status IN ('DELIVERED', 'RETURN');
