UPDATE rule_versions SET approved_by = 'system-approver' WHERE author = 'system' AND approved_by = 'system';

ALTER TABLE rule_versions
    ADD CONSTRAINT rule_versions_maker_checker CHECK (approved_by IS NULL OR approved_by <> author);
