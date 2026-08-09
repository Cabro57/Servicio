-- [DÜZELTİLDİ] V7'de work_orders tablosu yeniden oluşturulurken (services'ten ayrılırken)
-- detected_fault kolonu unutuldu; kod (WorkOrderRepository.updateDetectedFault) hâlâ
-- bu kolonu varsayıyordu ve "no such column: detected_fault" hatası veriyordu.
ALTER TABLE work_orders ADD COLUMN detected_fault TEXT;
