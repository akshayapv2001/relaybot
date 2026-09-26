import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, firstValueFrom, map, of, tap, throwError } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class Auth {
  private http = inject(HttpClient);
  readonly username = signal<string | null>(null);
  private checked = false;

  /** Also primes the XSRF-TOKEN cookie that login needs. */
  async isLoggedIn(): Promise<boolean> {
    if (this.checked) return this.username() !== null;
    const name = await firstValueFrom(
      this.http.get<{ username: string }>('/api/auth/me').pipe(
        map((r) => r.username),
        catchError(() => of(null)),
      ),
    );
    this.checked = true;
    this.username.set(name);
    return name !== null;
  }

  login(username: string, password: string) {
    return this.http
      .post<{ username: string }>('/api/auth/login', { username, password })
      .pipe(tap((r) => { this.username.set(r.username); this.checked = true; }));
  }

  logout() {
    return this.http.post('/api/auth/logout', {}).pipe(
      catchError(() => of(null)),
      tap(() => this.forget()),
    );
  }

  forget() {
    this.username.set(null);
    this.checked = true;
  }
}

export const authGuard: CanActivateFn = async () => {
  const auth = inject(Auth);
  const router = inject(Router);
  return (await auth.isLoggedIn()) ? true : router.createUrlTree(['/login']);
};

/** Session expired while using the app: go back to the login page. */
export const unauthorizedInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const router = inject(Router);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !req.url.startsWith('/api/auth/')) {
        auth.forget();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    }),
  );
};
