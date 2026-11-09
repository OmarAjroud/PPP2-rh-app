# Assistant RH (étape 17) : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Poser des questions RH en langage naturel (soldes, absences, demandes en attente, effectifs) depuis le front-end.

**Architecture:** Une page `/assistant` (tous les rôles) et un service racine `Conversation` qui garde l'échange en mémoire pendant la session. L'API ne garde aucun historique : chaque question envoie toute la conversation (`POST /api/assistant/chat`, ≤ 20 messages de ≤ 4000 caractères). `GET /api/assistant/statut` dit si l'assistant est disponible. Aucun changement côté back-end.

**Tech Stack:** Angular 21, PrimeNG 21 (Textarea, Button), Vitest.

## Décisions (déléguées par l'utilisateur à Claude)

- Rail : « Assistant » (tous les rôles), dans le premier groupe.
- Réponses affichées en texte brut (retours à la ligne conservés), jamais en HTML ; outils utilisés en libellés français sous la réponse.
- Conversation gardée en mémoire pendant la session, effacée à la déconnexion et par « Nouvelle conversation ».
- Limite : quand l'échange atteint 20 messages, l'envoi est désactivé et un message invite à recommencer.
- Envoi : Entrée envoie, Maj+Entrée va à la ligne ; compteur 4000 caractères ; « L'assistant réfléchit… » annoncé (`aria-live`).
- Erreur (dont la limite de 10 questions/minute) : la question revient dans le champ, le message de l'API s'affiche.
- Conversation vide : exemples de questions selon le rôle, cliquables.

## Global Constraints

- Interface en français, chemins en anglais ; pas de changement d'API.
- Rôles de message envoyés : `utilisateur` / `assistant`.
- Commits en français, sans mention d'IA.

## Review Focus

1. Le dernier message envoyé est toujours la nouvelle question.
2. Jamais plus de 20 messages envoyés.
3. Une réponse ne peut pas injecter de HTML.
4. Double envoi impossible pendant l'attente.
5. Déconnexion : la conversation d'une personne n'est pas visible par la suivante.

---

### Task 1: API et service `Conversation`
### Task 2: Page `/assistant` (statut, envoi, réponses, outils, erreurs, limite, exemples)
### Task 3: Route et navigation
### Task 4: Vérification (captures API simulée), revue finale, PR
