-- =========================================================================
-- V12__document_templates.sql
-- PDF form (belge) ve WhatsApp mesaj şablonları için tek tablo — iki tür de
-- yapısal olarak aynı (ad + gövde metni), work_order_items.item_type
-- desenindeki gibi tek tabloda type ayırıcısıyla tutuluyor.
-- =========================================================================

CREATE TABLE document_templates (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    template_type VARCHAR(20)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    body          TEXT         NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_templates_type ON document_templates(template_type);

-- Kullanıcı hızlı başlasın diye örnek şablonlar
INSERT INTO document_templates (template_type, name, body) VALUES
('FORM', 'Cihaz Kabul Formu',
 'Müşteri {musteri_adi}, {cihaz_bilgisi} cihazını aşağıdaki arıza şikayeti ile servisimize teslim etmiştir: "{ariza_aciklamasi}". Cihaz incelenip tamir sürecine alınacaktır. Teslim alınan cihazda oluşabilecek veri kaybından işletmemiz sorumlu değildir.'),
('WHATSAPP_MESSAGE', 'Servis Hazır Bildirimi',
 'Merhaba {musteri_adi}, {servis_no} numaralı servisiniz hazır durumda. Kalan bakiye: {kalan_tutar}. Cihazınızı teslim alabilirsiniz.');

-- İşletme adresi — PDF üst bilgi bloğunda kullanılacak (opsiyonel alan)
ALTER TABLE users ADD COLUMN address VARCHAR(255);
