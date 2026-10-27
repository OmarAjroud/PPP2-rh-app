package com.hrapp.rh.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestControllerAdvice
public class GestionnaireExceptionsGlobal {

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<ErreurApi> introuvable(RessourceIntrouvableException ex, HttpServletRequest req) {
        return reponse(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ConflitException.class)
    public ResponseEntity<ErreurApi> conflit(ConflitException ex, HttpServletRequest req) {
        return reponse(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    /** Erreurs de validation (@Valid) : on renvoie une erreur par champ. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApi> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> champs = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "Valeur invalide" : fe.getDefaultMessage(),
                        (a, b) -> a));
        return reponse(HttpStatus.BAD_REQUEST, "Données invalides", req, champs);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErreurApi> requeteIncorrecte(Exception ex, HttpServletRequest req) {
        return reponse(HttpStatus.BAD_REQUEST, "Requête incorrecte (JSON ou paramètre invalide)", req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErreurApi> urlInconnue(NoResourceFoundException ex, HttpServletRequest req) {
        return reponse(HttpStatus.NOT_FOUND, "URL inconnue", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurApi> erreurInterne(Exception ex, HttpServletRequest req) {
        log.error("Erreur interne sur {}", req.getRequestURI(), ex);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue", req, null);
    }

    private ResponseEntity<ErreurApi> reponse(HttpStatus statut, String message,
                                              HttpServletRequest req, Map<String, String> champs) {
        ErreurApi corps = new ErreurApi(LocalDateTime.now(), statut.value(),
                statut.getReasonPhrase(), message, req.getRequestURI(), champs);
        return ResponseEntity.status(statut).body(corps);
    }
}