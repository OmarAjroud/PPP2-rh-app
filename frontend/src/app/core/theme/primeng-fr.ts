import { Translation } from 'primeng/api';

/** French texts and calendar conventions for PrimeNG (dd/mm/yy, Monday first). */
export const PRIMENG_FR: Translation = {
  dayNames: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'],
  dayNamesShort: ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'],
  dayNamesMin: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
  monthNames: ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'],
  monthNamesShort: ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'],
  today: "Aujourd'hui",
  clear: 'Effacer',
  dateFormat: 'dd/mm/yy',
  firstDayOfWeek: 1,
  weekHeader: 'Sem.',
  weak: 'Faible',
  medium: 'Moyen',
  strong: 'Fort',
  passwordPrompt: 'Saisissez un mot de passe',
  emptyMessage: 'Aucun résultat',
  emptyFilterMessage: 'Aucun résultat',
  emptySelectionMessage: 'Aucun élément sélectionné',
  selectionMessage: '{0} éléments sélectionnés',
  aria: {
    trueLabel: 'Vrai', falseLabel: 'Faux', nullLabel: 'Non sélectionné', star: '1 étoile', stars: '{star} étoiles',
    selectAll: 'Tout sélectionner', unselectAll: 'Tout désélectionner', close: 'Fermer', previous: 'Précédent', next: 'Suivant',
    navigation: 'Navigation', scrollTop: 'Haut de page', moveTop: 'Déplacer en haut', moveUp: 'Monter', moveDown: 'Descendre',
    moveBottom: 'Déplacer en bas', pageLabel: 'Page {page}', firstPageLabel: 'Première page', lastPageLabel: 'Dernière page',
    nextPageLabel: 'Page suivante', prevPageLabel: 'Page précédente', rowsPerPageLabel: 'Lignes par page', previousPageLabel: 'Page précédente', jumpToPageDropdownLabel: 'Aller à la page', jumpToPageInputLabel: 'Aller à la page',
  },
};
