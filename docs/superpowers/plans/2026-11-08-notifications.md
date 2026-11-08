# Notifications (étape 15) : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Voir ses notifications (demande à décider, décision, annulation, transmission aux RH) en temps réel, avec un compteur de non-lues.

**Architecture:** Un service racine `Notifications` ouvre le flux SSE du back-end quand une session existe : `POST /api/notifications/flux/ticket` (JWT) → `new EventSource('/api/notifications/flux?ticket=…')`. Il tient le compteur de non-lues (événements `non-lues` et `notification`) et la dernière notification reçue (toast). Une page liste les notifications. Aucun changement côté back-end.

**Tech Stack:** Angular 21, EventSource natif, PrimeNG 21, Vitest.

## Décisions (déléguées par l'utilisateur à Claude)

- Rail : entrée « Notifications » (tous les rôles) avec le nombre de non-lues ; point sur le bouton menu en mobile.
- Page `/notifications` : toutes / non lues, paginée ; cliquer une notification la marque lue et ouvre son lien ; « Tout marquer comme lu ».
- Toast en bas à droite à l'arrivée d'une notification (titre, message, lien « Voir »), fermé seul après 8 s ou à la main ; annoncé aux lecteurs d'écran (`role="status"`).
- Reconnexion : à l'erreur ou à la fermeture du flux, nouveau ticket puis réouverture après 5 s, 10 s, 20 s… jusqu'à 60 s ; remise à 5 s dès qu'un événement arrive. Fermeture à la déconnexion.
- Sans `EventSource` (tests, vieux navigateur) : pas de flux, le compteur est lu une fois via `GET /non-lues/nombre`.

## Global Constraints

- Interface en français, chemins en anglais ; pas de changement d'API.
- Le lien d'une notification est un chemin du front (`/leave-requests/42`) : navigation interne.
- Commits en français, sans mention d'IA.

## Review Focus

1. Ticket à usage unique : la reconnexion demande toujours un nouveau ticket.
2. Déconnexion : le flux est fermé, aucune reconnexion ensuite.
3. Plusieurs onglets : le compteur suit les lectures faites ailleurs (événement `non-lues`).
4. Flux coupé pendant longtemps : pas de rafale de tickets (délai croissant).
5. Lien absent : la notification se marque lue sans navigation.

---

### Task 1: API et service `Notifications`
- Models `Notification`, API `notifications(nonLues, page, size)`, `nombreNonLues()`, `marquerLue(id)`, `toutMarquerLu()`, `ticketFlux()`.
- `core/notifications/notifications.ts` : `nonLues: Signal<number>`, `derniere: Signal<Notification | null>`, `fermerToast()`, `ajuster(nombre)`.
- Tests (EventSource simulé, faux minuteurs) : ticket puis flux ; événements → compteur ; erreur → nouveau ticket après 5 s ; déconnexion → fermé.

### Task 2: Rail et toast
- Entrée « Notifications » + compteur ; point sur le bouton menu ; composant toast dans la coque.
- Tests : compteur affiché ; toast affiché puis fermé.

### Task 3: Page `/notifications`
- Tests : liste et filtre non lues ; clic → `lecture` puis navigation vers le lien ; « Tout marquer comme lu ».

### Task 4: Vérification
- Captures (API simulée), revue finale, PR.
