package com.mifincaapp.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.Enumeration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/")
public class ProxyController {

    @Value("${api.usuarios.url}")
    private String usuariosApiUrl;

    @Value("${api.productos.url}")
    private String productosApiUrl; // Incluye admin, ventas, fincas, productos

    @Value("${api.pagos.url}")
    private String pagosApiUrl;

    private final RestTemplate restTemplate;

    public ProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

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

    @PostMapping(value = "/productos/finca/{fincaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> proxyCrearProducto(
            @PathVariable Long fincaId,
            @RequestPart("producto") String productoJson,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen,
            @RequestHeader("USER-MIFINCA-CLIENT") String clientHeader
    ) {
        try {
            // URL final que apunta al microservicio de productos
            String targetUrl = productosApiUrl + "/productos/finca/" + fincaId;

            // Construir multipart
            MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();

            // Parte JSON
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            multipartBody.add("producto", new HttpEntity<>(productoJson, jsonHeaders));

            // Parte archivo
            if (imagen != null && !imagen.isEmpty()) {
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentDispositionFormData("imagen", imagen.getOriginalFilename());
                fileHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
                multipartBody.add("imagen", new HttpEntity<>(imagen.getResource(), fileHeaders));
            }

            // Encabezados generales
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.add("USER-MIFINCA-CLIENT", clientHeader);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(multipartBody, headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );

            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error reenviando multipart: " + e.getMessage());
        }
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
            HttpEntity<byte[]> entity;
            if (bodyBytes.length == 0) {
                entity = new HttpEntity<>(headers);
            } else {
                entity = new HttpEntity<>(bodyBytes, headers);
            }

            // Redireccionar la petición
            return restTemplate.exchange(targetUrl, method, entity, byte[].class);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al redirigir la petición: " + ex.getMessage());
        }
    }
}
