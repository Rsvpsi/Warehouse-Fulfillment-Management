package com.warehouse;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;

@WebServlet("/signup")
public class SignupServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                           HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        String name = request.getParameter("adminName");
        String email = request.getParameter("adminEmailId");
        String phone = request.getParameter("phone");
        String password = request.getParameter("password");

        if (name == null || name.isBlank()
                || email == null || email.isBlank()
                || phone == null || !phone.matches("[0-9]{10}")
                || password == null || password.length() < 8) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a name, valid phone number, email, and password of at least 8 characters.");
            return;
        }

        String sql = "INSERT INTO admin_credentials (id, password_hash, admin_name, phone) " +
                     "VALUES (?, ?, ?, ?)";

        try {
            String passwordHash = PasswordUtil.hashPassword(password);
            try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, email.trim().toLowerCase(java.util.Locale.ROOT));
            statement.setString(2, passwordHash);
            statement.setString(3, name.trim());
            statement.setString(4, phone.trim());

            int result = statement.executeUpdate();

            if (result > 0) {
                response.sendRedirect(request.getContextPath() + "/login.html?registered=1");
            } else {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Admin registration failed.");
            }

            }
        } catch (java.sql.SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "An admin account with this email already exists. Sign in with that email or use another one.");
                return;
            }
            log("Admin registration failed.", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Admin registration could not be saved. Check the database connection and try again.");
        } catch (GeneralSecurityException | RuntimeException e) {
            log("Admin registration failed.", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Admin registration could not be completed. Try again.");
        }
    }
}