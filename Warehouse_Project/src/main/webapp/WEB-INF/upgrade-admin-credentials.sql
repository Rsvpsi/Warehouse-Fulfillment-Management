USE warehouse_db;

ALTER TABLE admin_credentials
    ADD COLUMN admin_name VARCHAR(255),
    ADD COLUMN phone VARCHAR(15);
