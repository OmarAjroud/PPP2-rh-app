/** Mirrors of the backend DTOs (backend/src/main/java/com/hrapp/rh/**). */

export interface PageReponse<T> {
  contenu: T[];
  numeroPage: number;
  taillePage: number;
  totalElements: number;
  totalPages: number;
  dernierePage: boolean;
}

export interface Employe {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  departementId: number;
  departementNom: string;
  roleId: number;
  roleNom: string;
}

export interface EmployeDetail extends Employe {
  dateEmbauche: string; // yyyy-MM-dd
  salaire: number | null; // null: not set, or not visible to the current user
  managerId: number | null;
  managerNomComplet: string | null;
}

export interface EmployeRequest {
  nom: string;
  prenom: string;
  email: string;
  dateEmbauche: string;
  salaire: number | null;
  departementId: number;
  roleId: number;
  managerId: number | null;
}

export interface EmployeResume {
  id: number;
  nom: string;
  prenom: string;
  email: string;
}

export interface Departement {
  id: number;
  nom: string;
  description: string | null;
  nombreEmployes: number;
}

export interface DepartementDetail {
  id: number;
  nom: string;
  description: string | null;
  employes: EmployeResume[];
}

export interface Role {
  id: number;
  nom: string;
  description: string | null;
}

/** Payload for departments and roles. */
export interface NomDescription {
  nom: string;
  description: string | null;
}

export type StatutCompte = 'EN_ATTENTE' | 'ACTIF' | 'DESACTIVE';

export interface Compte {
  id: number;
  employeId: number;
  nomUtilisateur: string;
  statut: StatutCompte;
}

// ---- Leave (read-only here; requesting and deciding come with the leave screens) ----

export type StatutDemande = 'EN_ATTENTE' | 'APPROUVEE' | 'REFUSEE' | 'ANNULEE';

export interface Solde {
  typeCongeId: number;
  typeCongeCode: string;
  typeCongeLibelle: string;
  annee: number;
  joursAlloues: number;
  joursPris: number;
  joursEnAttente: number;
  joursDisponibles: number; // alloués − pris − en attente
}

export interface DemandeConge {
  id: number;
  employeId: number;
  employeNomComplet: string;
  employeDepartementNom: string;
  typeCongeId: number;
  typeCongeCode: string;
  typeCongeLibelle: string;
  dateDebut: string;
  dateFin: string;
  nombreJours: number;
  commentaire: string | null;
  statut: StatutDemande;
  dateCreation: string;
  decideParNomComplet: string | null;
  dateDecision: string | null;
  motifDecision: string | null;
  dateEscalade: string | null;
  motifInterruption: string | null;
}

export interface TypeConge {
  id: number;
  code: string;
  libelle: string;
  deduitDuSolde: boolean;
  joursParAn: number;
  retroactifAutorise: boolean;
}

/** Working days between two dates (weekends and public holidays excluded), computed by the API. */
export interface JoursOuvrables {
  dateDebut: string;
  dateFin: string;
  joursOuvrables: number;
  joursCalendaires: number;
}

export interface DemandeCongeRequest {
  typeCongeId: number;
  dateDebut: string; // yyyy-MM-dd
  dateFin: string;
  commentaire?: string;
}

export type TypeNotification = 'CONGE_DEMANDE' | 'CONGE_APPROUVE' | 'CONGE_REFUSE' | 'CONGE_ANNULE' | 'CONGE_A_TRAITER' | string;

export interface Notification {
  id: number;
  type: TypeNotification;
  titre: string;
  message: string;
  lien: string | null; // front-end path, e.g. /leave-requests/42
  lue: boolean;
  dateCreation: string;
  dateLecture: string | null;
}

/** Dashboard for a year and a scope (one department, or the whole company when departementId is null). */
export interface TableauDeBord {
  annee: number;
  departementId: number | null;
  indicateurs: { nombreEmployes: number; demandesEnAttente: number; embauchesDuMois: number; absentsAujourdhui: number };
  effectifParDepartement: { departementId: number; departementNom: string; nombreEmployes: number }[];
  /** mois: 1 to 12, always the 12 months. */
  embauchesParMois: { mois: number; nombre: number }[];
  repartitionConges: { typeCongeCode: string; typeCongeLibelle: string; nombreDemandes: number; nombreJours: number }[];
  /** ADMIN and RH only: null for managers. */
  masseSalariale: { departementId: number; departementNom: string; nombreEmployes: number; total: number; moyenne: number }[] | null;
}

export interface AuditEntree {
  id: number;
  dateAction: string;
  utilisateurId: number | null;
  nomUtilisateur: string | null;
  action: string;
  entite: string | null;
  entiteId: string | null;
  anciennesValeurs: unknown;
  nouvellesValeurs: unknown;
  details: string | null;
  adresseIp: string | null;
}

export interface StatutAssistant {
  moteur: string;
  modele: string;
  disponible: boolean;
  outils: string[];
}

export interface ReponseAssistant {
  reponse: string;
  /** HR tools the assistant called to answer, in order. */
  outilsUtilises: string[];
}

export type TypeMouvement = 'ALLOCATION' | 'DEDUCTION' | 'REMBOURSEMENT' | 'AJUSTEMENT';

/** One line of a balance history: the movement (signed: deductions are negative) and the balance right after. */
export interface MouvementSolde {
  id: number;
  dateMouvement: string;
  typeCongeCode: string;
  annee: number;
  typeMouvement: TypeMouvement;
  jours: number;
  soldeApres: number;
  demandeId: number | null;
  commentaire: string | null;
  creeParNomComplet: string | null;
}
