import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, Channel, Guild, GuildSettings, PRIORITIES, Priority, Rule, errorMessage } from '../api';

type Status = { text: string; bad: boolean } | null;

@Component({
  selector: 'app-server-detail',
  imports: [FormsModule, RouterLink],
  template: `
    <section class="page">
      <a routerLink="/servers" class="small">Back to servers</a>

      @if (guild(); as g) {
        <header class="page-head">
          <h1>{{ g.name }}</h1>
          <p>Choose what the bot does when someone uses its commands in this server.</p>
        </header>

        @if (connect() === 'ok') {
          <p class="notice ok" role="status">
            RelayBot is now in {{ g.name }}.{{ g.postChannelId ? '' : ' Pick a channel for reports below to finish setting up.' }}
          </p>
        }

        <!-- Channel + command behaviour: one form, one save -->
        <form class="panel" (ngSubmit)="saveSettings()">
          <header>
            <h2>Commands</h2>
            <p>Where new reports are posted for the team, and how /report and /status behave.</p>
          </header>

          <label class="field">Post new reports in
            @if (channelsError()) {
              <span class="notice warn small">{{ channelsError() }}</span>
            }
            <select name="channel" [(ngModel)]="settings.postChannelId">
              <option [ngValue]="null">Don't post reports to a channel</option>
              @for (c of channels(); track c.id) { <option [ngValue]="c.id">#{{ c.name }}</option> }
            </select>
            <span class="hint">The bot needs permission to view and send messages there. Reports appear with Acknowledge and Escalate buttons.</span>
          </label>

          <div class="checks">
            <label class="check"><input type="checkbox" name="report" [(ngModel)]="settings.reportEnabled">
              <span>/report is on<small>People can file reports, with text or a form.</small></span></label>
            <label class="check"><input type="checkbox" name="status" [(ngModel)]="settings.statusEnabled">
              <span>/status is on<small>Shows how many reports are open, by priority.</small></span></label>
            <label class="check"><input type="checkbox" name="eph" [(ngModel)]="settings.ephemeralReplies">
              <span>Reply privately<small>Only the person who ran the command sees the bot's answer.</small></span></label>
            <label class="check"><input type="checkbox" name="ai" [(ngModel)]="settings.aiEnabled" [disabled]="!g.aiAvailable">
              <span>AI triage
                <small>{{ g.aiAvailable ? 'Adds a one-line summary and category, and suggests a priority when no keyword rule matches.' : 'Unavailable: GROQ_API_KEY is not set on the server.' }}</small>
              </span></label>
          </div>

          <label class="field narrow">Priority when nothing else decides
            <select name="defPri" [(ngModel)]="settings.defaultPriority">
              @for (p of priorities; track p) { <option [ngValue]="p">{{ label(p) }}</option> }
            </select>
          </label>

          <div class="row">
            <button type="submit" [disabled]="busy()">Save settings</button>
            @if (settingsStatus(); as s) { <span class="small" [class.bad]="s.bad" role="status">{{ s.text }}</span> }
          </div>
        </form>

        <!-- Keyword rules -->
        <form class="panel" (ngSubmit)="saveRules()">
          <header>
            <h2>Priority rules</h2>
            <p>If a report contains one of these words, it gets that priority. Whole words only, not case-sensitive. When several match, the highest wins. Rules always beat the AI's suggestion.</p>
          </header>

          <div class="rules">
            @for (rule of rules; track $index; let i = $index) {
              <div class="rule">
                <label class="field">Word or phrase
                  <input type="text" [name]="'kw' + i" [(ngModel)]="rule.keyword" maxlength="50" required>
                </label>
                <label class="field">Priority
                  <select [name]="'pri' + i" [(ngModel)]="rule.priority">
                    @for (p of priorities; track p) { <option [ngValue]="p">{{ label(p) }}</option> }
                  </select>
                </label>
                <button type="button" class="link" (click)="rules.splice(i, 1)">Remove</button>
              </div>
            } @empty {
              <p class="muted small">No rules yet. Try "outage" as High, or "slow" as Medium.</p>
            }
          </div>

          <div class="row">
            <button type="button" class="secondary" (click)="rules.push({ keyword: '', priority: 'HIGH' })" [disabled]="rules.length >= 50">Add a rule</button>
            <button type="submit" [disabled]="busy()">Save rules</button>
            @if (rulesStatus(); as s) { <span class="small" [class.bad]="s.bad" role="status">{{ s.text }}</span> }
          </div>
        </form>

        <!-- Mirror -->
        <section class="panel">
          <header>
            <h2>Notifications</h2>
            <p>Also send new reports to Slack or to another Discord channel, using an incoming webhook URL. The URL is stored encrypted and never shown again.</p>
          </header>

          <p>
            @if (g.mirrorConfigured) {
              Sending to <strong>{{ g.mirrorKind === 'SLACK' ? 'Slack' : 'a Discord webhook' }}</strong>.
            } @else {
              <span class="muted">Not set up.</span>
            }
          </p>

          <form class="row" (ngSubmit)="saveMirror()">
            <label class="field grow">{{ g.mirrorConfigured ? 'Replace webhook URL' : 'Webhook URL' }}
              <input type="url" name="mirrorUrl" [(ngModel)]="mirrorUrl" autocomplete="off"
                     placeholder="https://hooks.slack.com/services/…">
            </label>
            <button type="submit" [disabled]="busy() || !mirrorUrl.trim()">Save webhook</button>
          </form>

          <label class="field narrow">Send notifications for
            <select name="mirPri" [(ngModel)]="settings.mirrorMinPriority" (ngModelChange)="saveSettings()">
              <option ngValue="LOW">All reports</option>
              <option ngValue="MEDIUM">Medium and high priority</option>
              <option ngValue="HIGH">High priority only</option>
            </select>
            <span class="hint">Escalations are always sent.</span>
          </label>

          @if (g.mirrorConfigured) {
            <div class="row">
              <button type="button" class="secondary" (click)="testMirror()" [disabled]="busy()">Send a test notification</button>
              <button type="button" class="danger" (click)="removeMirror()" [disabled]="busy()">Stop notifications</button>
            </div>
          }
          @if (mirrorStatus(); as s) { <p class="small" [class.bad]="s.bad" role="status">{{ s.text }}</p> }
        </section>

        <section class="panel danger-zone">
          <header>
            <h2>Disconnect this server</h2>
            <p>The bot leaves {{ g.name }} and its settings and reports are deleted here. You can connect it again later.</p>
          </header>
          <div><button type="button" class="danger" (click)="disconnect()" [disabled]="busy()">Disconnect {{ g.name }}</button></div>
        </section>
      } @else if (loadError()) {
        <p class="notice error">{{ loadError() }}</p>
      } @else {
        <p class="muted">Loading…</p>
      }
    </section>
  `,
  styles: `
    .checks { display: grid; gap: 0.75rem; }
    .narrow { max-width: 22rem; }
    .grow { flex: 1 1 20rem; }
    form.row { align-items: flex-end; }
    .rules { display: grid; gap: 0.5rem; }
    .rule { display: grid; grid-template-columns: minmax(10rem, 1fr) 10rem auto; gap: 0.75rem; align-items: end; }
    .bad { color: var(--high); font-weight: 700; }
    .danger-zone { border-color: #efc2bb; }
    @media (max-width: 600px) { .rule { grid-template-columns: 1fr 1fr; } }
  `,
})
export class ServerDetailPage implements OnInit {
  private api = inject(Api);
  private router = inject(Router);

