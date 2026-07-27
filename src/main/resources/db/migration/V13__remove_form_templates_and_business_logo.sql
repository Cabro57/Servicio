-- =========================================================================
-- V13__remove_form_templates_and_business_logo.sql
-- PDF form şablonları artık kod-tabanlı sabit form tipleriyle üretiliyor
-- (document_templates.template_type = 'FORM' kayıtları artık kullanılmıyor).
-- TemplateType enum'undan FORM değeri kaldırıldığı için mevcut satırlar
-- @EnumByName okumasında hataya yol açar; bu yüzden önce siliniyor.
-- =========================================================================

DELETE FROM document_templates WHERE template_type = 'FORM';

-- İşletme logosu — PDF üst bilgi bloğunda (antetli kağıt gibi) opsiyonel kullanılacak
ALTER TABLE users ADD COLUMN logo_path VARCHAR(255);
