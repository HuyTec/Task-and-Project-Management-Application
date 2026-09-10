-- Existing sessions remain valid until the first security change.
ALTER TABLE users ADD COLUMN security_stamp VARCHAR(36);
