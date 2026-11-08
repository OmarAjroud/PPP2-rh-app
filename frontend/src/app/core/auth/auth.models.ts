export type Role = 'ADMIN' | 'RH' | 'MANAGER' | 'EMPLOYE';

/** Backend ProfilResponse. */
export interface Profil {
  nomUtilisateur: string;
  role: Role;
  employeId: number;
  departementId: number | null;
}

/** Backend AuthResponse (login, refresh, password change). */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number; // seconds
  profil: Profil;
}

/** What the browser keeps between reloads and shares between tabs. */
export interface Session {
  accessToken: string;
  refreshToken: string;
  expiresAt: number; // epoch ms, informational
  profil: Profil;
}

export const LIBELLES_ROLE: Record<Role, string> = {
  ADMIN: 'Administrateur',
  RH: 'Ressources humaines',
  MANAGER: 'Manager',
  EMPLOYE: 'Employé',
};

/** Mirrors the backend RegleMotDePasse: 8 to 72 characters, at least one letter and one digit. */
export const REGLE_MOT_DE_PASSE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;
export const MESSAGE_MOT_DE_PASSE = 'De 8 à 72 caractères, avec au moins une lettre et un chiffre.';
