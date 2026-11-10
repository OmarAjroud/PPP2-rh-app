/** One field that changed in an audited action. null: no value on that side (creation, deletion). */
export interface Difference {
  champ: string;
  avant: string | null;
  apres: string | null;
}

const ACTIONS: Record<string, string> = {
  CREATION: 'Création',
  MODIFICATION: 'Modification',
  SUPPRESSION: 'Suppression',
  CONNEXION: 'Connexion',
  ECHEC_CONNEXION: 'Échec de connexion',
  DECONNEXION: 'Déconnexion',
  INVITATION: 'Invitation',
  ACTIVATION_COMPTE: 'Activation de compte',
  CHANGEMENT_MOT_DE_PASSE: 'Changement de mot de passe',
  REINITIALISATION_MOT_DE_PASSE: 'Réinitialisation du mot de passe',
  APPEL_ASSISTANT: 'Appel de l’assistant',
  DESACTIVATION_COMPTE: 'Désactivation de compte',
  REACTIVATION_COMPTE: 'Réactivation de compte',
  EXPORT: 'Export',
};

/** Whether the action has its own French label (used to check every backend action is covered). */
export function actionConnue(action: string): boolean {
  return action in ACTIONS;
}

/** French label of an audit action; an unknown one is made readable ("NOUVELLE_ACTION" → "Nouvelle action"). */
export function libelleAction(action: string): string {
  if (ACTIONS[action]) return ACTIONS[action];
  const mots = action.toLowerCase().replace(/_/g, ' ');
  return mots.charAt(0).toUpperCase() + mots.slice(1);
}

function texte(v: unknown): string | null {
  if (v === null || v === undefined) return null;
  return typeof v === 'object' ? JSON.stringify(v) : String(v);
}

function estObjet(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

/** Fields that differ between the values before and after an action, sorted by name. */
export function differences(avant: unknown, apres: unknown): Difference[] {
  if (avant == null && apres == null) return [];
  if ((avant != null && !estObjet(avant)) || (apres != null && !estObjet(apres))) {
    const a = texte(avant), b = texte(apres);
    return a === b ? [] : [{ champ: 'valeur', avant: a, apres: b }];
  }
  const ancien = (avant ?? {}) as Record<string, unknown>;
  const nouveau = (apres ?? {}) as Record<string, unknown>;
  return [...new Set([...Object.keys(ancien), ...Object.keys(nouveau)])]
    .sort((x, y) => x.localeCompare(y))
    .map(champ => ({ champ, avant: avant == null ? null : texte(ancien[champ]), apres: apres == null ? null : texte(nouveau[champ]) }))
    .filter(d => d.avant !== d.apres);
}
