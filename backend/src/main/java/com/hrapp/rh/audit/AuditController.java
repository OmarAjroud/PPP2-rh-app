package com.hrapp.rh.audit;

import com.hrapp.rh.common.dto.PageReponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService service;

    /** Exemple : GET /api/admin/audit?action=MODIFICATION&entite=Employe&du=2026-11-01&au=2026-11-30 */
    @GetMapping
    public PageReponse<AuditEntryResponse> search(
            @RequestParam(required = false) String nomUtilisateur,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entite,
            @RequestParam(required = false) String entiteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(new AuditFilter(nomUtilisateur, action, entite, entiteId, du, au), page, size);
    }

    /** Valeurs possibles pour le filtre « action ». */
    @GetMapping("/actions")
    public List<String> actions() {
        return Arrays.stream(AuditAction.values()).map(Enum::name).toList();
    }

    /** Valeurs possibles pour le filtre « entité » (celles déjà présentes dans le journal). */
    @GetMapping("/entites")
    public List<String> entites() {
        return service.listEntities();
    }
}