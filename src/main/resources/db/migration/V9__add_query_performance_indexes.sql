-- =========================================================================
-- V9__add_query_performance_indexes.sql
-- Sık filtrelenen/JOIN edilen kolonlara eksik index'lerin eklenmesi
-- =========================================================================

-- Parça arama/filtreleme ekranlarında kategori, tedarikçi ve depo bazlı sorgular tam tablo taraması yapıyordu
CREATE INDEX IF NOT EXISTS idx_parts_category ON parts(category);
CREATE INDEX IF NOT EXISTS idx_parts_supplier ON parts(supplier_id);
CREATE INDEX IF NOT EXISTS idx_parts_warehouse ON parts(warehouse_id);

-- ReportRepository'deki cihaz türü/marka dağılım grafikleri devices tablosunu JOIN ediyor
CREATE INDEX IF NOT EXISTS idx_devices_type ON devices(device_type_id);
CREATE INDEX IF NOT EXISTS idx_devices_brand ON devices(brand_id);

-- İş emri durum filtreleri (findByStatuses/findByStatusesExcluded) ve tarih aralıklı raporlar için
CREATE INDEX IF NOT EXISTS idx_work_orders_status ON work_orders(service_status);
CREATE INDEX IF NOT EXISTS idx_work_orders_created ON work_orders(created_at);

-- V8 ile eklenen cihaz türüne göre işçilik filtrelemesi (LaborRepository.findByTypeId)
CREATE INDEX IF NOT EXISTS idx_labors_device_type ON labors(device_type_id);

-- WorkOrderRepository.findByPartId JOIN'i için
CREATE INDEX IF NOT EXISTS idx_work_order_items_part ON work_order_items(part_id);
