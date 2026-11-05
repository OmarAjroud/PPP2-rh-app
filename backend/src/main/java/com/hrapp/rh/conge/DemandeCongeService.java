package com.hrapp.rh.conge;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DemandeCongeService {

    /** Statuts qui « occupent » des jours : une nouvelle demande ne peut pas les chevaucher. */
    private static final List<StatutDemandeConge> STATUTS_ACTIFS =
            List.of(StatutDemandeConge.EN_ATTENTE, StatutDemandeConge.APPROUVEE);
    /** Nombre maximal de demandes en attente examinées pour « mes demandes à décider ». */
    private static final int MAX_A_DECIDER = 500;
    private static final DateTimeFormatter FORMAT_DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final DemandeCongeRepository repository;
    private final EmployeRepository employeRepository;
    private final TypeCongeRepository typeCongeRepository;
    private final SoldeCongeService soldeService;
    private final JoursOuvrablesService joursOuvrablesService;
    private final DemandeCongeMapper mapper;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final RegleDecisionConge regle;

    // ---------- demande ----------

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
        DemandeConge enregistree = repository.save(demande);
        if (regle.recours(enregistree).isPresent()) {
            // pas de manager en mesure de décider : la demande va directement aux RH (pas de relance plus tard)
            enregistree.setDateEscalade(enregistree.getDateCreation());
        }
        publish(DemandeCongeEvenement.Type.CREEE, enregistree);
        return mapper.toResponse(enregistree);
    }

    // ---------- consultation ----------

    /** Mes demandes, la plus récente d'abord, avec filtres optionnels. */
    @Transactional(readOnly = true)
    public PageReponse<DemandeCongeResponse> listMine(UtilisateurConnecte utilisateur, StatutDemandeConge statut,
                                                      Integer annee, int page, int taille) {
        Specification<DemandeConge> filtre = DemandeCongeSpecification.employe(utilisateur.getEmployeId())
                .and(DemandeCongeSpecification.statut(statut))
                .and(DemandeCongeSpecification.annee(annee));
        return PageReponse.from(repository.findAll(filtre, pageRequest(page, taille)).map(mapper::toResponse));
    }

    /** Demandes de mon équipe (les employés dont je suis le manager direct) : la « boîte de réception ». */
    @Transactional(readOnly = true)
    public PageReponse<DemandeCongeResponse> listTeam(UtilisateurConnecte acteur, StatutDemandeConge statut,
                                                      Integer annee, int page, int taille) {
        Specification<DemandeConge> filtre = DemandeCongeSpecification.equipeDe(acteur.getEmployeId())
                .and(DemandeCongeSpecification.statut(statut))
                .and(DemandeCongeSpecification.annee(annee));
        return PageReponse.from(repository.findAll(filtre, pageRequest(page, taille)).map(mapper::toResponse));
    }

    /** Vue globale pour les RH : toutes les demandes, avec filtres (statut, employé, département, période). */
    @Transactional(readOnly = true)
    public PageReponse<DemandeCongeResponse> listAll(StatutDemandeConge statut, Long employeId, Long departementId,
                                                     LocalDate du, LocalDate au, int page, int taille) {
        if (du != null && au != null && du.isAfter(au)) {
            throw new RequeteInvalideException("La date de début doit précéder la date de fin");
        }
        Specification<DemandeConge> filtre = DemandeCongeSpecification.employe(employeId)
                .and(DemandeCongeSpecification.departement(departementId))
                .and(DemandeCongeSpecification.statut(statut))
                .and(DemandeCongeSpecification.chevauche(du, au));
        return PageReponse.from(repository.findAll(filtre, pageRequest(page, taille)).map(mapper::toResponse));
    }

    /** Détail : visible par le demandeur, son manager d'équipe, ADMIN et RH (sinon 404). */
    @Transactional(readOnly = true)
    public DemandeCongeResponse findById(UtilisateurConnecte acteur, Long id) {
        return mapper.toResponse(findVisibleOrThrow(acteur, id));
    }

    // ---------- décision ----------

    /** Approuve une demande en attente. Un seul bloc transactionnel : déduction du solde, mouvement, décision. */
    @Transactional
    public DemandeCongeResponse approve(UtilisateurConnecte acteur, Long id, String motif) {
        DemandeConge demande = findVisibleOrThrow(acteur, id);
        verifyNotOwnRequest(acteur, demande);
        verifyPending(demande);
        verifyCanDecide(acteur, demande);
        Employe decideur = loadActor(acteur);

        if (demande.getTypeConge().isDeduitDuSolde()) {
            soldeService.deductDays(demande, decideur);       // verrou + vérification + mouvement
        }
        demande.setStatut(StatutDemandeConge.APPROUVEE);
        decide(demande, decideur, motif);
        publish(DemandeCongeEvenement.Type.APPROUVEE, demande);
        return mapper.toResponse(demande);
    }

    /** Refuse une demande en attente (le motif est obligatoire, le solde ne change pas). */
    @Transactional
    public DemandeCongeResponse reject(UtilisateurConnecte acteur, Long id, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new RequeteInvalideException("Le motif du refus est obligatoire");
        }
        DemandeConge demande = findVisibleOrThrow(acteur, id);
        verifyNotOwnRequest(acteur, demande);
        verifyPending(demande);
        verifyCanDecide(acteur, demande);
        Employe decideur = loadActor(acteur);

        demande.setStatut(StatutDemandeConge.REFUSEE);
        decide(demande, decideur, motif);
        publish(DemandeCongeEvenement.Type.REFUSEE, demande);
        return mapper.toResponse(demande);
    }

    // ---------- annulation ----------

    /** Annule ma demande : en attente, ou approuvée tant que le congé n'a pas commencé (les jours sont alors rendus). */
    @Transactional
    public DemandeCongeResponse cancel(UtilisateurConnecte utilisateur, Long id) {
        DemandeConge demande = findOwnOrThrow(utilisateur, id);
        switch (demande.getStatut()) {
            case EN_ATTENTE -> { /* rien à rendre : les jours n'étaient que réservés */ }
            case APPROUVEE -> cancelApproved(demande);
            default -> throw new ConflitException("Cette demande ne peut plus être annulée (statut actuel : "
                    + demande.getStatut().getLibelle() + ")");
        }
        demande.setStatut(StatutDemandeConge.ANNULEE);
        publish(DemandeCongeEvenement.Type.ANNULEE, demande);
        return mapper.toResponse(demande);
    }

    /**
     * Interruption d'un congé approuvé par les RH (retour anticipé, raison exceptionnelle), motif obligatoire.
     * Pas encore commencé, ou commencé aujourd'hui : annulé, tous les jours sont rendus.
     * En cours : il se termine la veille, seuls les jours non pris sont rendus. Terminé : impossible.
     */
    @Transactional
    public DemandeCongeResponse interrupt(UtilisateurConnecte acteur, Long id, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new RequeteInvalideException("Le motif de l'interruption est obligatoire");
        }
        DemandeConge demande = findVisibleOrThrow(acteur, id);
        if (!acteur.isAdminOrRh()) {
            throw new AccessDeniedException("Seuls les RH et les administrateurs peuvent interrompre un congé");
        }
        if (demande.getEmploye().getId().equals(acteur.getEmployeId())) {
            throw new AccessDeniedException("Vous ne pouvez pas interrompre votre propre congé");
        }
        if (demande.getStatut() != StatutDemandeConge.APPROUVEE) {
            throw new ConflitException("Seul un congé approuvé peut être interrompu (statut actuel : "
                    + demande.getStatut().getLibelle() + ")");
        }
        LocalDate aujourdhui = LocalDate.now(clock);
        if (demande.getDateFin().isBefore(aujourdhui)) {
            throw new ConflitException("Ce congé est terminé : il ne peut plus être interrompu");
        }
        Employe auteur = loadActor(acteur);
        String commentaire = "Interruption par les RH : " + motif.trim();
        // jours ouvrables déjà pris : du début jusqu'à la veille (0 si le congé n'a pas commencé)
        BigDecimal joursPris = demande.getDateDebut().isBefore(aujourdhui)
                ? BigDecimal.valueOf(joursOuvrablesService.compter(demande.getDateDebut(), aujourdhui.minusDays(1))).setScale(1)
                : BigDecimal.ZERO.setScale(1);
        BigDecimal joursRendus = demande.getNombreJours().subtract(joursPris);
        if (joursPris.signum() == 0) {
            // rien n'a encore été pris (pas commencé, commence aujourd'hui, ou seulement un week-end) : annulé
            demande.setStatut(StatutDemandeConge.ANNULEE);
        } else {
            // en cours : le congé s'arrête la veille, les jours déjà pris restent décomptés
            demande.setDateFin(aujourdhui.minusDays(1));
            demande.setNombreJours(joursPris);
        }
        if (demande.getTypeConge().isDeduitDuSolde() && joursRendus.signum() > 0) {
            soldeService.refundDays(demande, joursRendus, auteur, commentaire);
        }
        demande.setMotifInterruption(motif.trim());
        publish(DemandeCongeEvenement.Type.INTERROMPUE, demande);
        return mapper.toResponse(demande);
    }

    // ---------- demandes à décider ----------

    /**
     * Les demandes en attente que JE peux valider ou refuser maintenant, la plus ancienne d'abord :
     * pour un manager, son équipe ; pour les RH et l'ADMIN, les demandes en recours.
     */
    @Transactional(readOnly = true)
    public PageReponse<DemandeCongeResponse> listToDecide(UtilisateurConnecte acteur, int page, int taille) {
        Specification<DemandeConge> perimetre = acteur.isAdminOrRh()
                ? DemandeCongeSpecification.statut(StatutDemandeConge.EN_ATTENTE)
                : DemandeCongeSpecification.equipeDe(acteur.getEmployeId())
                        .and(DemandeCongeSpecification.statut(StatutDemandeConge.EN_ATTENTE));
        // le filtre « puis-je décider ? » dépend de l'état des comptes : il est appliqué ici, sur un volume borné
        List<DemandeConge> aDecider = repository.findAll(perimetre,
                        PageRequest.of(0, MAX_A_DECIDER, Sort.by(Sort.Direction.ASC, "dateCreation", "id")))
                .stream()
                .filter(d -> regle.canDecide(acteur, d))
                .toList();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), 100));
        int debut = (int) Math.min(pageable.getOffset(), aDecider.size());
        int fin = Math.min(debut + pageable.getPageSize(), aDecider.size());
        return PageReponse.from(new PageImpl<>(
                aDecider.subList(debut, fin).stream().map(mapper::toResponse).toList(), pageable, aDecider.size()));
    }

    // ---------- méthodes privées ----------

    /** Le manager direct décide ; les RH et l'ADMIN seulement en recours (voir RegleDecisionConge). */
    private void verifyCanDecide(UtilisateurConnecte acteur, DemandeConge demande) {
        if (regle.canDecide(acteur, demande)) {
            return;
        }
        String manager = regle.activeManager(demande)
                .map(m -> m.getPrenom() + " " + m.getNom())
                .orElse("son manager");
        throw new AccessDeniedException("Cette demande est à traiter par " + manager + ". Les RH pourront la traiter "
                + "à partir du " + regle.escalationDate(demande).format(FORMAT_DATE_HEURE)
                + " si elle est toujours en attente");
    }

    /** Prévient les écouteurs (notifications) ; ils s'exécutent dans cette même transaction. */
    private void publish(DemandeCongeEvenement.Type type, DemandeConge demande) {
        eventPublisher.publishEvent(new DemandeCongeEvenement(type, demande));
    }

    private void cancelApproved(DemandeConge demande) {
        if (!demande.getDateDebut().isAfter(LocalDate.now(clock))) {
            throw new ConflitException(
                    "Un congé déjà commencé ne peut plus être annulé : les ressources humaines peuvent l'interrompre");
        }
        if (demande.getTypeConge().isDeduitDuSolde()) {
            soldeService.refundDays(demande, demande.getEmploye());
        }
    }

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

    /** Visible par le demandeur, son manager d'équipe, ADMIN et RH ; introuvable pour tous les autres. */
    private DemandeConge findVisibleOrThrow(UtilisateurConnecte acteur, Long id) {
        return repository.findById(id)
                .filter(d -> AccesConge.canView(acteur, d.getEmploye()))
                .orElseThrow(() -> new RessourceIntrouvableException("Demande de congé introuvable (id=" + id + ")"));
    }

    /** Personne ne valide ni ne refuse sa propre demande (ADMIN et RH compris). */
    private void verifyNotOwnRequest(UtilisateurConnecte acteur, DemandeConge demande) {
        if (demande.getEmploye().getId().equals(acteur.getEmployeId())) {
            throw new AccessDeniedException("Vous ne pouvez pas valider ou refuser votre propre demande");
        }
    }

    private void verifyPending(DemandeConge demande) {
        if (demande.getStatut() != StatutDemandeConge.EN_ATTENTE) {
            throw new ConflitException("Cette demande a déjà été traitée (statut actuel : "
                    + demande.getStatut().getLibelle() + ")");
        }
    }

    private Employe loadActor(UtilisateurConnecte acteur) {
        return employeRepository.findById(acteur.getEmployeId())
                .orElseThrow(() -> new RessourceIntrouvableException("Employé introuvable"));
    }

    private void decide(DemandeConge demande, Employe decideur, String motif) {
        demande.setDecidePar(decideur);
        demande.setDateDecision(LocalDateTime.now(clock));
        demande.setMotifDecision(cleanComment(motif));
    }

    private PageRequest pageRequest(int page, int taille) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), 100),
                Sort.by(Sort.Direction.DESC, "dateDebut", "id"));
    }

    private String cleanComment(String commentaire) {
        return commentaire == null || commentaire.isBlank() ? null : commentaire.trim();
    }

    /** 22.0 devient « 22 », 4.5 reste « 4.5 ». */
    private String format(BigDecimal jours) {
        return jours.stripTrailingZeros().toPlainString();
    }
}