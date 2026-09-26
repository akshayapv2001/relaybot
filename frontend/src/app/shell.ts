import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from './auth';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="layout">
      <nav class="rail" aria-label="Main Navigation">
        <a class="wordmark" routerLink="/activity">
          <div class="logo-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="m18 16 4-4-4-4"/>
              <path d="m6 8-4 4 4 4"/>
              <path d="m14.5 4-5 16"/>
            </svg>
          </div>
          <span class="brand-name">Relay<span class="highlight">Bot</span></span>
        </a>
        <ul class="nav-links">
          <li>
            <a routerLink="/activity" routerLinkActive="active">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
              <span>Live log</span>
            </a>
          </li>
          <li>
            <a routerLink="/reports" routerLinkActive="active">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/></svg>
              <span>Reports</span>
            </a>
          </li>
          <li>
            <a routerLink="/jobs" routerLinkActive="active">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>
              <span>Queue</span>
            </a>
          </li>
          <li>
            <a routerLink="/servers" routerLinkActive="active">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>
              <span>Servers</span>
            </a>
          </li>
        </ul>
        <div class="user-card">
          <div class="user-info">
            <span class="user-email">{{ auth.username() }}</span>
            <span class="user-role">Administrator</span>
          </div>
          <button class="link logout-btn" type="button" (click)="signOut()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
            Sign out
          </button>
        </div>
      </nav>
      <main id="main">
        <router-outlet />
      </main>
    </div>
  `,
  styles: `
    .layout {
      display: grid;
      grid-template-columns: 15.5rem 1fr;
      min-height: 100vh;
      background: var(--bg-root);
    }
    .rail {
      background: rgba(13, 19, 31, 0.95);
      border-right: 1px solid var(--border-subtle);
      color: #94a3b8;
      padding: 1.75rem 1.1rem;
      display: flex;
      flex-direction: column;
      gap: 2rem;
      position: sticky;
      top: 0;
      height: 100vh;
      backdrop-filter: blur(20px);
      z-index: 50;
    }
    .wordmark {
      color: #ffffff;
      font-size: 1.35rem;
      font-weight: 800;
      text-decoration: none;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0 0.4rem;
      letter-spacing: -0.02em;
    }
    .logo-icon {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 14px rgba(99, 102, 241, 0.4);
    }
    .brand-name .highlight {
      color: #818cf8;
      font-weight: 500;
    }
    ul.nav-links {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 0.35rem;
    }
    ul.nav-links a {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.65rem 0.9rem;
      border-radius: 10px;
      color: #94a3b8;
      font-weight: 500;
      font-size: 0.925rem;
      text-decoration: none;
      transition: all 0.2s ease;
    }
    ul.nav-links a:hover {
      background: rgba(255, 255, 255, 0.05);
      color: #f8fafc;
    }
    ul.nav-links a.active {
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(79, 70, 229, 0.1) 100%);
      border: 1px solid rgba(99, 102, 241, 0.3);
      color: #ffffff;
      font-weight: 600;
      box-shadow: 0 4px 15px rgba(99, 102, 241, 0.15);
    }
    ul.nav-links a.active svg {
      color: #818cf8;
    }
    .user-card {
      margin-top: auto;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--border-subtle);
      border-radius: 12px;
      padding: 0.85rem 1rem;
      display: grid;
      gap: 0.6rem;
    }
    .user-info {
      display: grid;
      gap: 0.15rem;
    }
    .user-email {
      color: #f8fafc;
      font-size: 0.85rem;
      font-weight: 600;
      word-break: break-all;
    }
    .user-role {
      color: #64748b;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .logout-btn {
      color: #ef4444 !important;
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.8rem;
      padding-top: 0.2rem;
      cursor: pointer;
    }
    .logout-btn:hover {
      color: #fca5a5 !important;
    }
    main {
      padding: 2.5rem clamp(1rem, 4vw, 3.5rem) 4rem;
      min-width: 0;
    }
    @media (max-width: 760px) {
      .layout { grid-template-columns: 1fr; }
      .rail {
        position: static;
        height: auto;
        flex-direction: row;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        padding: 1rem;
      }
      ul.nav-links { display: flex; flex-wrap: wrap; }
      .user-card { margin: 0; }
      main { padding-top: 1.5rem; }
    }
  `,
})
export class Shell {
  protected auth = inject(Auth);
  private router = inject(Router);

  signOut() {
    this.auth.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
