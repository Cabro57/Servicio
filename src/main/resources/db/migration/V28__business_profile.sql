-- =========================================================================
-- V28__business_profile.sql
-- İşletme bilgileri kullanıcı satırından ayrılır. Eskiden işletme adı, telefonu, adresi ve
-- logosu users tablosundaki tek satırda, kişinin adı ve PIN'iyle birlikte duruyordu; çoklu
-- kullanıcıya geçildiğinde işletme kişiye bağlı kalamaz.
--
-- business_profile tek satırlık bir tablodur (id her zaman 1). Belgelerin antedi, termal fiş ve
-- WhatsApp şablonları bilgiyi buradan okur. Yeni alanlar: ikinci telefon, işletme e-postası,
-- web sitesi, vergi dairesi/numarası, IBAN, banka, hesap sahibi ve çalışma saatleri.
--
-- Mevcut değerler users(id=1)'den taşınır. Belgeler e-posta olarak kullanıcının e-postasını
-- basıyordu; bu da işletme e-postasına kopyalanır ki antet değişmesin. users tablosundaki eski
-- kolonlar silinmez (geri dönüş ve eski yedeklerin okunabilmesi için); artık yazılmaz.
-- =========================================================================

CREATE TABLE business_profile (
    id             INTEGER PRIMARY KEY CHECK (id = 1),
    business_name  VARCHAR(255),
    phone_number   VARCHAR(50),
    phone_number2  VARCHAR(50),
    email          VARCHAR(255),
    website        VARCHAR(255),
    address        VARCHAR(500),
    tax_office     VARCHAR(255),
    tax_number     VARCHAR(50),
    iban           VARCHAR(50),
    bank_name      VARCHAR(255),
    account_holder VARCHAR(255),
    working_hours  VARCHAR(255),
    logo_path      VARCHAR(255),
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO business_profile (id, business_name, phone_number, email, address, logo_path)
SELECT 1, business_name, phone_number, NULLIF(email, ''), address, logo_path
FROM users
WHERE id = 1;
