import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Api, JOB_LABELS, Job, errorMessage } from '../api';

type Tab = { label: string; status: string };

@Component({
  selector: 'app-jobs',
  imports: [DatePipe],
  template: `
    <section class="page">
      <header class="page-head">
        <h1>Queue</h1>
        <p>
          Replies, channel posts, notifications and AI triage run in the background and retry on their own
          when Discord, Slack or the AI service is down. Anything that still fails ends up here.
        </p>
      </header>

      <div class="row">
        <div class="tabs" role="tablist">
          @for (t of tabs; track t.status) {
            <button type="button" role="tab" [attr.aria-selected]="t.status === status()"
                    [class.secondary]="t.status !== status()" (click)="select(t.status)">{{ t.label }}</button>
          }
        </div>
        <span class="spacer"></span>
        <button type="button" class="secondary" (click)="load()">Refresh</button>
      </div>

      @if (message(); as m) { <p class="notice" [class.error]="m.bad" [class.ok]="!m.bad" role="status">{{ m.text }}</p> }

      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>Step</th><th>Report</th><th>Attempts</th><th>Last error</th><th>Updated</th><th><span class="sr">Actions</span></th></tr>
          </thead>
          <tbody>
            @for (j of jobs(); track j.id) {
              <tr>
                <td>{{ labels[j.type] ?? j.type }}<div class="small muted">{{ statusText(j) }}</div></td>
                <td class="num">{{ j.reportId ? '#' + j.reportId : '-' }}</td>
                <td class="num">{{ j.attempts }} of {{ j.maxAttempts }}</td>
                <td class="wrap small">{{ j.lastError ?? '-' }}</td>
                <td class="num small">{{ j.updatedAt | date: 'MMM d, HH:mm:ss' }}</td>
                <td>
                  @if (j.status === 'FAILED') {
                    <button type="button" class="secondary" [disabled]="busy() === j.id" (click)="retry(j)">Retry</button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="empty">{{ status() === 'FAILED' ? 'No failed deliveries. Everything went through.' : 'Nothing here right now.' }}</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .tabs { display: flex; gap: 0.4rem; }
    .sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `,
})
export class JobsPage implements OnInit {
  private api = inject(Api);
  readonly labels = JOB_LABELS;
  readonly tabs: Tab[] = [
    { label: 'Failed', status: 'FAILED' },
    { label: 'Waiting', status: 'PENDING' },
    { label: 'All', status: '' },
  ];
  status = signal('FAILED');
  jobs = signal<Job[]>([]);
  busy = signal<number | null>(null);
  message = signal<{ text: string; bad: boolean } | null>(null);

  ngOnInit() { this.load(); }

  select(status: string) {
    this.status.set(status);
    this.message.set(null);
    this.load();
  }

  load() {
    this.api.jobs(this.status()).subscribe({
      next: (j) => this.jobs.set(j),
      error: () => this.message.set({ text: 'Could not load the queue.', bad: true }),
    });
  }

  retry(job: Job) {
    this.busy.set(job.id);
    this.api.retryJob(job.id).subscribe({
      next: () => {
        this.message.set({ text: `Retrying ${this.labels[job.type] ?? job.type} now. Watch the live log for the result.`, bad: false });
        this.busy.set(null);
        this.load();
      },
      error: (e) => { this.message.set({ text: errorMessage(e), bad: true }); this.busy.set(null); },
    });
  }

  statusText(j: Job) {
    switch (j.status) {
      case 'PENDING': return j.attempts > 0 ? 'Waiting to retry' : 'Queued';
      case 'RUNNING': return 'Running';
      case 'DONE': return 'Done';
      default: return 'Failed';
    }
  }
}
