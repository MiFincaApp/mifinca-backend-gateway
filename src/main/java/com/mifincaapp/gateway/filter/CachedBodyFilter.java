
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
        if (request instanceof HttpServletRequest) {
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest((HttpServletRequest) request);
            chain.doFilter(cachedRequest, response);
        } else {
            chain.doFilter(request, response);
        }
    }
}
