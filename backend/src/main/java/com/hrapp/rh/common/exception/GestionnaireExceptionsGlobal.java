package com.hrapp.rh.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

@Slf4j
@RestControllerAdvice
public class GestionnaireExceptionsGlobal {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurApi> accessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.FORBIDDEN,
                "Accès refusé : vous n'avez pas les droits nécessaires", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErreurApi> authenticationFailed(AuthenticationException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.UNAUTHORIZED,
                "Authentification échouée : identifiants ou jeton invalides", req, null);
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<ErreurApi> notFound(RessourceIntrouvableException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ConflitException.class)
    public ResponseEntity<ErreurApi> conflict(ConflitException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    /** Erreurs de validation (@Valid) : on renvoie une erreur par champ. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApi> validationError(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> champs = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "Valeur invalide" : fe.getDefaultMessage(),
                        (a, b) -> a));
        return buildResponse(HttpStatus.BAD_REQUEST, "Données invalides", req, champs);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErreurApi> badRequest(Exception ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Requête incorrecte (JSON ou paramètre invalide)", req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErreurApi> unknownUrl(NoResourceFoundException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.NOT_FOUND, "URL inconnue", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurApi> internalError(Exception ex, HttpServletRequest req) {
        log.error("Erreur interne sur {}", req.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue", req, null);
    }

    @ExceptionHandler(RequeteInvalideException.class)
    public ResponseEntity<ErreurApi> invalidRequest(RequeteInvalideException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErreurApi> dataIntegrityViolation(DataIntegrityViolationException ex,
                                                              HttpServletRequest req) {
        return buildResponse(HttpStatus.CONFLICT, "L'opération viole une contrainte de données", req, null);
    }

    private ResponseEntity<ErreurApi> buildResponse(HttpStatus statut, String message,
                                              HttpServletRequest req, Map<String, String> champs) {
        ErreurApi corps = new ErreurApi(LocalDateTime.now(), statut.value(),
                statut.getReasonPhrase(), message, req.getRequestURI(), champs);
        return ResponseEntity.status(statut).body(corps);
    }
}
