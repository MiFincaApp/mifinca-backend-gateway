
package com.mifincaapp.gateway.config;

import com.mifincaapp.gateway.filter.CachedBodyFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<CachedBodyFilter> cachedBodyFilter() {
        FilterRegistrationBean<CachedBodyFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CachedBodyFilter());
        registrationBean.addUrlPatterns("/*"); // Aplica a todas las rutas
        registrationBean.setOrder(1); // Prioridad alta
        return registrationBean;
    }
}
