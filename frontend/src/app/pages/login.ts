import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { errorMessage } from '../api';
import { Auth } from '../auth';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <main class="wrap">
      <div class="bg-glow"></div>
      <form class="card" (ngSubmit)="submit()" #f="ngForm">
        <div class="header">
          <div class="brand-badge">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="m18 16 4-4-4-4"/>
              <path d="m6 8-4 4 4 4"/>
              <path d="m14.5 4-5 16"/>
            </svg>
          </div>
          <h1>Sign in to <span class="accent-text">RelayBot</span></h1>
          <p class="muted">Admin Control Panel & Live Activity Center</p>
        </div>

        @if (error()) {
          <p class="notice error" role="alert">{{ error() }}</p>
        }

        <label class="field">
          <span>Username or Email</span>
          <input type="text" name="username" [(ngModel)]="username" required autocomplete="username" autofocus placeholder="admin@yopmail.com">
        </label>
        <label class="field">
          <span>Password</span>
          <input type="password" name="password" [(ngModel)]="password" required autocomplete="current-password" placeholder="••••••••">
        </label>
        <button type="submit" [disabled]="busy() || !f.valid">
          {{ busy() ? 'Signing in…' : 'Sign in to Dashboard' }}
        </button>
      </form>
    </main>
  `,
  styles: `
    .wrap {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 1.5rem;
      background: radial-gradient(circle at 50% 20%, #1e1b4b 0%, #090d16 60%);
      position: relative;
      overflow: hidden;
    }
    .bg-glow {
      position: absolute;
      width: 450px;
      height: 450px;
      background: radial-gradient(circle, rgba(99, 102, 241, 0.25) 0%, rgba(0, 0, 0, 0) 70%);
      top: 30%;
      left: 50%;
      transform: translate(-50%, -50%);
      pointer-events: none;
    }
    .card {
      width: min(26rem, 100%);
      background: rgba(15, 23, 42, 0.75);
      backdrop-filter: blur(24px);
      -webkit-backdrop-filter: blur(24px);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 20px;
      padding: 2.5rem 2rem;
      display: grid;
      gap: 1.25rem;
      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.6);
      position: relative;
      z-index: 10;
    }
    .header {
      display: grid;
      gap: 0.4rem;
      text-align: center;
      justify-items: center;
    }
    .brand-badge {
      width: 48px;
      height: 48px;
      border-radius: 14px;
      background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
      margin-bottom: 0.5rem;
    }
    h1 {
      font-size: 1.6rem;
      font-weight: 800;
    }
    .accent-text {
      color: #818cf8;
      background: none;
      -webkit-text-fill-color: initial;
    }
    label.field span {
      color: #cbd5e1;
      font-weight: 600;
      font-size: 0.85rem;
    }
    button {
      justify-content: center;
      margin-top: 0.75rem;
      padding: 0.75rem 1.2rem;
      font-size: 0.95rem;
      font-weight: 700;
      border-radius: 10px;
    }
  `,
})
export class LoginPage {
  private auth = inject(Auth);
  private router = inject(Router);
  username = '';
  password = '';
  busy = signal(false);
  error = signal<string | null>(null);

  constructor() {
    this.auth.isLoggedIn().then((ok) => ok && this.router.navigate(['/activity']));
  }

  submit() {
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/activity']),
      error: (e) => {
        this.error.set(errorMessage(e, 'Sign-in failed.'));
        this.busy.set(false);
      },
    });
  }
}
