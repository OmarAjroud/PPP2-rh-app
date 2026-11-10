import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Compte, Departement, DepartementDetail, Employe, EmployeDetail, EmployeRequest,
  DemandeConge, DemandeCongeRequest, JoursOuvrables, NomDescription, Notification, TableauDeBord, AuditEntree, StatutAssistant, ReponseAssistant, MouvementSolde, JourFerie, PageReponse, Role, Solde, StatutDemande, TypeConge,
} from './models';

export interface RechercheAudit {
  nomUtilisateur?: string | null;
  action?: string | null;
  entite?: string | null;
  du?: string | null; // yyyy-MM-dd
  au?: string | null;
  page: number;
  size: number;
}

export interface RechercheDemandes {
  statut?: StatutDemande | null;
  page: number;
  size: number;
}

export interface RechercheEmployes {
  recherche?: string;
  departementId?: number | null;
  roleId?: number | null;
  page: number;
  size: number;
  sort: string; // "nom,asc"
}

/** Employees, departments, roles and accounts endpoints. Access rules are enforced by the API. */
@Injectable({ providedIn: 'root' })
export class RhApi {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/api`;

  // ---- Employees ----
  employes(r: RechercheEmployes): Observable<PageReponse<Employe>> {
    let params = new HttpParams().set('page', r.page).set('size', r.size).set('sort', r.sort);
    if (r.recherche?.trim()) params = params.set('recherche', r.recherche.trim());
    if (r.departementId) params = params.set('departementId', r.departementId);
    if (r.roleId) params = params.set('roleId', r.roleId);
    return this.http.get<PageReponse<Employe>>(`${this.api}/employes`, { params });
  }
  employe(id: number): Observable<EmployeDetail> { return this.http.get<EmployeDetail>(`${this.api}/employes/${id}`); }
  monProfil(): Observable<EmployeDetail> { return this.http.get<EmployeDetail>(`${this.api}/employes/me`); }
  creerEmploye(e: EmployeRequest): Observable<EmployeDetail> { return this.http.post<EmployeDetail>(`${this.api}/employes`, e); }
  modifierEmploye(id: number, e: EmployeRequest): Observable<EmployeDetail> { return this.http.put<EmployeDetail>(`${this.api}/employes/${id}`, e); }
  supprimerEmploye(id: number): Observable<void> { return this.http.delete<void>(`${this.api}/employes/${id}`); }

  // ---- Accounts (ADMIN, RH) ----
  compte(employeId: number): Observable<Compte> { return this.http.get<Compte>(`${this.api}/employes/${employeId}/compte`); }
  inviter(employeId: number): Observable<Compte> { return this.http.post<Compte>(`${this.api}/employes/${employeId}/invitation`, {}); }
  desactiver(employeId: number): Observable<Compte> { return this.http.post<Compte>(`${this.api}/employes/${employeId}/compte/desactivation`, {}); }
  reactiver(employeId: number): Observable<Compte> { return this.http.post<Compte>(`${this.api}/employes/${employeId}/compte/reactivation`, {}); }

  // ---- Departments ----
  departements(): Observable<Departement[]> { return this.http.get<Departement[]>(`${this.api}/departements`); }
  departement(id: number): Observable<DepartementDetail> { return this.http.get<DepartementDetail>(`${this.api}/departements/${id}`); }
  creerDepartement(d: NomDescription): Observable<Departement> { return this.http.post<Departement>(`${this.api}/departements`, d); }
  modifierDepartement(id: number, d: NomDescription): Observable<Departement> { return this.http.put<Departement>(`${this.api}/departements/${id}`, d); }
  supprimerDepartement(id: number): Observable<void> { return this.http.delete<void>(`${this.api}/departements/${id}`); }

  // ---- Leave (read) ----
  /** My balances for a year (the current year when omitted). */
  mesSoldes(annee?: number): Observable<Solde[]> {
    return this.http.get<Solde[]>(`${this.api}/conges/soldes/moi`, annee ? { params: { annee } } : {});
  }
  soldes(employeId: number): Observable<Solde[]> { return this.http.get<Solde[]>(`${this.api}/conges/soldes/${employeId}`); }
  mesDemandes(size = 5): Observable<PageReponse<DemandeConge>> { return this.http.get<PageReponse<DemandeConge>>(`${this.api}/conges/demandes/moi`, { params: { size } }); }
  /** ADMIN and RH only (global view). */
  demandesDe(employeId: number, size = 5): Observable<PageReponse<DemandeConge>> { return this.http.get<PageReponse<DemandeConge>>(`${this.api}/conges/demandes`, { params: { employeId, size } }); }

  // ---- Leave requests ----
  typesConge(): Observable<TypeConge[]> { return this.http.get<TypeConge[]>(`${this.api}/types-conge`); }
  joursOuvrables(dateDebut: string, dateFin: string): Observable<JoursOuvrables> {
    return this.http.get<JoursOuvrables>(`${this.api}/conges/demandes/jours-ouvrables`, { params: { dateDebut, dateFin } });
  }
  creerDemande(r: DemandeCongeRequest): Observable<DemandeConge> { return this.http.post<DemandeConge>(`${this.api}/conges/demandes`, r); }
  demande(id: number): Observable<DemandeConge> { return this.http.get<DemandeConge>(`${this.api}/conges/demandes/${id}`); }
  annulerDemande(id: number): Observable<DemandeConge> { return this.http.post<DemandeConge>(`${this.api}/conges/demandes/${id}/annulation`, {}); }
  validerDemande(id: number, motif?: string): Observable<DemandeConge> {
    return this.http.post<DemandeConge>(`${this.api}/conges/demandes/${id}/validation`, motif?.trim() ? { motif: motif.trim() } : {});
  }
  refuserDemande(id: number, motif: string): Observable<DemandeConge> { return this.http.post<DemandeConge>(`${this.api}/conges/demandes/${id}/refus`, { motif }); }
  interrompreDemande(id: number, motif: string): Observable<DemandeConge> { return this.http.post<DemandeConge>(`${this.api}/conges/demandes/${id}/interruption`, { motif }); }
  mesDemandesPage(r: RechercheDemandes): Observable<PageReponse<DemandeConge>> { return this.http.get<PageReponse<DemandeConge>>(`${this.api}/conges/demandes/moi`, { params: paramsDemandes(r) }); }
  /** MANAGER, RH, ADMIN: what the caller can decide now. */
  aDecider(page = 0, size = 10): Observable<PageReponse<DemandeConge>> { return this.http.get<PageReponse<DemandeConge>>(`${this.api}/conges/demandes/a-decider`, { params: { page, size } }); }
  /** MANAGER, RH, ADMIN: the caller's team. */
  demandesEquipe(r: RechercheDemandes): Observable<PageReponse<DemandeConge>> { return this.http.get<PageReponse<DemandeConge>>(`${this.api}/conges/demandes/equipe`, { params: paramsDemandes(r) }); }
  /** RH, ADMIN: every request. */
  toutesDemandes(r: RechercheDemandes): Observable<PageReponse<DemandeConge>> { return this.http.get<PageReponse<DemandeConge>>(`${this.api}/conges/demandes`, { params: paramsDemandes(r) }); }

  // ---- Notifications ----
  notifications(nonLues: boolean, page = 0, size = 10): Observable<PageReponse<Notification>> {
    return this.http.get<PageReponse<Notification>>(`${this.api}/notifications`, { params: { nonLues, page, size } });
  }
  nombreNonLues(): Observable<{ nombre: number }> { return this.http.get<{ nombre: number }>(`${this.api}/notifications/non-lues/nombre`); }
  marquerLue(id: number): Observable<Notification> { return this.http.post<Notification>(`${this.api}/notifications/${id}/lecture`, {}); }
  toutMarquerLu(): Observable<{ nombre: number }> { return this.http.post<{ nombre: number }>(`${this.api}/notifications/lecture`, {}); }
  /** One-use ticket (30 s) to open the stream: EventSource cannot send the Authorization header. */
  ticketFlux(): Observable<{ ticket: string; expireDans: number }> { return this.http.post<{ ticket: string; expireDans: number }>(`${this.api}/notifications/flux/ticket`, {}); }
  urlFlux(ticket: string): string { return `${this.api}/notifications/flux?ticket=${encodeURIComponent(ticket)}`; }

  // ---- Dashboard (ADMIN, RH, MANAGER) ----
  tableauDeBord(annee?: number, departementId?: number | null): Observable<TableauDeBord> {
    let params = new HttpParams();
    if (annee) params = params.set('annee', annee);
    if (departementId) params = params.set('departementId', departementId);
    return this.http.get<TableauDeBord>(`${this.api}/tableau-de-bord`, { params });
  }

  // ---- Audit log (ADMIN) ----
  audit(r: RechercheAudit): Observable<PageReponse<AuditEntree>> {
    let params = new HttpParams().set('page', r.page).set('size', r.size);
    for (const cle of ['nomUtilisateur', 'action', 'entite', 'du', 'au'] as const) {
      const v = r[cle]?.trim();
      if (v) params = params.set(cle, v);
    }
    return this.http.get<PageReponse<AuditEntree>>(`${this.api}/admin/audit`, { params });
  }
  actionsAudit(): Observable<string[]> { return this.http.get<string[]>(`${this.api}/admin/audit/actions`); }
  entitesAudit(): Observable<string[]> { return this.http.get<string[]>(`${this.api}/admin/audit/entites`); }

  // ---- HR assistant ----
  statutAssistant(): Observable<StatutAssistant> { return this.http.get<StatutAssistant>(`${this.api}/assistant/statut`); }
  /** The API keeps no history: the whole conversation is sent, the new question last. */
  questionAssistant(messages: { role: 'utilisateur' | 'assistant'; contenu: string }[]): Observable<ReponseAssistant> {
    return this.http.post<ReponseAssistant>(`${this.api}/assistant/chat`, { messages });
  }

  // ---- Balance history and adjustments ----
  /** History of a balance for a year: mine (employeId null) or an employee's (API checks the right to see it). */
  mouvementsSolde(employeId: number | null, annee: number): Observable<MouvementSolde[]> {
    const chemin = employeId === null ? 'moi' : String(employeId);
    return this.http.get<MouvementSolde[]>(`${this.api}/conges/soldes/${chemin}/mouvements`, { params: { annee } });
  }
  /** ADMIN, RH: add (positive) or remove (negative) days, with a reason. */
  ajusterSolde(employeId: number, a: { typeCongeId: number; annee: number; jours: number; commentaire: string }): Observable<MouvementSolde> {
    return this.http.post<MouvementSolde>(`${this.api}/conges/soldes/${employeId}/ajustements`, a);
  }

  // ---- Public holidays (read: everyone; write: ADMIN, RH) ----
  joursFeries(annee: number): Observable<JourFerie[]> { return this.http.get<JourFerie[]>(`${this.api}/jours-feries`, { params: { annee } }); }
  ajouterJourFerie(j: { date: string; libelle: string }): Observable<JourFerie> { return this.http.post<JourFerie>(`${this.api}/jours-feries`, j); }
  supprimerJourFerie(id: number): Observable<void> { return this.http.delete<void>(`${this.api}/jours-feries/${id}`); }

  // ---- Roles ----
  roles(): Observable<Role[]> { return this.http.get<Role[]>(`${this.api}/roles`); }
  role(id: number): Observable<Role> { return this.http.get<Role>(`${this.api}/roles/${id}`); }
  creerRole(r: NomDescription): Observable<Role> { return this.http.post<Role>(`${this.api}/roles`, r); }
  modifierRole(id: number, r: NomDescription): Observable<Role> { return this.http.put<Role>(`${this.api}/roles/${id}`, r); }
  supprimerRole(id: number): Observable<void> { return this.http.delete<void>(`${this.api}/roles/${id}`); }
}

function paramsDemandes(r: RechercheDemandes): HttpParams {
  let p = new HttpParams().set('page', r.page).set('size', r.size);
  if (r.statut) p = p.set('statut', r.statut);
  return p;
}
