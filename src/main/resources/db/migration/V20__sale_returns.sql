-- =========================================================================
-- V20__sale_returns.sql
-- İade akışı için: bir iade kaleminin hangi orijinal satış kalemine karşılık
-- geldiğini izler. "Aynı kalem iki kez iade edilemez" kontrolü bu bağ
-- üzerinden yapılır (bkz. SaleService.recordReturn) — part_id/miktar
-- eşleştirmesi aynı üründen iki ayrı satır varsa belirsiz kalırdı.
-- =========================================================================

ALTER TABLE sale_items ADD COLUMN source_sale_item_id INTEGER NULL REFERENCES sale_items(id) ON DELETE RESTRICT;

CREATE INDEX idx_sale_items_source ON sale_items(source_sale_item_id);
