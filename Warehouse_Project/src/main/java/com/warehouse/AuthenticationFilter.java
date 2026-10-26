package com.warehouse;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;

@WebFilter(urlPatterns = {
        "/warehouse.html", "/products.html", "/orders.html", "/inventory.html",
        "/customers.html", "/warehouses.html", "/dispatch.html", "/api/*", "/addProduct"
})
public class AuthenticationFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/login", "/signup", "/login.html", "/signup.html"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        HttpSession session = request.getSession(false);

        if (PUBLIC_PATHS.contains(request.getServletPath())
                || session != null && session.getAttribute("adminEmail") != null) {
            chain.doFilter(request, response);
            return;
        }

        if (request.getServletPath().startsWith("/api/")
                || request.getHeader("Accept") != null
                && request.getHeader("Accept").contains("application/json")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print("{\"error\":\"Please sign in to continue.\"}");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/login.html");
    }
}
