package br.com.motorfraude.rules.adapters.controller;

import br.com.motorfraude.rules.adapters.presenter.RestExceptionHandler;
import br.com.motorfraude.rules.application.gateway.IdempotencyStore.StoredResponse;
import br.com.motorfraude.rules.application.usecase.IdempotencyGuard;
import br.com.motorfraude.rules.application.usecase.IdempotencyGuard.Begin;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code Idempotency-Key} nos POST e PUT da API (plano 002, §4.4): a mesma chave não repete a ação e devolve a mesma
 * resposta. Roda depois da autenticação, porque a chave vale por usuário. Sem o header, a requisição passa direto.
 * Só respostas 2xx são guardadas; uma falha libera a chave para a origem corrigir e repetir.
 */
public final class IdempotencyFilter extends OncePerRequestFilter {

    static final String HEADER = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";
    private static final Pattern VALID_KEY = Pattern.compile("^[\\x21-\\x7E]{1,255}$");

    private final IdempotencyGuard guard;
    private final JsonMapper json;

    public IdempotencyFilter(IdempotencyGuard guard, JsonMapper json) {
        this.guard = guard;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var method = request.getMethod();
        return !(method.equals("POST") || method.equals("PUT")) || !request.getRequestURI().startsWith("/v1/")
                || request.getHeader(HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var key = request.getHeader(HEADER);
        if (!VALID_KEY.matcher(key).matches()) {
            problem(response, 400, "invalid-idempotency-key", "Idempotency-Key inválida",
                    "Use de 1 a 255 caracteres ASCII visíveis, sem espaços.");
            return;
        }
        var principal = request.getUserPrincipal();
        if (principal == null) {
            chain.doFilter(request, response);
            return;
        }
        var actor = principal.getName();
        byte[] body = request.getInputStream().readAllBytes();
        var hash = hash(request.getMethod(), request.getRequestURI(), request.getQueryString(), body);

        switch (guard.begin(actor, key, hash)) {
            case Begin.Replay replay -> replay(response, replay.response());
            case Begin.InProgress ignored -> {
                response.setHeader("Retry-After", "1");
                problem(response, 409, "idempotency-in-progress", "Requisição em andamento",
                        "Outra requisição com a mesma Idempotency-Key ainda está sendo processada. Tente de novo em instantes.");
            }
            case Begin.KeyReused ignored -> problem(response, 422, "idempotency-key-reused", "Idempotency-Key reutilizada",
                    "Esta chave já foi usada com outra requisição. Use uma chave nova para uma operação diferente.");
            case Begin.Proceed ignored -> execute(new CachedBodyRequest(request, body), response, chain, actor, key);
        }
    }

    private void execute(HttpServletRequest request, HttpServletResponse response, FilterChain chain, String actor, String key)
            throws ServletException, IOException {
        var wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
        } catch (ServletException | IOException | RuntimeException e) {
            guard.abandon(actor, key);
            throw e;
        }
        int status = wrapper.getStatus();
        if (status >= 200 && status < 300) {
            guard.complete(actor, key, new StoredResponse(status, wrapper.getContentType(), wrapper.getHeader("Location"),
                    new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8)));
        } else {
            guard.abandon(actor, key);
        }
        wrapper.copyBodyToResponse();
    }

    private static void replay(HttpServletResponse response, StoredResponse stored) throws IOException {
        response.setStatus(stored.status());
        if (stored.contentType() != null) {
            response.setContentType(stored.contentType());
        }
        if (stored.location() != null) {
            response.setHeader("Location", stored.location());
        }
        response.setHeader(REPLAYED, "true");
        response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
    }

    private void problem(HttpServletResponse response, int status, String slug, String title, String detail) throws IOException {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "urn:motorfraude:problem:" + slug);
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("errors", List.of());
        response.setStatus(status);
        response.setContentType(RestExceptionHandler.PROBLEM_JSON.toString());
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(body));
    }

    /** Identifica a requisição: método, caminho, consulta e corpo. A mesma chave com outro hash é uso indevido. */
    static String hash(String method, String path, String query, byte[] body) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update((method + " " + path + "?" + (query == null ? "" : query) + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** O corpo já foi lido para o hash; os controllers precisam lê-lo de novo. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("leitura assíncrona não suportada");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            var charset = getCharacterEncoding() == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
