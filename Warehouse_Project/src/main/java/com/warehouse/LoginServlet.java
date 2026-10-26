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
import java.sql.ResultSet;
import jakarta.servlet.http.HttpSession;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                           HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        String email = request.getParameter("email");
        String password = request.getParameter("password");

        String sql = "SELECT password_hash FROM admin_credentials WHERE id = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT));

            ResultSet result = statement.executeQuery();

            if (result.next()
                    && password != null
                    && PasswordUtil.verifyPassword(password, result.getString("password_hash"))) {
                HttpSession session = request.getSession(true);
                request.changeSessionId();
                session.setAttribute("adminEmail",
                        email.trim().toLowerCase(java.util.Locale.ROOT));
                response.sendRedirect(request.getContextPath() + "/warehouse.html");
            } else {
                response.sendRedirect(request.getContextPath() + "/login.html?error=invalid");
            }

        } catch (java.sql.SQLException | GeneralSecurityException | RuntimeException e) {
            log("Login failed because of a server or database error.", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Login is temporarily unavailable. Check the database configuration.");
        }
    }
}