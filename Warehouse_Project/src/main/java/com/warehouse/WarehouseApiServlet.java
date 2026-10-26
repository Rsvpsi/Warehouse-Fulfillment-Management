package com.warehouse;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/api/*")
public class WarehouseApiServlet extends HttpServlet {

    private record Resource(String selectSql, String insertSql, String updateSql, String deleteSql,
                            String[] columns, String[] parameters) {
    }

    private static final Resource PRODUCTS = new Resource(
            "SELECT product_id, product_name, sku, price, quantity FROM products ORDER BY product_id",
            "INSERT INTO products (product_name, sku, price, quantity) VALUES (?, ?, ?, ?)",
            "UPDATE products SET product_name = ?, sku = ?, price = ?, quantity = ? WHERE product_id = ?",
            "DELETE FROM products WHERE product_id = ?",
            new String[]{"id", "name", "sku", "price", "quantity"},
            new String[]{"name", "sku", "price", "quantity"});
    private static final Resource ORDERS = new Resource(
            "SELECT order_id, customer_name, amount, status FROM orders ORDER BY order_id",
            "INSERT INTO orders (customer_name, amount, status) VALUES (?, ?, ?)",
            "UPDATE orders SET customer_name = ?, amount = ?, status = ? WHERE order_id = ?",
            "DELETE FROM orders WHERE order_id = ?",
            new String[]{"id", "customer", "amount", "status"},
            new String[]{"customer", "amount", "status"});
    private static final Resource INVENTORY = new Resource(
            "SELECT inventory_id, product_name, quantity, location FROM inventory ORDER BY inventory_id",
            "INSERT INTO inventory (product_name, quantity, location) VALUES (?, ?, ?)",
            "UPDATE inventory SET product_name = ?, quantity = ?, location = ? WHERE inventory_id = ?",
            "DELETE FROM inventory WHERE inventory_id = ?",
            new String[]{"id", "product", "quantity", "location"},
            new String[]{"product", "quantity", "location"});
    private static final Resource CUSTOMERS = new Resource(
            "SELECT customer_id, name, email, phone, address FROM customers ORDER BY customer_id",
            "INSERT INTO customers (name, email, phone, address) VALUES (?, ?, ?, ?)",
            "UPDATE customers SET name = ?, email = ?, phone = ?, address = ? WHERE customer_id = ?",
            "DELETE FROM customers WHERE customer_id = ?",
            new String[]{"id", "name", "email", "phone", "address"},
            new String[]{"name", "email", "phone", "address"});
    private static final Resource WAREHOUSES = new Resource(
            "SELECT warehouse_id, name, location, capacity FROM warehouses ORDER BY warehouse_id",
            "INSERT INTO warehouses (name, location, capacity) VALUES (?, ?, ?)",
            "UPDATE warehouses SET name = ?, location = ?, capacity = ? WHERE warehouse_id = ?",
            "DELETE FROM warehouses WHERE warehouse_id = ?",
            new String[]{"id", "name", "location", "capacity"},
            new String[]{"name", "location", "capacity"});
    private static final Resource DISPATCHES = new Resource(
            "SELECT dispatch_id, order_id, carrier, tracking_number, status FROM dispatches ORDER BY dispatch_id",
            "INSERT INTO dispatches (order_id, carrier, tracking_number, status) VALUES (?, ?, ?, ?)",
            "UPDATE dispatches SET order_id = ?, carrier = ?, tracking_number = ?, status = ? WHERE dispatch_id = ?",
            "DELETE FROM dispatches WHERE dispatch_id = ?",
            new String[]{"id", "orderId", "carrier", "tracking", "status"},
            new String[]{"orderId", "carrier", "tracking", "status"});

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        switch (path) {
            case "/dashboard/summary" -> {
                writeDashboardSummary(response);
                return;
            }
            case "/inventory/low-stock" -> {
                writeLowStock(request, response);
                return;
            }
            case "/orders/recent" -> {
                writeRecentOrders(request, response);
                return;
            }
            case "/dispatches/pending" -> {
                writePendingDispatches(response);
                return;
            }
            case "/products/low-stock" -> {
                writeLowStockProducts(request, response);
                return;
            }
            case "/orders/by-status" -> {
                writeOrdersByStatus(request, response);
                return;
            }
            case "/inventory/by-location" -> {
                writeInventoryByLocation(request, response);
                return;
            }
            default -> {
            }
        }

        Resource resource = resource(request, response);
        if (resource == null) {
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(resource.selectSql());
             ResultSet rows = statement.executeQuery()) {
            writeRows(response, rows, resource.columns());
        } catch (SQLException | RuntimeException exception) {
            log("Failed to load " + request.getPathInfo(), exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not load records. Check the database configuration.");
        }
    }

    private void writeDashboardSummary(HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String sql = "SELECT (SELECT COUNT(*) FROM products) AS products, " +
                "(SELECT COUNT(*) FROM orders) AS orders, " +
                "(SELECT COUNT(*) FROM customers) AS customers, " +
                "(SELECT COUNT(*) FROM inventory) AS inventoryItems, " +
                "(SELECT COALESCE(SUM(quantity), 0) FROM inventory) AS totalUnits, " +
                "(SELECT COUNT(*) FROM inventory WHERE quantity <= ?) AS lowStockItems, " +
                "(SELECT COUNT(*) FROM dispatches WHERE LOWER(status) = 'pending') AS pendingDispatches, " +
                "(SELECT COALESCE(SUM(amount), 0) FROM orders) AS orderRevenue";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, DEFAULT_LOW_STOCK_THRESHOLD);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("Dashboard summary query returned no rows.");
                }
                response.getWriter().print("{\"lowStockThreshold\":" + DEFAULT_LOW_STOCK_THRESHOLD
                        + ",\"products\":" + row.getLong("products")
                        + ",\"orders\":" + row.getLong("orders")
                        + ",\"customers\":" + row.getLong("customers")
                        + ",\"inventoryItems\":" + row.getLong("inventoryItems")
                        + ",\"totalUnits\":" + row.getLong("totalUnits")
                        + ",\"lowStockItems\":" + row.getLong("lowStockItems")
                        + ",\"pendingDispatches\":" + row.getLong("pendingDispatches")
                        + ",\"orderRevenue\":" + row.getBigDecimal("orderRevenue") + "}");
            }
        } catch (SQLException | RuntimeException exception) {
            log("Failed to load dashboard summary.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not load the dashboard summary. Check the database configuration.");
        }
    }

    private void writeLowStock(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int threshold;
        try {
            threshold = nonNegativeInteger(request.getParameter("threshold"),
                    DEFAULT_LOW_STOCK_THRESHOLD);
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The threshold must be a non-negative whole number.");
            return;
        }
        writeFilteredRows(response,
                "SELECT inventory_id, product_name, quantity, location FROM inventory " +
                        "WHERE quantity <= ? ORDER BY quantity, inventory_id",
                new String[]{"id", "product", "quantity", "location"},
                threshold, "low-stock inventory");
    }

    private void writeRecentOrders(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int limit;
        try {
            limit = boundedInteger(request.getParameter("limit"), 10, 1, 100);
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The limit must be a whole number between 1 and 100.");
            return;
        }
        writeFilteredRows(response,
                "SELECT order_id, customer_name, amount, status FROM orders " +
                        "ORDER BY order_id DESC LIMIT ?",
                new String[]{"id", "customer", "amount", "status"},
                limit, "recent orders");
    }

    private void writePendingDispatches(HttpServletResponse response) throws IOException {
        writeFilteredRows(response,
                "SELECT dispatch_id, order_id, carrier, tracking_number, status FROM dispatches " +
                        "WHERE LOWER(status) = 'pending' ORDER BY dispatch_id",
                new String[]{"id", "orderId", "carrier", "tracking", "status"},
                null, "pending dispatches");
    }

    private void writeLowStockProducts(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        int threshold;
        try {
            threshold = nonNegativeInteger(request.getParameter("threshold"),
                    DEFAULT_LOW_STOCK_THRESHOLD);
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The threshold must be a non-negative whole number.");
            return;
        }
        writeFilteredRows(response,
                "SELECT product_id, product_name, sku, price, quantity FROM products " +
                        "WHERE quantity <= ? ORDER BY quantity, product_id",
                new String[]{"id", "name", "sku", "price", "quantity"},
                threshold, "low-stock products");
    }

    private void writeOrdersByStatus(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String status = request.getParameter("status");
        if (status == null || status.isBlank()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "The status parameter is required.");
            return;
        }
        writeFilteredTextRows(response,
                "SELECT order_id, customer_name, amount, status FROM orders " +
                        "WHERE LOWER(status) = LOWER(?) ORDER BY order_id DESC",
                new String[]{"id", "customer", "amount", "status"},
                status.trim(), "orders by status");
    }

    private void writeInventoryByLocation(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String location = request.getParameter("location");
        if (location == null || location.isBlank()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "The location parameter is required.");
            return;
        }
        writeFilteredTextRows(response,
                "SELECT inventory_id, product_name, quantity, location FROM inventory " +
                        "WHERE location = ? ORDER BY inventory_id",
                new String[]{"id", "product", "quantity", "location"},
                location.trim(), "inventory by location");
    }

    private void writeFilteredRows(HttpServletResponse response, String sql, String[] columns,
                                   Integer parameter, String description) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setInt(1, parameter);
            }
            try (ResultSet rows = statement.executeQuery()) {
                writeRows(response, rows, columns);
            }
        } catch (SQLException | RuntimeException exception) {
            log("Failed to load " + description + ".", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not load " + description + ". Check the database configuration.");
        }
    }

    private void writeFilteredTextRows(HttpServletResponse response, String sql, String[] columns,
                                       String parameter, String description) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                writeRows(response, rows, columns);
            }
        } catch (SQLException | RuntimeException exception) {
            log("Failed to load " + description + ".", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not load " + description + ". Check the database configuration.");
        }
    }

    private static void writeRows(HttpServletResponse response, ResultSet rows, String[] columns)
            throws SQLException, IOException {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (rows.next()) {
            if (!first) {
                json.append(',');
            }
            json.append('{');
            for (int index = 0; index < columns.length; index++) {
                if (index > 0) {
                    json.append(',');
                }
                json.append('"').append(columns[index]).append("\":");
                Object value = rows.getObject(index + 1);
                if (value instanceof Number || value instanceof Boolean) {
                    json.append(value);
                } else if (value == null) {
                    json.append("null");
                } else {
                    appendJsonString(json, value.toString());
                }
            }
            json.append('}');
            first = false;
        }
        response.getWriter().print(json.append(']'));
    }

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    private static int nonNegativeInteger(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new NumberFormatException();
        }
        return parsed;
    }

    private static int boundedInteger(String value, int defaultValue, int minimum, int maximum) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed < minimum || parsed > maximum) {
            throw new NumberFormatException();
        }
        return parsed;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Resource resource = resource(request, response);
        if (resource == null) {
            return;
        }
        if (request.getParameter("id") != null) {
            updateRecord(request, response, resource);
            return;
        }

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(resource.insertSql())) {
            if (!bindParameters(statement, request, response, resource)) {
                return;
            }

            if (statement.executeUpdate() != 1) {
                sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "The record was not saved.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print("{\"saved\":true}");
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a valid number for the amount, quantity, capacity, or order ID.");
        } catch (SQLException | RuntimeException exception) {
            log("Failed to save " + request.getPathInfo(), exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not save the record. Check the submitted values and database.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Resource resource = resource(request, response);
        if (resource == null) {
            return;
        }

        updateRecord(request, response, resource);
    }

    private void updateRecord(HttpServletRequest request, HttpServletResponse response, Resource resource)
            throws IOException {
        long id;
        try {
            id = positiveLong(request.getParameter("id"));
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "A valid record ID is required.");
            return;
        }

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(resource.updateSql())) {
            if (!bindParameters(statement, request, response, resource)) {
                return;
            }
            statement.setLong(resource.parameters().length + 1, id);
            if (statement.executeUpdate() == 0) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Record not found.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter valid numeric values. Amount, quantity, capacity, and order ID cannot be negative.");
        } catch (SQLException | RuntimeException exception) {
            log("Failed to update record " + id + " in " + request.getPathInfo(), exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not update the record. Check the submitted values and database.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Resource resource = resource(request, response);
        if (resource == null) {
            return;
        }

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
            if (id < 1) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "A valid record ID is required.");
            return;
        }

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(resource.deleteSql())) {
            statement.setLong(1, id);
            if (statement.executeUpdate() == 0) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Record not found.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (SQLException | RuntimeException exception) {
            log("Failed to delete record " + id + " from " + request.getPathInfo(), exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not delete the record. It may still be referenced by another record.");
        }
    }

    private Resource resource(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        return switch (request.getPathInfo() == null ? "" : request.getPathInfo()) {
            case "/products" -> PRODUCTS;
            case "/orders" -> ORDERS;
            case "/inventory" -> INVENTORY;
            case "/customers" -> CUSTOMERS;
            case "/warehouses" -> WAREHOUSES;
            case "/dispatches" -> DISPATCHES;
            default -> {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown API resource.");
                yield null;
            }
        };
    }

    private static void appendJsonString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static long positiveLong(String value) {
        long parsed = Long.parseLong(value);
        if (parsed < 1) {
            throw new NumberFormatException();
        }
        return parsed;
    }

    private static boolean bindParameters(PreparedStatement statement, HttpServletRequest request,
                                          HttpServletResponse response, Resource resource)
            throws IOException, SQLException {
        String path = request.getPathInfo();
        for (int index = 0; index < resource.parameters().length; index++) {
            String parameter = resource.parameters()[index];
            String value = request.getParameter(parameter);
            if (value == null || value.isBlank()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Missing required field: " + parameter);
                return false;
            }

            switch (path) {
                case "/products" -> {
                    if ("price".equals(parameter)) {
                        java.math.BigDecimal price = new java.math.BigDecimal(value);
                        if (price.signum() < 0) {
                            throw new NumberFormatException();
                        }
                        statement.setBigDecimal(index + 1, price);
                    } else if ("quantity".equals(parameter)) {
                        int quantity = Integer.parseInt(value);
                        if (quantity < 0) {
                            throw new NumberFormatException();
                        }
                        statement.setInt(index + 1, quantity);
                    } else {
                        statement.setString(index + 1, value.trim());
                    }
                }
                case "/orders" -> {
                    if ("amount".equals(parameter)) {
                        java.math.BigDecimal amount = new java.math.BigDecimal(value);
                        if (amount.signum() < 0) {
                            throw new NumberFormatException();
                        }
                        statement.setBigDecimal(index + 1, amount);
                    } else {
                        statement.setString(index + 1, value.trim());
                    }
                }
                case "/inventory" -> {
                    if ("quantity".equals(parameter)) {
                        int quantity = Integer.parseInt(value);
                        if (quantity < 0) {
                            throw new NumberFormatException();
                        }
                        statement.setInt(index + 1, quantity);
                    } else {
                        statement.setString(index + 1, value.trim());
                    }
                }
                case "/warehouses" -> {
                    if ("capacity".equals(parameter)) {
                        int capacity = Integer.parseInt(value);
                        if (capacity < 0) {
                            throw new NumberFormatException();
                        }
                        statement.setInt(index + 1, capacity);
                    } else {
                        statement.setString(index + 1, value.trim());
                    }
                }
                case "/dispatches" -> {
                    if ("orderId".equals(parameter)) {
                        statement.setLong(index + 1, positiveLong(value));
                    } else {
                        statement.setString(index + 1, value.trim());
                    }
                }
                default -> statement.setString(index + 1, value.trim());
            }
        }
        return true;
    }

    private static void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        StringBuilder json = new StringBuilder("{\"error\":");
        appendJsonString(json, message);
        response.getWriter().print(json.append('}'));
    }
}
