import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { LIBELLES_ROLE } from '../core/auth/auth.models';

/** Application shell for signed-in users: sidebar navigation + routed work area (DESIGN.md, Layout). */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly role = computed(() => {
    const p = this.auth.profil();
    return p ? LIBELLES_ROLE[p.role] : '';
  });
}
