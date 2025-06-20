package com.mifincaapp.gateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CachedBodyFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String method = httpRequest.getMethod();
        String contentType = httpRequest.getContentType();

        // Aplicar filtro solo si:
        // - Método es POST o PUT
        // - Y Content-Type contiene "application/json"
        boolean esJson = contentType != null && contentType.contains("application/json");
        boolean esPostOPut = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method);

        if (esJson && esPostOPut) {
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(httpRequest);
            chain.doFilter(cachedRequest, response);
        } else {
            // Evita envolver DELETE o multipart/form-data
            chain.doFilter(request, response);
        }
    }
}
