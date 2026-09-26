import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Activity, Api, Guild, Overview } from '../api';

type Connection = 'connecting' | 'live' | 'reconnecting';

@Component({
  selector: 'app-activity',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <section class="page">
      <header class="page-head">
        <h1>Live log</h1>
        <p>Every command, reply, retry and failure, as it happens.</p>
      </header>

      @if (overview(); as o) {
        <dl class="health">
          <div class="stat-card"><dt>Waiting to send</dt><dd class="num">{{ o.jobsPending }}</dd></div>
          <div class="stat-card" [class.bad]="o.jobsFailed > 0">
            <dt>Failed deliveries</dt>
            <dd class="num">@if (o.jobsFailed > 0) {<a routerLink="/jobs">{{ o.jobsFailed }}</a>} @else {0}</dd>
          </div>
          <div class="stat-card" [class.bad]="o.errorsLast24h > 0"><dt>Errors (24h)</dt><dd class="num">{{ o.errorsLast24h }}</dd></div>
          <div class="stat-card"><dt>Blocked requests</dt><dd class="num">{{ o.rejectedRequests }}</dd></div>
        </dl>
      }

      <div class="row filters">
        <label class="field">Server
          <select [(ngModel)]="guildFilter" (ngModelChange)="reload()">
            <option value="">All servers</option>
            @for (g of guilds(); track g.id) { <option [value]="g.id">{{ g.name }}</option> }
          </select>
        </label>
        <label class="field">Show
          <select [(ngModel)]="levelFilter" (ngModelChange)="reload()">
            <option value="">Everything</option>
            <option value="WARN">Warnings</option>
            <option value="ERROR">Errors</option>
          </select>
        </label>
        <span class="spacer"></span>
        <span class="conn" [class]="connection()" role="status">
          {{ connection() === 'live' ? 'Live' : connection() === 'connecting' ? 'Connecting…' : 'Reconnecting…' }}
        </span>
      </div>

      @if (error()) { <p class="notice error">{{ error() }}</p> }

      <ol class="log" aria-live="polite">
        @for (a of entries(); track a.id) {
          <li [class]="a.level" [class.fresh]="freshIds.has(a.id)">
            <time [attr.datetime]="a.createdAt" class="num small">{{ a.createdAt | date: 'MMM d, HH:mm:ss' }}</time>
            <div class="body">
              <p>{{ a.message }}</p>
              <p class="meta small">
                @if (a.guildId) { <span>{{ guildName(a.guildId) }}</span> }
                @if (a.reportId) { <span>Report #{{ a.reportId }}</span> }
                @if (a.level !== 'INFO') { <span class="lvl">{{ a.level === 'WARN' ? 'Warning' : 'Error' }}</span> }
              </p>
            </div>
          </li>
        } @empty {
          <li class="empty">Nothing yet. Run <strong>/report</strong> in a connected server and it will show up here.</li>
        }
      </ol>

      @if (overview()?.recentRejections?.length) {
        <details class="panel blocked">
          <summary><strong>Recently blocked requests</strong>&nbsp;<span class="muted small">(failed signature or timestamp checks)</span></summary>
          <ul class="rejections small">
            @for (r of overview()!.recentRejections; track $index) {
              <li><time class="num">{{ r.at | date: 'MMM d, HH:mm:ss' }}</time> {{ r.reason }} <span class="muted">from {{ r.remoteAddress }}</span></li>
            }
          </ul>
        </details>
      }
    </section>
  `,
  styles: `
    .health {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
      gap: 1rem;
      margin: 0;
    }
    .stat-card {
      background: rgba(15, 23, 42, 0.75);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid var(--border-subtle);
      border-radius: 14px;
      padding: 1.1rem 1.35rem;
      display: grid;
      gap: 0.35rem;
      transition: all 0.2s ease;
    }
    .stat-card:hover {
      border-color: rgba(255, 255, 255, 0.15);
      transform: translateY(-2px);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
    }
    .stat-card dt {
      font-size: 0.8rem;
      font-weight: 600;
      color: #94a3b8;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .stat-card dd {
      margin: 0;
      font-size: 1.8rem;
      font-weight: 800;
      color: #ffffff;
      font-family: var(--font-mono);
    }
    .stat-card.bad dd, .stat-card.bad dd a {
      color: #ef4444;
    }
    .filters {
      align-items: flex-end;
      background: rgba(15, 23, 42, 0.5);
      padding: 1rem 1.25rem;
      border: 1px solid var(--border-subtle);
      border-radius: 12px;
    }
    .conn {
      font-size: 0.85rem;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      gap: 0.6rem;
      color: var(--muted);
      padding: 0.4rem 0.85rem;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid var(--border-subtle);
      border-radius: 20px;
    }
    .conn::before {
      content: "";
      width: 0.5rem;
      height: 0.5rem;
      border-radius: 50%;
      background: #64748b;
    }
    .conn.live {
      color: #34d399;
      background: rgba(16, 185, 129, 0.1);
      border-color: rgba(16, 185, 129, 0.3);
    }
    .conn.live::before {
      background: #10b981;
      box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.25);
      animation: pulse 2s infinite;
    }
    @keyframes pulse {
      0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.4); }
      70% { box-shadow: 0 0 0 8px rgba(16, 185, 129, 0); }
      100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
    }
    .log {
      list-style: none;
      margin: 0;
      padding: 0;
      background: rgba(15, 23, 42, 0.75);
      backdrop-filter: blur(16px);
      border: 1px solid var(--border-subtle);
      border-radius: 14px;
      overflow: hidden;
      box-shadow: var(--shadow-md);
    }
    .log li {
      display: grid;
      grid-template-columns: 9.5rem 1fr;
      gap: 1rem;
      padding: 0.85rem 1.25rem;
      border-bottom: 1px solid var(--border-subtle);
      transition: background-color 0.15s ease;
    }
    .log li:hover {
      background-color: rgba(255, 255, 255, 0.02);
    }
    .log li:last-child {
      border-bottom: none;
    }
    .log li.WARN {
      border-left: 4px solid var(--medium);
    }
    .log li.ERROR {
      border-left: 4px solid var(--high);
      background: rgba(239, 68, 68, 0.05);
    }
    .log li.fresh {
      animation: arrive 2.4s ease-out;
    }
    @keyframes arrive {
      from { background: rgba(99, 102, 241, 0.25); }
      to { background: transparent; }
    }
    .log li.empty {
      display: block;
      padding: 3rem 1.5rem;
      text-align: center;
      color: var(--muted);
    }
    time {
      color: #64748b;
      font-size: 0.825rem;
      padding-top: 0.1rem;
    }
    .body p {
      color: #f1f5f9;
      font-size: 0.925rem;
    }
    .meta {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      color: var(--muted);
      margin-top: 0.25rem;
      font-size: 0.8rem;
    }
    .lvl {
      font-weight: 700;
    }
    .ERROR .lvl { color: #ef4444; }
    .WARN .lvl { color: #f59e0b; }
    .blocked {
      display: block;
      background: rgba(15, 23, 42, 0.75);
      border: 1px solid var(--border-subtle);
      border-radius: 12px;
      padding: 1rem 1.25rem;
    }
    .blocked[open] summary {
      margin-bottom: 0.85rem;
    }
    summary {
      cursor: pointer;
      color: #cbd5e1;
    }
    .rejections {
      margin: 0;
      padding-left: 1.1rem;
      display: grid;
      gap: 0.35rem;
      color: #94a3b8;
    }
    @media (max-width: 600px) {
      .log li { grid-template-columns: 1fr; gap: 0.25rem; }
    }
  `,
})
export class ActivityPage implements OnInit, OnDestroy {
  private api = inject(Api);

  guilds = signal<Guild[]>([]);
  entries = signal<Activity[]>([]);
  overview = signal<Overview | null>(null);
  connection = signal<Connection>('connecting');
  error = signal<string | null>(null);
  freshIds = new Set<number>();
  guildFilter = '';
  levelFilter = '';

  private source?: EventSource;
  private overviewTimer?: ReturnType<typeof setInterval>;
  private guildNames = computed(() => new Map(this.guilds().map((g) => [g.id, g.name])));

  ngOnInit() {
    this.api.guilds().subscribe((g) => this.guilds.set(g));
    this.reload();
    this.loadOverview();
    this.overviewTimer = setInterval(() => this.loadOverview(), 30_000);
    this.connect();
  }

  ngOnDestroy() {
    this.source?.close();
    clearInterval(this.overviewTimer);
  }

  reload() {
    this.api.activity(this.guildFilter, this.levelFilter).subscribe({
      next: (rows) => { this.entries.set(rows); this.error.set(null); },
      error: () => this.error.set('Could not load the log.'),
    });
  }

  guildName(id: string) {
    return this.guildNames().get(id) ?? 'Unknown server';
  }

  private loadOverview() {
    this.api.overview().subscribe({ next: (o) => this.overview.set(o), error: () => {} });
  }

  private connect() {
    // Same-origin, so the session cookie is sent automatically. EventSource reconnects by itself.
    this.source = new EventSource('/api/activity/stream');
    this.source.onopen = () => this.connection.set('live');
    this.source.onerror = () => this.connection.set('reconnecting');
    this.source.addEventListener('activity', (e) => {
      const a = JSON.parse((e as MessageEvent).data) as Activity;
      if (!this.matches(a)) return;
      this.freshIds.add(a.id);
      this.entries.update((list) => [a, ...list.filter((x) => x.id !== a.id)].slice(0, 300));
      if (a.level !== 'INFO' || a.event.startsWith('job.')) this.loadOverview();
    });
  }

  private matches(a: Activity) {
    if (this.guildFilter && a.guildId !== this.guildFilter) return false;
    if (this.levelFilter === 'WARN' && a.level !== 'WARN') return false;
    if (this.levelFilter === 'ERROR' && a.level !== 'ERROR') return false;
    return true;
  }
}
