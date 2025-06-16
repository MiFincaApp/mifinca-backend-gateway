package com.mifincaapp.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

@RestController
@RequestMapping("/")
public class ProxyController {

    @Value("${api.usuarios.url}")
    private String usuariosApiUrl;

    @Value("${api.productos.url}")
    private String productosApiUrl;

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

    @RequestMapping("/webhook/**")
    public ResponseEntity<?> proxyPagos(HttpServletRequest request) {
        return proxyRequest(request, pagosApiUrl, false, false);
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
            String targetUrl = productosApiUrl + "/productos/finca/" + fincaId;

            MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();

            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            multipartBody.add("producto", new HttpEntity<>(productoJson, jsonHeaders));

            if (imagen != null && !imagen.isEmpty()) {
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentDispositionFormData("imagen", imagen.getOriginalFilename());
                fileHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
                multipartBody.add("imagen", new HttpEntity<>(imagen.getResource(), fileHeaders));
            }

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

    @RequestMapping(method = RequestMethod.GET, path = "")
    public ResponseEntity<?> proxyRoot(HttpServletRequest request) {
        return proxyRequest(request, usuariosApiUrl, false, false);
    }

    // ------------------------- MÉTODO AUXILIAR GENERAL --------------------------

    private ResponseEntity<?> proxyRequest(HttpServletRequest request,
                                           String targetBaseUrl,
                                           boolean requireToken) {
        return proxyRequest(request, targetBaseUrl, requireToken, true);
    }

    private ResponseEntity<?> proxyRequest(HttpServletRequest request,
                                           String targetBaseUrl,
                                           boolean requireToken,
                                           boolean requireCustomHeader) {
        try {
            String path = request.getRequestURI();
            String query = request.getQueryString();
            String targetUrl = targetBaseUrl + path + (query != null ? "?" + query : "");

            byte[] bodyBytes = getRequestBody(request);

            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String value = request.getHeader(name);
                headers.add(name, value);
            }

            if (requireCustomHeader) {
                boolean hasCustomHeader = headers.keySet().stream()
                        .anyMatch(h -> h.equalsIgnoreCase("USER-MIFINCA-CLIENT"));
                if (!hasCustomHeader) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Falta el header requerido: USER-MIFINCA-CLIENT");
                }
            }

            if (requireToken && !headers.containsKey("Authorization")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Token JWT requerido en Authorization header");
            }

            HttpMethod method;
            try {
                method = HttpMethod.valueOf(request.getMethod().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                        .body("Método HTTP no soportado: " + request.getMethod());
            }

            System.out.println("➡️ Reenviando a: " + targetUrl);
            System.out.println("➡️ Método: " + method);
            if (bodyBytes.length > 0) {
                System.out.println("➡️ Cuerpo: " + new String(bodyBytes, StandardCharsets.UTF_8));
            }

            HttpEntity<byte[]> entity = new HttpEntity<>(bodyBytes, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(targetUrl, method, entity, byte[].class);

            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al redirigir la petición: " + ex.getMessage());
        }
    }

    private byte[] getRequestBody(HttpServletRequest request) {
        try (InputStream is = request.getInputStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
