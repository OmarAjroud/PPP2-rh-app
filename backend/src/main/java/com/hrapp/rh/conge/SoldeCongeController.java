package com.hrapp.rh.conge;

import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conges/soldes")
@RequiredArgsConstructor
public class SoldeCongeController {

    private final SoldeCongeService service;

    /** Mes soldes de l'année (l'année en cours par défaut). */
    @GetMapping("/moi")
    public List<SoldeResponse> mine(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                    @RequestParam(required = false) Integer annee) {
        return service.findMine(utilisateur, annee);
    }
}