  id = input.required<string>();
  connect = input<string>();

  readonly priorities = PRIORITIES;
  guild = signal<Guild | null>(null);
  channels = signal<Channel[]>([]);
  channelsError = signal<string | null>(null);
  loadError = signal<string | null>(null);
  busy = signal(false);
  settingsStatus = signal<Status>(null);
  rulesStatus = signal<Status>(null);
  mirrorStatus = signal<Status>(null);

  settings!: GuildSettings;
  rules: Rule[] = [];
  mirrorUrl = '';

  ngOnInit() {
    this.api.guild(this.id()).subscribe({
      next: (g) => this.apply(g),
      error: (e) => this.loadError.set(e.status === 404 ? 'This server is not connected.' : 'Could not load this server.'),
    });
    this.api.channels(this.id()).subscribe({
      next: (c) => this.channels.set(c),
      error: (e) => this.channelsError.set(`Couldn't load channels from Discord. ${errorMessage(e, '')}`.trim()),
    });
  }

  label(p: Priority) { return p.charAt(0) + p.slice(1).toLowerCase(); }

  saveSettings() {
    this.run(this.api.saveSettings(this.id(), this.settings), this.settingsStatus, 'Settings saved.');
  }

  saveRules() {
    const clean = this.rules.map((r) => ({ ...r, keyword: r.keyword.trim() })).filter((r) => r.keyword);
    this.run(this.api.saveRules(this.id(), clean), this.rulesStatus, 'Rules saved.');
  }

  saveMirror() {
    this.run(this.api.setMirror(this.id(), this.mirrorUrl.trim()), this.mirrorStatus, 'Webhook saved. Send a test to check it.',
      () => (this.mirrorUrl = ''));
  }

  removeMirror() {
    if (!confirm('Stop sending notifications for this server?')) return;
    this.run(this.api.removeMirror(this.id()), this.mirrorStatus, 'Notifications stopped.');
  }

  testMirror() {
    this.busy.set(true);
    this.api.testMirror(this.id()).subscribe({
      next: () => { this.mirrorStatus.set({ text: 'Test notification sent. Check the channel.', bad: false }); this.busy.set(false); },
      error: (e) => { this.mirrorStatus.set({ text: errorMessage(e), bad: true }); this.busy.set(false); },
    });
  }

  disconnect() {
    const g = this.guild();
    if (!g || !confirm(`Disconnect ${g.name}? Its reports and settings will be deleted.`)) return;
    this.busy.set(true);
    this.api.disconnect(g.id).subscribe({
      next: () => this.router.navigate(['/servers']),
      error: (e) => { this.busy.set(false); alert(errorMessage(e)); },
    });
  }

  private run(call: ReturnType<Api['guild']>, status: ReturnType<typeof signal<Status>>, ok: string, after?: () => void) {
    this.busy.set(true);
    status.set(null);
    call.subscribe({
      next: (g) => { this.apply(g); status.set({ text: ok, bad: false }); this.busy.set(false); after?.(); },
      error: (e) => { status.set({ text: errorMessage(e), bad: true }); this.busy.set(false); },
    });
  }

  private apply(g: Guild) {
    this.guild.set(g);
    this.settings = {
      postChannelId: g.postChannelId,
      reportEnabled: g.reportEnabled,
      statusEnabled: g.statusEnabled,
      aiEnabled: g.aiEnabled,
      ephemeralReplies: g.ephemeralReplies,
      defaultPriority: g.defaultPriority,
      mirrorMinPriority: g.mirrorMinPriority,
    };
    this.rules = g.rules.map((r) => ({ ...r }));
  }
}
