# Product

## Platform

web

## Stack

Angular (standalone components, signals), Angular Router, typed Reactive Forms, Angular Material (required by the internship spec), Chart.js via ng2-charts. The front-end calls the existing Spring Boot API (`backend/`) directly over CORS; it will be hosted separately (Netlify or Vercel) from the API.

## Users

Four roles, all employees of one mid-sized company, using the same app with different rights:

- **EMPLOYE**: checks their own profile and leave balances, requests leave, follows the decision. Occasional use, sometimes from a phone.
- **MANAGER**: everything an employee does, plus approves or refuses their team's leave requests and sees their department's dashboard. Uses it a few times a week, at a desk.
- **RH** (HR): manages employees, departments, public holidays, leave balances and adjustments; sees every request and decides only as a fallback (escalation). Daily, desk-bound, data-heavy use.
- **ADMIN**: everything HR does, plus roles and the audit log.

Demo accounts: `admin` (Amine Ben Salah), `rh` (Sara Trabelsi), `manager` (Karim Mansour), `employe` (Lina Gharbi), password `Test1234!`.

## Product Purpose

An internal HR application: employees, departments and roles; leave requests with working-day counts, balances and a manager-first approval workflow; real-time notifications; a role-scoped dashboard; an audit log; and an HR assistant that answers natural-language questions with read-only tools. Success means each role finishes its task (request leave, decide on a request, find an employee, read the dashboard) quickly and without ambiguity about what it is allowed to do.

## Positioning

The server enforces every rule (role scope, salary visibility, who may decide a request, escalation after 3 days), and the interface makes those rules legible: it shows who will decide, why an action is unavailable, and what changed. The HR assistant only sees what the connected user may see.

## Operating Context

- Internship project (Stage 2026), evaluated in a live defense following `docs/demo.md`; there is no real company or brand behind it.
- Company time zone Africa/Tunis; weekends are Saturday and Sunday (configurable); public holidays are entered yearly by HR, including religious holidays that move each year.
- Leave workflow: EN_ATTENTE → APPROUVEE / REFUSEE / ANNULEE; transmitted to HR after 3 days without manager decision; HR can interrupt an approved leave (early return).
- Notifications arrive live (SSE) and by email; the assistant may take 10 to 25 seconds to answer on a laptop without GPU.

## Capabilities and Constraints

- All features listed in `backend/README.md` and `docs/architecture.md` already exist server-side; the front-end consumes them as-is.
- Interface language: **French only**. Domain terms come from the backend: congé, solde, jours ouvrables, demande, validation, refus, motif, recours, jour férié, journal d'audit.
- Dates are local (no time-zone suffix from the API) and shown in French format; amounts in the demo are euros.
- Desktop first; every screen must also work at phone width (employees requesting leave or checking a balance).
- Salary is visible only to ADMIN, RH and the employee themself.
- Undecided: hosting provider for the API; whether the assistant is enabled in the hosted version.

## Brand Commitments

None. The product name "RH App" is a working title; no logo, no brand colors, no company claims.

## Evidence on Hand

- Seed data: 4 employees, 3 departments (Informatique, Ressources Humaines, Finance), leave types (annuel, maladie, sans solde), 2026 balances.
- Internship spec: `docs/Sujet Stage enrichi_omar _ajroud.pdf`.
- No testimonials, customers or metrics exist; do not invent any.

## Product Principles

1. The role decides the screen: show each person only what they can act on, and explain refusals instead of hiding them silently.
2. Numbers are exact: working days, balances and counts match the server, never estimated in the UI.
3. Every decision leaves a trace: who decided, when, and why are always visible on a request.
4. Calm for daily use: HR staff spend hours in it, so density and legibility outrank decoration.

## Accessibility & Inclusion

WCAG 2.1 AA: text contrast, full keyboard use, visible focus, screen-reader labels on icon buttons and status badges, no information conveyed by color alone (leave statuses carry text).
