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
        boolean esJson = contentType != null && contentType.contains("application/json");
        boolean esPostPutPatch = "POST".equalsIgnoreCase(method) ||
                                 "PUT".equalsIgnoreCase(method) ||
                                 "PATCH".equalsIgnoreCase(method);

        if (esJson && esPostPutPatch) {
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(httpRequest);
            chain.doFilter(cachedRequest, response);
        } else {
            // 🚫 NO envolver DELETE ni multipart ni nada más
            chain.doFilter(request, response);
        }
    }
}

