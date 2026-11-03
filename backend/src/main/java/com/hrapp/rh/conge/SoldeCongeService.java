package com.hrapp.rh.conge;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SoldeCongeService {

    private final SoldeCongeRepository soldeRepository;
    private final DemandeCongeRepository demandeRepository;
    private final TypeCongeRepository typeCongeRepository;
    private final Clock clock;

    /** Mes soldes de l'année (sans rien créer en base : un solde absent est présenté avec la valeur par défaut). */
    @Transactional(readOnly = true)
    public List<SoldeResponse> findMine(UtilisateurConnecte utilisateur, Integer annee) {
        int anneeCible = annee != null ? annee : LocalDate.now(clock).getYear();
        if (anneeCible < 2000 || anneeCible > 2100) {
            throw new RequeteInvalideException("Année invalide : " + anneeCible);
        }
        Long employeId = utilisateur.getEmployeId();

        return typeCongeRepository.findByDeduitDuSoldeTrueOrderByLibelle().stream().map(type -> {
            SoldeConge solde = soldeRepository
                    .findByEmployeIdAndTypeCongeIdAndAnnee(employeId, type.getId(), anneeCible).orElse(null);
            BigDecimal alloues = solde != null ? solde.getJoursAlloues() : defaultAllowance(type);
            BigDecimal pris = solde != null ? solde.getJoursPris() : BigDecimal.ZERO;
            BigDecimal enAttente = pendingDays(employeId, type.getId(), anneeCible);
            return new SoldeResponse(type.getId(), type.getCode(), type.getLibelle(), anneeCible,
                    alloues, pris, enAttente, alloues.subtract(pris).subtract(enAttente));
        }).toList();
    }

    /**
     * Jours disponibles pour une nouvelle demande. Crée le solde de l'année s'il n'existe pas encore.
     * À appeler dans une transaction (l'employé est verrouillé par l'appelant).
     */
    @Transactional
    public BigDecimal calculateAvailable(Employe employe, TypeConge type, int annee) {
        SoldeConge solde = soldeRepository
                .findByEmployeIdAndTypeCongeIdAndAnnee(employe.getId(), type.getId(), annee)
                .orElseGet(() -> createBalance(employe, type, annee));
        BigDecimal enAttente = pendingDays(employe.getId(), type.getId(), annee);
        return solde.getJoursAlloues().subtract(solde.getJoursPris()).subtract(enAttente);
    }

    // ---------- méthodes privées ----------

    private SoldeConge createBalance(Employe employe, TypeConge type, int annee) {
        SoldeConge solde = new SoldeConge();
        solde.setEmploye(employe);
        solde.setTypeConge(type);
        solde.setAnnee(annee);
        solde.setJoursAlloues(defaultAllowance(type));
        solde.setJoursPris(BigDecimal.ZERO);
        return soldeRepository.save(solde);
    }

    private BigDecimal pendingDays(Long employeId, Long typeId, int annee) {
        BigDecimal total = demandeRepository.sumJours(employeId, typeId, StatutDemandeConge.EN_ATTENTE,
                LocalDate.of(annee, 1, 1), LocalDate.of(annee, 12, 31));
        return Objects.requireNonNullElse(total, BigDecimal.ZERO);
    }

    private BigDecimal defaultAllowance(TypeConge type) {
        return type.getJoursParAn() != null ? type.getJoursParAn() : BigDecimal.ZERO;
    }
}