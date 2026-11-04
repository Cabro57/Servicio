-- =========================================================================
-- V30__user_recovery_code.sql
-- PIN kurtarma kodu: PIN unutulduğunda kilit ekranından yeni PIN belirlemek için.
-- Kod düz metin tutulmaz; PIN gibi bcrypt özeti saklanır. NULL = kod oluşturulmamış.
-- =========================================================================

ALTER TABLE users ADD COLUMN recovery_code TEXT NULL;
