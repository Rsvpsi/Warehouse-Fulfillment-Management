package com.warehouse;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/addProduct")
public class ProductServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        if (request.getParameter("id") != null) {
            doPut(request, response);
            return;
        }
        String productName = request.getParameter("productName");
        String sku = request.getParameter("sku");
        String price = request.getParameter("price");
        String quantity = request.getParameter("quantity");

        if (isBlank(productName) || isBlank(sku) || isBlank(price) || isBlank(quantity)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Product name, SKU, price, and quantity are required.");
            return;
        }

        try {
            BigDecimal parsedPrice = new BigDecimal(price);
            int parsedQuantity = Integer.parseInt(quantity);
            if (parsedPrice.signum() < 0 || parsedQuantity < 0) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Price and quantity must not be negative.");
                return;
            }
            String sql = "INSERT INTO products " +
                    "(product_name, sku, price, quantity) VALUES (?, ?, ?, ?)";
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, productName.trim());
                statement.setString(2, sku.trim());
                statement.setBigDecimal(3, parsedPrice);
                statement.setInt(4, parsedQuantity);
                statement.executeUpdate();
            }
            response.sendRedirect(request.getContextPath() + "/products.html?saved=1");
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a valid numeric price and whole-number quantity.");
        } catch (SQLException | RuntimeException exception) {
            log("Failed to save product.", exception);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not save the product. Check the database and ensure the SKU is unique.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
            if (id < 1) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A valid product ID is required.");
            return;
        }

        String productName = request.getParameter("productName");
        String sku = request.getParameter("sku");
        String price = request.getParameter("price");
        String quantity = request.getParameter("quantity");
        if (isBlank(productName) || isBlank(sku) || isBlank(price) || isBlank(quantity)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Product name, SKU, price, and quantity are required.");
            return;
        }

        try {
            BigDecimal parsedPrice = new BigDecimal(price);
            int parsedQuantity = Integer.parseInt(quantity);
            if (parsedPrice.signum() < 0 || parsedQuantity < 0) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Price and quantity must not be negative.");
                return;
            }
            String sql = "UPDATE products SET product_name = ?, sku = ?, price = ?, quantity = ? " +
                    "WHERE product_id = ?";
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, productName.trim());
                statement.setString(2, sku.trim());
                statement.setBigDecimal(3, parsedPrice);
                statement.setInt(4, parsedQuantity);
                statement.setLong(5, id);
                if (statement.executeUpdate() == 0) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Product not found.");
                    return;
                }
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a valid numeric price and whole-number quantity.");
        } catch (SQLException | RuntimeException exception) {
            log("Failed to update product " + id + ".", exception);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not update the product. Check the database and ensure the SKU is unique.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String sql = "SELECT product_id, product_name, sku, price, quantity " +
                "FROM products ORDER BY product_id";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rows.next()) {
                if (!first) {
                    json.append(',');
                }
                json.append("{\"id\":").append(rows.getLong("product_id"))
                        .append(",\"name\":");
                appendJsonString(json, rows.getString("product_name"));
                json.append(",\"sku\":");
                appendJsonString(json, rows.getString("sku"));
                json.append(",\"price\":").append(rows.getBigDecimal("price"))
                        .append(",\"quantity\":").append(rows.getInt("quantity"))
                        .append('}');
                first = false;
            }
            response.getWriter().print(json.append(']'));
        } catch (SQLException | RuntimeException exception) {
            log("Failed to load products.", exception);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\":\"Could not load products. Check the database configuration.\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
            if (id < 1) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A valid product ID is required.");
            return;
        }

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM products WHERE product_id = ?")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() == 0) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Product not found.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (SQLException | RuntimeException exception) {
            log("Failed to delete product " + id + ".", exception);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not delete the product.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void appendJsonString(StringBuilder json, String value) {
        if (value == null) {
            json.append("null");
            return;
        }
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
}
