-- =========================================================================
-- V11__reduce_customer_type_and_problematic_flag.sql
-- CustomerType 6 değerden 2'ye indiriliyor (BIREYSEL / KURUMSAL). Eski
-- "dikkat edilmesi gereken" anlamındaki değerler (BE_CAREFUL, DOING_BUSINESS,
-- PROBLEM) artık ayrı bir boolean bayrağa (is_problematic) taşınıyor.
-- =========================================================================

-- 1) Yeni "sorunlu müşteri" bayrağı
ALTER TABLE customers ADD COLUMN is_problematic BOOLEAN NOT NULL DEFAULT 0;

-- 2) Eski değerlerden bayrağı doldur
UPDATE customers SET is_problematic = 1
WHERE customer_type IN ('BE_CAREFUL', 'DOING_BUSINESS', 'PROBLEM');

-- 3) customer_type'ı yeni 2 değere indirge
UPDATE customers SET customer_type = 'KURUMSAL'
WHERE customer_type IN ('SMALL_BUSINESS', 'DEALER');

UPDATE customers SET customer_type = 'BIREYSEL'
WHERE customer_type IS NULL OR customer_type NOT IN ('KURUMSAL');
