package com.hrapp.rh.conge;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SoldeCongeService {

    private final SoldeCongeRepository soldeRepository;
    private final DemandeCongeRepository demandeRepository;
    private final TypeCongeRepository typeCongeRepository;
    private final MouvementSoldeRepository mouvementRepository;
    private final EmployeRepository employeRepository;
    private final Clock clock;

    // ---------- consultation ----------

    /** Mes soldes de l'année (sans rien créer en base : un solde absent est présenté avec la valeur par défaut). */
    @Transactional(readOnly = true)
    public List<SoldeResponse> findMine(UtilisateurConnecte utilisateur, Integer annee) {
        return balances(utilisateur.getEmployeId(), checkedYear(annee));
    }

    /** Soldes d'un employé : lui-même, son manager d'équipe, ADMIN/RH (sinon 404). */
    @Transactional(readOnly = true)
    public List<SoldeResponse> findForEmployee(UtilisateurConnecte acteur, Long employeId, Integer annee) {
        loadVisibleEmployee(acteur, employeId);
        return balances(employeId, checkedYear(annee));
    }

    @Transactional(readOnly = true)
    public List<MouvementSoldeResponse> historyMine(UtilisateurConnecte utilisateur, Integer annee) {
        return history(utilisateur.getEmployeId(), checkedYear(annee));
    }

    @Transactional(readOnly = true)
    public List<MouvementSoldeResponse> historyForEmployee(UtilisateurConnecte acteur, Long employeId, Integer annee) {
        loadVisibleEmployee(acteur, employeId);
        return history(employeId, checkedYear(annee));
    }

    // ---------- utilisé à la création d'une demande ----------

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

    // ---------- utilisé à la validation et à l'annulation ----------

    /**
     * Déduit les jours d'une demande approuvée. Le solde est verrouillé, puis revérifié :
     * deux validations simultanées ne peuvent pas dépasser le solde.
     */
    @Transactional
    public void deductDays(DemandeConge demande, Employe acteur) {
        SoldeConge solde = lockBalance(demande);
        BigDecimal disponible = solde.getJoursAlloues().subtract(solde.getJoursPris());
        if (demande.getNombreJours().compareTo(disponible) > 0) {
            throw new ConflitException("Solde insuffisant au moment de la validation : " + format(disponible)
                    + " jour(s) disponible(s), " + format(demande.getNombreJours()) + " demandé(s)");
        }
        solde.setJoursPris(solde.getJoursPris().add(demande.getNombreJours()));
        recordMovement(solde, demande, TypeMouvement.DEDUCTION, demande.getNombreJours().negate(),
                "Validation de la demande n°" + demande.getId(), acteur);
    }

    /** Rend les jours d'un congé approuvé qui est annulé avant son début. */
    @Transactional
    public void refundDays(DemandeConge demande, Employe acteur) {
        refundDays(demande, demande.getNombreJours(), acteur, "Annulation de la demande n°" + demande.getId());
    }

    /** Rend une partie (ou la totalité) des jours d'un congé approuvé : annulation, interruption par les RH. */
    @Transactional
    public void refundDays(DemandeConge demande, BigDecimal jours, Employe acteur, String commentaire) {
        SoldeConge solde = lockBalance(demande);
        BigDecimal restant = solde.getJoursPris().subtract(jours);
        solde.setJoursPris(restant.max(BigDecimal.ZERO));      // garde-fou : jamais de jours pris négatifs
        recordMovement(solde, demande, TypeMouvement.REMBOURSEMENT, jours, commentaire, acteur);
    }

    // ---------- ajustement par les RH ----------

    /** Ajoute ou retire des jours au solde alloué d'un employé (commentaire obligatoire, jamais pour soi-même). */
    @Transactional
    public MouvementSoldeResponse adjust(UtilisateurConnecte acteur, Long employeId, AjustementSoldeRequest requete) {
        if (employeId.equals(acteur.getEmployeId())) {
            throw new AccessDeniedException("Vous ne pouvez pas ajuster votre propre solde");
        }
        if (requete.jours().signum() == 0) {
            throw new RequeteInvalideException("L'ajustement ne peut pas être nul");
        }
        int annee = checkedYear(requete.annee());

        Employe employe = employeRepository.findById(employeId)
                .orElseThrow(() -> new RessourceIntrouvableException("Employé introuvable (id=" + employeId + ")"));
        TypeConge type = typeCongeRepository.findById(requete.typeCongeId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Type de congé introuvable (id=" + requete.typeCongeId() + ")"));
        if (!type.isDeduitDuSolde()) {
            throw new RequeteInvalideException("Ce type de congé n'a pas de solde à ajuster");
        }

        SoldeConge solde = soldeRepository.findForUpdate(employeId, type.getId(), annee)
                .orElseGet(() -> createBalance(employe, type, annee));
        BigDecimal ajustement = requete.jours().setScale(1, RoundingMode.HALF_UP);
        BigDecimal nouveauAlloue = solde.getJoursAlloues().add(ajustement);
        if (nouveauAlloue.compareTo(solde.getJoursPris()) < 0) {
            throw new ConflitException("Le solde alloué (" + format(nouveauAlloue)
                    + ") ne peut pas être inférieur aux jours déjà pris (" + format(solde.getJoursPris()) + ")");
        }
        solde.setJoursAlloues(nouveauAlloue);

        Employe auteur = employeRepository.findById(acteur.getEmployeId())
                .orElseThrow(() -> new RessourceIntrouvableException("Employé introuvable"));
        MouvementSolde mouvement = recordMovement(solde, null, TypeMouvement.AJUSTEMENT, ajustement,
                requete.commentaire().trim(), auteur);
        return toMovementResponse(mouvement, nouveauAlloue.subtract(solde.getJoursPris()));
    }

    // ---------- méthodes privées ----------

    private List<SoldeResponse> balances(Long employeId, int annee) {
        return typeCongeRepository.findByDeduitDuSoldeTrueOrderByLibelle().stream().map(type -> {
            SoldeConge solde = soldeRepository
                    .findByEmployeIdAndTypeCongeIdAndAnnee(employeId, type.getId(), annee).orElse(null);
            BigDecimal alloues = solde != null ? solde.getJoursAlloues() : defaultAllowance(type);
            BigDecimal pris = solde != null ? solde.getJoursPris() : BigDecimal.ZERO;
            BigDecimal enAttente = pendingDays(employeId, type.getId(), annee);
            return new SoldeResponse(type.getId(), type.getCode(), type.getLibelle(), annee,
                    alloues, pris, enAttente, alloues.subtract(pris).subtract(enAttente));
        }).toList();
    }

    /** Historique de l'année, le plus récent d'abord, avec le solde disponible après chaque mouvement. */
    private List<MouvementSoldeResponse> history(Long employeId, int annee) {
        Map<Long, BigDecimal> cumul = new HashMap<>();
        List<MouvementSoldeResponse> resultat = new ArrayList<>();
        for (MouvementSolde mouvement : mouvementRepository.findHistory(employeId, annee)) {
            BigDecimal total = cumul.merge(mouvement.getSolde().getId(), mouvement.getJours(), BigDecimal::add);
            resultat.add(toMovementResponse(mouvement, total));
        }
        resultat.sort(Comparator.comparing(MouvementSoldeResponse::dateMouvement)
                .thenComparing(MouvementSoldeResponse::id)
                .reversed());
        return resultat;
    }

    private MouvementSoldeResponse toMovementResponse(MouvementSolde m, BigDecimal soldeApres) {
        String auteur = m.getCreePar() == null ? null : m.getCreePar().getPrenom() + " " + m.getCreePar().getNom();
        return new MouvementSoldeResponse(m.getId(), m.getDateMouvement(),
                m.getSolde().getTypeConge().getCode(), m.getSolde().getAnnee(), m.getTypeMouvement(),
                m.getJours(), soldeApres, m.getDemande() == null ? null : m.getDemande().getId(),
                m.getCommentaire(), auteur);
    }

    /** Verrouille le solde correspondant à la demande (le crée s'il n'existe pas). */
    private SoldeConge lockBalance(DemandeConge demande) {
        int annee = demande.getDateDebut().getYear();
        return soldeRepository
                .findForUpdate(demande.getEmploye().getId(), demande.getTypeConge().getId(), annee)
                .orElseGet(() -> createBalance(demande.getEmploye(), demande.getTypeConge(), annee));
    }

    private SoldeConge createBalance(Employe employe, TypeConge type, int annee) {
        SoldeConge solde = new SoldeConge();
        solde.setEmploye(employe);
        solde.setTypeConge(type);
        solde.setAnnee(annee);
        solde.setJoursAlloues(defaultAllowance(type));
        solde.setJoursPris(BigDecimal.ZERO);
        SoldeConge cree = soldeRepository.save(solde);
        recordMovement(cree, null, TypeMouvement.ALLOCATION, defaultAllowance(type), "Allocation annuelle " + annee, null);
        return cree;
    }

    private MouvementSolde recordMovement(SoldeConge solde, DemandeConge demande, TypeMouvement type,
                                          BigDecimal jours, String commentaire, Employe auteur) {
        MouvementSolde mouvement = new MouvementSolde();
        mouvement.setSolde(solde);
        mouvement.setDemande(demande);
        mouvement.setTypeMouvement(type);
        mouvement.setJours(jours);
        mouvement.setDateMouvement(LocalDateTime.now(clock));
        // colonne de 255 caractères : un motif plus long (500 max) est tronqué plutôt que de faire échouer l'opération
        mouvement.setCommentaire(commentaire == null || commentaire.length() <= 255 ? commentaire : commentaire.substring(0, 254) + "…");
        mouvement.setCreePar(auteur);
        mouvementRepository.save(mouvement);
        return mouvement;
    }

    private Employe loadVisibleEmployee(UtilisateurConnecte acteur, Long employeId) {
        return employeRepository.findById(employeId)
                .filter(e -> AccesConge.canView(acteur, e))
                .orElseThrow(() -> new RessourceIntrouvableException("Employé introuvable (id=" + employeId + ")"));
    }

    private BigDecimal pendingDays(Long employeId, Long typeId, int annee) {
        BigDecimal total = demandeRepository.sumJours(employeId, typeId, StatutDemandeConge.EN_ATTENTE,
                LocalDate.of(annee, 1, 1), LocalDate.of(annee, 12, 31));
        return Objects.requireNonNullElse(total, BigDecimal.ZERO);
    }

    private BigDecimal defaultAllowance(TypeConge type) {
        return type.getJoursParAn() != null ? type.getJoursParAn() : BigDecimal.ZERO;
    }

    private int checkedYear(Integer annee) {
        int cible = annee != null ? annee : LocalDate.now(clock).getYear();
        if (cible < 2000 || cible > 2100) {
            throw new RequeteInvalideException("Année invalide : " + cible);
        }
        return cible;
    }

    private String format(BigDecimal jours) {
        return jours.stripTrailingZeros().toPlainString();
    }
}