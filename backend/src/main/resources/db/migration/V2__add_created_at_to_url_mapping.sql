-- Renames the legacy created_date column to created_at to match the JPA entity (UrlMapping.createdAt).
ALTER TABLE url_mapping
    RENAME COLUMN created_date TO created_at;
