package br.com.motorfraude.rules.adapters.presenter;

import br.com.motorfraude.rules.application.RuleAlreadyExistsException;
import br.com.motorfraude.rules.application.RuleInvalidException;
import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.RuleNotInSetException;
import br.com.motorfraude.rules.application.RuleSetInvalidException;
import br.com.motorfraude.rules.domain.InvalidRuleDefinitionException;
import br.com.motorfraude.rules.domain.InvalidTransitionException;
import br.com.motorfraude.rules.domain.SelfApprovalException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);
    public static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    @ExceptionHandler(RuleInvalidException.class)
    ResponseEntity<Map<String, Object>> ruleInvalid(RuleInvalidException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "rule-invalid", "Regra inválida", "A expressão da regra não compila.", e.errors());
    }

    @ExceptionHandler(RuleSetInvalidException.class)
    ResponseEntity<Map<String, Object>> ruleSetInvalid(RuleSetInvalidException e) {
        var errors = e.issues().stream().flatMap(i -> i.errors().stream().map(m -> i.ruleId() + ": " + m)).toList();
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "rule-set-invalid", "Conjunto de regras inválido",
                "O conjunto resultante não compila, então nada foi publicado.", errors);
    }

    @ExceptionHandler(InvalidRuleDefinitionException.class)
    ResponseEntity<Map<String, Object>> invalidDefinition(InvalidRuleDefinitionException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Requisição inválida", "Há campos fora das regras do contrato.", e.errors());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "unreadable-request", "Requisição ilegível",
                "O corpo ou um parâmetro da rota não pôde ser lido.", List.of("corpo ou parâmetro malformado"));
    }

    @ExceptionHandler(RuleNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(RuleNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Não encontrado", e.getMessage(), List.of());
    }

    @ExceptionHandler({RuleAlreadyExistsException.class, RuleNotInSetException.class, InvalidTransitionException.class})
    ResponseEntity<Map<String, Object>> conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "conflict", "Conflito", e.getMessage(), List.of());
    }

    @ExceptionHandler(SelfApprovalException.class)
    ResponseEntity<Map<String, Object>> selfApproval(SelfApprovalException e) {
        return problem(HttpStatus.FORBIDDEN, "self-approval", "Aprovação por outra pessoa",
                "A ativação precisa ser feita por alguém diferente de quem criou a regra.", List.of(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        LOG.error("erro inesperado", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Erro interno", "Erro inesperado.", List.of());
    }

    static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String slug, String title, String detail, List<String> errors) {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "urn:motorfraude:problem:" + slug);
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("errors", errors);
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body);
    }
}
