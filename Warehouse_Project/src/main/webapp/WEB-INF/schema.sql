CREATE DATABASE IF NOT EXISTS warehouse_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE warehouse_db;

CREATE TABLE IF NOT EXISTS admin_credentials (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    admin_name VARCHAR(255),
    phone VARCHAR(15)
);

CREATE TABLE IF NOT EXISTS customers (
    customer_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(255),
    address VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS products (
    product_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(255),
    sku VARCHAR(255),
    price DOUBLE NOT NULL,
    quantity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    order_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(255),
    amount DOUBLE NOT NULL,
    status VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS inventory (
    inventory_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(255),
    quantity INT NOT NULL,
    location VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS warehouses (
    warehouse_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    location VARCHAR(255),
    capacity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS dispatches (
    dispatch_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT,
    carrier VARCHAR(255),
    tracking_number VARCHAR(255),
    status VARCHAR(255)
);
