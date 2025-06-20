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
import org.springframework.core.io.ByteArrayResource;


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

    // --------------------- RUTAS PÚBLICAS ---------------------
    @PostMapping("/usuarios/login")
    public ResponseEntity<?> loginUsuario(HttpServletRequest request) {
        return proxyRequest(request, usuariosApiUrl, false);
    }

    @PostMapping("/usuarios/registro")
    public ResponseEntity<?> registroUsuario(HttpServletRequest request) {
        return proxyRequest(request, usuariosApiUrl, false);
    }

    @RequestMapping("/webhook/**")
    public ResponseEntity<?> proxyWebhookPagos(HttpServletRequest request) {
        return proxyRequest(request, pagosApiUrl, false, false);
    }

    @RequestMapping("/wompi/**")
    public ResponseEntity<?> proxyWompi(HttpServletRequest request) {
        return proxyRequest(request, pagosApiUrl, false, false);
    }

    @RequestMapping("/productos/**")
    public ResponseEntity<?> proxyProductosGenerales(HttpServletRequest request) {
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
    
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    
            // Enviar producto como String plano, no HttpEntity
            body.add("producto", productoJson);
    
            if (imagen != null && !imagen.isEmpty()) {
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentDispositionFormData("imagen", imagen.getOriginalFilename());
                fileHeaders.setContentType(MediaType.parseMediaType(imagen.getContentType()));
    
                ByteArrayResource byteArrayResource = new ByteArrayResource(imagen.getBytes()) {
                    @Override
                    public String getFilename() {
                        return imagen.getOriginalFilename();
                    }
                };
    
                body.add("imagen", new HttpEntity<>(byteArrayResource, fileHeaders));
            }
    
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("USER-MIFINCA-CLIENT", clientHeader);
    
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
    
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
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error reenviando producto con imagen: " + e.getMessage());
        }
    }
    
    @PutMapping("/productos/**")
    public ResponseEntity<?> proxyPutProductos(HttpServletRequest request) {
        return proxyPutRequestWithBody(request, productosApiUrl);
    }

    // --------------------- RUTAS CON TOKEN ---------------------
    @PutMapping("/productos/{id}")
    public ResponseEntity<?> proxyActualizarProductoConBodyCrudo(
            @PathVariable Long id,
            HttpServletRequest request,
            @RequestHeader("USER-MIFINCA-CLIENT") String clientHeader
    ) {
        try {
            // Leer el cuerpo crudo
            byte[] bodyBytes = request.getInputStream().readAllBytes();

            // Construir la URL al microservicio
            String targetUrl = productosApiUrl + "/productos/" + id;

            // Encabezados necesarios
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("USER-MIFINCA-CLIENT", clientHeader);

            // Construir la solicitud HTTP con body y headers
            HttpEntity<byte[]> entity = new HttpEntity<>(bodyBytes, headers);

            // Hacer el PUT al microservicio
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.PUT,
                    entity,
                    byte[].class
            );

            // Devolver respuesta del microservicio al cliente original
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error reenviando PUT /productos/{id}: " + e.getMessage());
        }
    }

    // --------------------- RUTAS CON TOKEN ---------------------
    @RequestMapping("/usuarios/**")
    public ResponseEntity<?> proxyUsuariosPrivado(HttpServletRequest request) {
        return proxyRequest(request, usuariosApiUrl, true);
    }

    @RequestMapping({"/ventas/**", "/finca/**", "/admin/**"})
    public ResponseEntity<?> proxyInternas(HttpServletRequest request) {
        return proxyRequest(request, productosApiUrl, true);
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResponseEntity<?> proxyRoot(HttpServletRequest request) {
        return proxyRequest(request, usuariosApiUrl, false, false);
    }

    // --------------------- MÉTODOS GENERALES ---------------------
    private ResponseEntity<?> proxyRequest(HttpServletRequest request,
            String baseUrl,
            boolean requiereToken) {
        return proxyRequest(request, baseUrl, requiereToken, true);
    }

    private ResponseEntity<?> proxyRequest(HttpServletRequest request,
            String baseUrl,
            boolean requiereToken,
            boolean requiereHeaderCliente) {
        try {
            String path = request.getRequestURI();
            String query = request.getQueryString();
            String fullUrl = baseUrl + path + (query != null ? "?" + query : "");

            byte[] bodyBytes = getRequestBody(request);

            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                headers.set(name, request.getHeader(name));
            }

            if (requiereHeaderCliente) {
                if (!headers.containsKey("USER-MIFINCA-CLIENT")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Falta el header requerido: USER-MIFINCA-CLIENT");
                }
            }

            if (requiereToken && !headers.containsKey("Authorization")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Falta el token en el header Authorization");
            }

            HttpMethod method;
            try {
                method = HttpMethod.valueOf(request.getMethod());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                        .body("Método HTTP no permitido: " + request.getMethod());
            }

            HttpEntity<?> entity;
            if (method == HttpMethod.DELETE) {
                entity = new HttpEntity<>(headers); // 🚫 nunca enviar body con DELETE
            } else {
                entity = new HttpEntity<>(body.length > 0 ? body : null, headers);
            }

            System.out.println("🔁 Reenviando a: " + fullUrl);
            System.out.println("🔸 Método: " + method);
            if (body.length > 0) {
                System.out.println("📦 Body:\n" + new String(body, StandardCharsets.UTF_8));
            }

            ResponseEntity<byte[]> response = restTemplate.exchange(fullUrl, method, entity, byte[].class);

            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al reenviar: " + ex.getMessage());
        }
    }

    // ✅ Aquí está el método corregido
    private byte[] getRequestBody(HttpServletRequest request) {
        try {
            String method = request.getMethod();
            if ("DELETE".equalsIgnoreCase(method)) {
                return new byte[0]; // 🔐 No leer body en DELETE
            }
            return request.getInputStream().readAllBytes();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private ResponseEntity<?> proxyPutRequestWithBody(HttpServletRequest request, String baseUrl) {
        try {
            String path = request.getRequestURI();
            String query = request.getQueryString();
            String fullUrl = baseUrl + path + (query != null ? "?" + query : "");

            byte[] bodyBytes = getRequestBody(request);

            if (bodyBytes == null || bodyBytes.length == 0) {
                return ResponseEntity.badRequest().body("El cuerpo PUT está vacío");
            }

            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.set(name, request.getHeader(name));
            }

            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<byte[]> entity = new HttpEntity<>(bodyBytes, headers);

            System.out.println("🔄 Reenviando PUT a: " + fullUrl);
            System.out.println("📦 Body:\n" + new String(bodyBytes, StandardCharsets.UTF_8));

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.PUT,
                    entity,
                    byte[].class
            );

            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al reenviar PUT: " + ex.getMessage());
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
