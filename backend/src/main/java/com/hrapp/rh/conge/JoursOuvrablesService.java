package com.hrapp.rh.conge;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JoursOuvrablesService {

    private static final long DUREE_MAX_JOURS = 366;

    private final JourFerieRepository jourFerieRepository;
    private final CongeProperties properties;

    /** Nombre de jours ouvrables entre deux dates incluses (jours fériés lus en base). */
    @Transactional(readOnly = true)
    public int compter(LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut)) {
            throw new RequeteInvalideException("La date de fin doit être postérieure ou égale à la date de début");
        }
        if (ChronoUnit.DAYS.between(debut, fin) > DUREE_MAX_JOURS) {
            throw new RequeteInvalideException("La période ne peut pas dépasser 366 jours");
        }
        Set<LocalDate> feries = jourFerieRepository.findByDateFerieBetweenOrderByDateFerie(debut, fin).stream()
                .map(JourFerie::getDateFerie)
                .collect(Collectors.toSet());
        return CalculJoursOuvrables.compter(debut, fin, properties.weekendDays(), feries);
    }

    /** Aperçu pour le formulaire de demande : combien de jours seront décomptés ? */
    @Transactional(readOnly = true)
    public JoursOuvrablesResponse calculate(LocalDate debut, LocalDate fin) {
        int ouvrables = compter(debut, fin);
        return new JoursOuvrablesResponse(debut, fin, ouvrables, ChronoUnit.DAYS.between(debut, fin) + 1);
    }
}