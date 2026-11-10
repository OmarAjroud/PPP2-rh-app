package com.hrapp.rh.export;

import com.hrapp.rh.audit.AuditAction;
import com.hrapp.rh.audit.AuditFilter;
import com.hrapp.rh.audit.AuditService;
import com.hrapp.rh.conge.StatutDemandeConge;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Exports Excel (?format=xlsx) et PDF (?format=pdf) des listes, avec les mêmes paramètres que les listes.
 * Droits : employés et départements comme les listes (le service restreint) ; demandes ADMIN, RH ; journal ADMIN.
 * Chaque export est enregistré dans le journal d'audit (qui, quelle liste, quel format, combien de lignes, filtres).
 */
@RestController
@RequestMapping("/api/exports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService service;
    private final AuditService auditService;
    private final Clock clock;

    @GetMapping("/employes")
    public ResponseEntity<byte[]> employes(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                           @RequestParam String format,
                                           @RequestParam(required = false) String recherche,
                                           @RequestParam(required = false) Long departementId,
                                           @RequestParam(required = false) Long roleId) {
        FormatExport f = FormatExport.depuis(format);
        return fichier("employes", service.employes(utilisateur, recherche, departementId, roleId), f, utilisateur);
    }

    @GetMapping("/departements")
    public ResponseEntity<byte[]> departements(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                               @RequestParam String format) {
        FormatExport f = FormatExport.depuis(format);
        return fichier("departements", service.departements(), f, utilisateur);
    }

    @GetMapping("/demandes")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<byte[]> demandes(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                           @RequestParam String format,
                                           @RequestParam(required = false) StatutDemandeConge statut,
                                           @RequestParam(required = false) Long employeId,
                                           @RequestParam(required = false) Long departementId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        FormatExport f = FormatExport.depuis(format);
        return fichier("demandes-conge", service.demandes(statut, employeId, departementId, du, au), f, utilisateur);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> audit(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                        @RequestParam String format,
                                        @RequestParam(required = false) String nomUtilisateur,
                                        @RequestParam(required = false) AuditAction action,
                                        @RequestParam(required = false) String entite,
                                        @RequestParam(required = false) String entiteId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        FormatExport f = FormatExport.depuis(format);
        return fichier("journal-audit", service.audit(new AuditFilter(nomUtilisateur, action, entite, entiteId, du, au)), f, utilisateur);
    }

    private ResponseEntity<byte[]> fichier(String liste, Tableau tableau, FormatExport format, UtilisateurConnecte utilisateur) {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            if (format == FormatExport.XLSX) {
                ExportExcel.ecrire(tableau, sortie);
            } else {
                ExportPdf.ecrire(tableau, utilisateur.getUsername(), maintenant, sortie);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String details = format.extension() + ", " + tableau.lignes().size() + " ligne(s)"
                + (tableau.filtres() == null ? "" : ", " + tableau.filtres());
        auditService.record(AuditAction.EXPORT, "Export", liste, details);

        String nomFichier = liste + "-" + maintenant.toLocalDate() + "." + format.extension();
        return ResponseEntity.ok()
                .contentType(format.type())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nomFichier, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")   // personal data: never kept in a shared cache
                .body(sortie.toByteArray());
    }
}
