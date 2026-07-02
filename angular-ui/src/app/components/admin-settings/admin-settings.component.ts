import { Component, OnInit } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { LLM_USAGE_PERIODS, LlmUsagePeriod, LlmUsageSummary } from 'src/app/models/llm-usage-summary';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-admin-settings',
  templateUrl: './admin-settings.component.html',
  styleUrl: './admin-settings.component.scss'
})
export class AdminSettingsComponent implements OnInit {

  roles: string[] = [];
  loading = false;
  loadingEditions = false;
  accessDenied = false;

  periods = LLM_USAGE_PERIODS;
  selectedPeriod: LlmUsagePeriod = 'week';
  selectedCodesystem = '';
  selectedModel = '';

  editions: any[] = [];
  knownModels: string[] = [];
  summary: LlmUsageSummary | null = null;

  byModelColumns = ['model', 'provider', 'inputTokens', 'outputTokens', 'totalTokens', 'requestCount', 'conceptsTranslated', 'costUsd'];
  dailyColumns = ['date', 'codesystem', 'model', 'provider', 'inputTokens', 'outputTokens', 'totalTokens', 'requestCount', 'conceptsTranslated'];

  constructor(private simplexService: SimplexService) { }

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    lastValueFrom(this.simplexService.getRoles()).then(
      (roles) => {
        this.roles = roles ?? [];
        if (this.isAdmin()) {
          this.loadEditions();
          this.loadUsage();
        } else {
          this.accessDenied = true;
        }
      },
      () => {
        this.accessDenied = true;
      }
    );
  }

  isAdmin(): boolean {
    return this.roles.includes('ADMIN');
  }

  loadEditions(): void {
    this.loadingEditions = true;
    lastValueFrom(this.simplexService.getEditions()).then(
      (response) => {
        this.editions = (response?.items ?? []).filter((item: any) => item.shortName);
        this.loadingEditions = false;
      },
      () => {
        this.loadingEditions = false;
      }
    );
  }

  loadUsage(): void {
    if (!this.isAdmin()) {
      return;
    }
    this.loading = true;
    const codesystem = this.selectedCodesystem || undefined;
    const model = this.selectedModel || undefined;
    lastValueFrom(this.simplexService.getLlmUsage(this.selectedPeriod, codesystem, model)).then(
      (summary: LlmUsageSummary) => {
        this.summary = summary;
        this.mergeKnownModels(summary);
        this.loading = false;
      },
      () => {
        this.loading = false;
      }
    );
  }

  onPeriodChange(period: LlmUsagePeriod): void {
    this.selectedPeriod = period;
    this.loadUsage();
  }

  onCodesystemChange(): void {
    this.loadUsage();
  }

  onModelChange(): void {
    this.loadUsage();
  }

  showCodesystemColumn(): boolean {
    return !this.selectedCodesystem;
  }

  formatNumber(value: number | undefined): string {
    return (value ?? 0).toLocaleString();
  }

  formatCost(value: number | undefined): string {
    if (value == null) {
      return '—';
    }
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 4
    }).format(value);
  }

  get dailyDisplayColumns(): string[] {
    return this.showCodesystemColumn()
      ? this.dailyColumns
      : this.dailyColumns.filter(column => column !== 'codesystem');
  }

  private mergeKnownModels(summary: LlmUsageSummary): void {
    const models = new Set(this.knownModels);
    for (const item of summary.byModel ?? []) {
      if (item.model) {
        models.add(item.model);
      }
    }
    this.knownModels = Array.from(models).sort();
  }
}
