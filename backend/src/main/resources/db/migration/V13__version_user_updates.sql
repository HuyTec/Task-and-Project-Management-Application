-- Reject stale writes from profile, login/linking or administrative operations.
ALTER TABLE users ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
