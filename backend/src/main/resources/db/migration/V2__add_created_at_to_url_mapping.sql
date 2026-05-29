ALTER TABLE url_mapping
ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE url_mapping
DROP COLUMN created_at;

ALTER TABLE url_mapping
RENAME COLUMN created_date TO created_at;