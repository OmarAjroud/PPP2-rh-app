package com.hrapp.rh.conge;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DemandeCongeService {

    /** Statuts qui « occupent » des jours : une nouvelle demande ne peut pas les chevaucher. */
    private static final List<StatutDemandeConge> STATUTS_ACTIFS =
            List.of(StatutDemandeConge.EN_ATTENTE, StatutDemandeConge.APPROUVEE);

    private final DemandeCongeRepository repository;
    private final EmployeRepository employeRepository;
    private final TypeCongeRepository typeCongeRepository;
    private final SoldeCongeService soldeService;
    private final JoursOuvrablesService joursOuvrablesService;
    private final DemandeCongeMapper mapper;
    private final Clock clock;

    /** Crée une demande pour l'utilisateur connecté (jamais pour quelqu'un d'autre). */
    @Transactional
    public DemandeCongeResponse create(UtilisateurConnecte utilisateur, DemandeCongeRequest requete) {
        // Verrou : les demandes d'un même employé sont traitées l'une après l'autre
        Employe employe = employeRepository.findByIdForUpdate(utilisateur.getEmployeId())
                .orElseThrow(() -> new RessourceIntrouvableException("Employé introuvable"));
        TypeConge type = typeCongeRepository.findById(requete.typeCongeId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Type de congé introuvable (id=" + requete.typeCongeId() + ")"));

        LocalDate debut = requete.dateDebut();
        LocalDate fin = requete.dateFin();
        verifyDates(type, debut, fin);

        int jours = joursOuvrablesService.compter(debut, fin);
        if (jours < 1) {
            throw new RequeteInvalideException("La période demandée ne contient aucun jour ouvrable");
        }

        if (repository.existsByEmployeIdAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                employe.getId(), STATUTS_ACTIFS, fin, debut)) {
            throw new ConflitException(
                    "Vous avez déjà une demande en attente ou approuvée qui chevauche cette période");
        }

        BigDecimal nombreJours = BigDecimal.valueOf(jours).setScale(1);
        if (type.isDeduitDuSolde()) {
            BigDecimal disponible = soldeService.calculateAvailable(employe, type, debut.getYear());
            if (nombreJours.compareTo(disponible) > 0) {
                throw new ConflitException("Solde insuffisant : " + format(disponible)
                        + " jour(s) disponible(s), " + format(nombreJours) + " demandé(s)");
            }
        }

        DemandeConge demande = new DemandeConge();
        demande.setEmploye(employe);
        demande.setTypeConge(type);
        demande.setDateDebut(debut);
        demande.setDateFin(fin);
        demande.setNombreJours(nombreJours);
        demande.setCommentaire(cleanComment(requete.commentaire()));
        demande.setStatut(StatutDemandeConge.EN_ATTENTE);
        demande.setDateCreation(LocalDateTime.now(clock));
        return mapper.toResponse(repository.save(demande));
    }

    /** Mes demandes, la plus récente d'abord, avec filtres optionnels. */
    @Transactional(readOnly = true)
    public PageReponse<DemandeCongeResponse> listMine(UtilisateurConnecte utilisateur, StatutDemandeConge statut,
                                                      Integer annee, int page, int taille) {
        Specification<DemandeConge> filtre = DemandeCongeSpecification.employe(utilisateur.getEmployeId())
                .and(DemandeCongeSpecification.statut(statut))
                .and(DemandeCongeSpecification.annee(annee));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), 100),
                Sort.by(Sort.Direction.DESC, "dateDebut", "id"));
        return PageReponse.from(repository.findAll(filtre, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public DemandeCongeResponse findById(UtilisateurConnecte utilisateur, Long id) {
        return mapper.toResponse(findOwnOrThrow(utilisateur, id));
    }

    /** Annule ma demande tant qu'elle est en attente. */
    @Transactional
    public DemandeCongeResponse cancel(UtilisateurConnecte utilisateur, Long id) {
        DemandeConge demande = findOwnOrThrow(utilisateur, id);
        if (demande.getStatut() != StatutDemandeConge.EN_ATTENTE) {
            throw new ConflitException("Seule une demande en attente peut être annulée (statut actuel : "
                    + demande.getStatut().getLibelle() + ")");
        }
        demande.setStatut(StatutDemandeConge.ANNULEE);
        return mapper.toResponse(demande);
    }

    // ---------- méthodes privées ----------

    private void verifyDates(TypeConge type, LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut)) {
            throw new RequeteInvalideException("La date de fin doit être postérieure ou égale à la date de début");
        }
        if (debut.getYear() != fin.getYear()) {
            throw new RequeteInvalideException(
                    "Une demande ne peut pas chevaucher deux années : faites une demande par année");
        }
        LocalDate aujourdhui = LocalDate.now(clock);
        int anneeCourante = aujourdhui.getYear();
        int anneeMin = type.isRetroactifAutorise() ? anneeCourante - 1 : anneeCourante;
        if (debut.getYear() < anneeMin || debut.getYear() > anneeCourante + 1) {
            throw new RequeteInvalideException("Les demandes ne sont acceptées que pour "
                    + anneeMin + " à " + (anneeCourante + 1));
        }
        if (!type.isRetroactifAutorise() && debut.isBefore(aujourdhui)) {
            throw new RequeteInvalideException("Cette demande ne peut pas commencer dans le passé");
        }
    }

    /** Une demande d'un autre employé est présentée comme introuvable (on ne révèle pas son existence). */
    private DemandeConge findOwnOrThrow(UtilisateurConnecte utilisateur, Long id) {
        return repository.findById(id)
                .filter(d -> d.getEmploye().getId().equals(utilisateur.getEmployeId()))
                .orElseThrow(() -> new RessourceIntrouvableException("Demande de congé introuvable (id=" + id + ")"));
    }

    private String cleanComment(String commentaire) {
        return commentaire == null || commentaire.isBlank() ? null : commentaire.trim();
    }

    /** 22.0 devient « 22 », 4.5 reste « 4.5 ». */
    private String format(BigDecimal jours) {
        return jours.stripTrailingZeros().toPlainString();
    }
}