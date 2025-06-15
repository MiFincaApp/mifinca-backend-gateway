package com.mifincaapp.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.Enumeration;

@RestController
@RequestMapping("/")
public class ProxyController {

    @Value("${api.usuarios.url}")
    private String usuariosApiUrl;

    @Value("${api.productos.url}")
    private String productosApiUrl; // Incluye admin, ventas, fincas, productos

    @Value("${api.pagos.url}")
    private String pagosApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // ------------------------- RUTAS SIN TOKEN --------------------------
    @RequestMapping("/usuarios/login")
    public ResponseEntity<?> loginUsuario(HttpServletRequest request) {
        return proxyRequest(request, usuariosApiUrl, false);
    }

    @RequestMapping("/usuarios/registro")
    public ResponseEntity<?> registroUsuario(HttpServletRequest request) {
        return proxyRequest(request, usuariosApiUrl, false);
    }

    @RequestMapping("/productos/**")
    public ResponseEntity<?> proxyProductos(HttpServletRequest request) {
        return proxyRequest(request, productosApiUrl, false);
    }

    // ------------------------- RUTAS CON TOKEN --------------------------
    @RequestMapping("/usuarios/**")
    public ResponseEntity<?> proxyUsuarios(HttpServletRequest request) {
        return proxyRequest(request, usuariosApiUrl, true);
    }

    @RequestMapping({"/ventas/**", "/finca/**", "/admin/**"})
    public ResponseEntity<?> proxyInternos(HttpServletRequest request) {
        return proxyRequest(request, productosApiUrl, true);
    }

    @RequestMapping("/pagos/**")
    public ResponseEntity<?> proxyPagos(HttpServletRequest request) {
        return proxyRequest(request, pagosApiUrl, true);
    }

    // ------------------------- RUTA RAÍZ --------------------------
    @RequestMapping(method = RequestMethod.GET, path = "")
    public ResponseEntity<?> proxyRoot(HttpServletRequest request) {
        return proxyRequest(request, usuariosApiUrl, false, false);
    }

    // ------------------------- SOBRECARGA SIMPLE --------------------------
    private ResponseEntity<?> proxyRequest(HttpServletRequest request,
            String targetBaseUrl,
            boolean requireToken) {
        return proxyRequest(request, targetBaseUrl, requireToken, true); // por defecto sí exige header
    }

    // ------------------------- MÉTODO GENERAL --------------------------
    private ResponseEntity<?> proxyRequest(HttpServletRequest request,
            String targetBaseUrl,
            boolean requireToken,
            boolean requireCustomHeader) {
        try {
            // Construir la URL de destino
            String path = request.getRequestURI();
            String query = request.getQueryString();
            String targetUrl = targetBaseUrl + path + (query != null ? "?" + query : "");

            // Leer el cuerpo como bytes (soporta raw, form-data, multipart, etc.)
            InputStream is = request.getInputStream();
            byte[] bodyBytes = is.readAllBytes();

            // Copiar headers originales
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();

            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String value = request.getHeader(name);
                headers.add(name, value);
            }

            // Validar que venga el header USER-MIFINCA-CLIENT (case-insensitive)
            if (requireCustomHeader) {
                boolean hasCustomHeader = headers.keySet().stream()
                        .anyMatch(h -> h.equalsIgnoreCase("USER-MIFINCA-CLIENT"));

                if (!hasCustomHeader) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Falta el header requerido: USER-MIFINCA-CLIENT");
                }
            }

            // Validar token si es necesario
            if (requireToken && !headers.containsKey("Authorization")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Token JWT requerido en Authorization header");
            }

            // Detectar método HTTP
            HttpMethod method;
            try {
                method = HttpMethod.valueOf(request.getMethod().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                        .body("Método HTTP no soportado: " + request.getMethod());
            }

            // Crear entidad con body binario + headers (manejo correcto de multipart/form-data)
            InputStreamResource resource = new InputStreamResource(request.getInputStream()) {
                public String getFilename() {
                    return null;
                }

                public long contentLength() {
                    return -1;
                }
            };

            HttpEntity<InputStreamResource> entity = new HttpEntity<>(resource, headers);

            // Redireccionar la petición
            return restTemplate.exchange(targetUrl, method, entity, byte[].class);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al redirigir la petición: " + ex.getMessage());
        }
    }
}
