import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subscription, lastValueFrom } from 'rxjs';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';
import {
  AdminAcceptability,
  AdminConceptDescription,
  AdminConceptEditorDetail,
  AdminConceptLangRefset
} from 'src/app/models/admin-concept-editor';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-admin-concept-editor',
  templateUrl: './admin-concept-editor.component.html',
  styleUrl: './admin-concept-editor.component.scss'
})
export class AdminConceptEditorComponent implements OnInit, OnDestroy {

  @Input() editions: any[] = [];

  selectedEdition = '';
  private editionSubscription?: Subscription;
  conceptIdInput = '';
  loading = false;
  saving = false;
  dirty = false;

  detail: AdminConceptEditorDetail | null = null;

  newSynonymRefsetId = '';
  newSynonymTerm = '';

  constructor(
    private simplexService: SimplexService,
    private snackBar: MatSnackBar,
    private uiConfigurationService: UiConfigurationService
  ) { }

  ngOnInit(): void {
    this.editionSubscription = this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
      if (edition?.shortName) {
        this.selectedEdition = edition.shortName;
      }
    });
  }

  ngOnDestroy(): void {
    this.editionSubscription?.unsubscribe();
  }

  loadConcept(): void {
    const conceptId = this.conceptIdInput?.trim();
    if (!this.selectedEdition || !conceptId) {
      this.snackBar.open('Select an edition and enter a concept ID.', 'Dismiss', { duration: 5000 });
      return;
    }
    this.loading = true;
    lastValueFrom(this.simplexService.getAdminConceptForEditor(this.selectedEdition, conceptId)).then(
      (detail: AdminConceptEditorDetail) => {
        this.detail = detail;
        this.conceptIdInput = detail.conceptId;
        this.dirty = false;
        this.loading = false;
        if (!this.newSynonymRefsetId && detail.langRefsets.length) {
          this.newSynonymRefsetId = detail.langRefsets[0].refsetId;
        }
      },
      (error) => {
        this.loading = false;
        this.snackBar.open(this.errorMessage(error), 'Dismiss', { duration: 8000 });
      }
    );
  }

  save(): void {
    if (!this.detail || !this.selectedEdition) {
      return;
    }
    this.saving = true;
    const body = { descriptions: this.detail.descriptions };
    lastValueFrom(this.simplexService.updateAdminConceptDescriptions(this.selectedEdition, this.detail.conceptId, body)).then(
      (detail: AdminConceptEditorDetail) => {
        this.detail = detail;
        this.dirty = false;
        this.saving = false;
        this.snackBar.open('Concept descriptions saved.', 'Dismiss', { duration: 4000 });
      },
      (error) => {
        this.saving = false;
        this.snackBar.open(this.errorMessage(error), 'Dismiss', { duration: 8000 });
      }
    );
  }

  onDescriptionChange(): void {
    this.dirty = true;
  }

  applicableLangRefsets(description: AdminConceptDescription): AdminConceptLangRefset[] {
    if (!this.detail) {
      return [];
    }
    return this.detail.langRefsets.filter(refset => refset.languageCode === description.lang);
  }

  acceptabilityLabel(description: AdminConceptDescription, refsetId: string): string {
    const value = description.acceptabilityMap?.[refsetId];
    if (value === 'PREFERRED') {
      return 'Pref';
    }
    if (value === 'ACCEPTABLE') {
      return 'Acc';
    }
    return '—';
  }

  isPreferredSynonymConflict(description: AdminConceptDescription, refsetId: string): boolean {
    if (!this.detail || !description.active || description.type !== 'SYNONYM') {
      return false;
    }
    if (description.acceptabilityMap?.[refsetId] !== 'PREFERRED') {
      return false;
    }
    const preferredSynonymCount = this.detail.descriptions.filter(d =>
      d.active &&
      d.type === 'SYNONYM' &&
      d.acceptabilityMap?.[refsetId] === 'PREFERRED'
    ).length;
    return preferredSynonymCount > 1;
  }

  cycleAcceptability(description: AdminConceptDescription, refsetId: string): void {
    if (!description.acceptabilityMap) {
      description.acceptabilityMap = {};
    }
    const current = description.acceptabilityMap[refsetId];
    let next: AdminAcceptability | undefined;
    if (!current) {
      next = 'PREFERRED';
    } else if (current === 'PREFERRED') {
      next = 'ACCEPTABLE';
    } else {
      next = undefined;
    }
    if (next) {
      description.acceptabilityMap[refsetId] = next;
    } else {
      delete description.acceptabilityMap[refsetId];
    }
    this.dirty = true;
  }

  addSynonym(): void {
    if (!this.detail) {
      return;
    }
    const term = this.newSynonymTerm?.trim();
    if (!term || !this.newSynonymRefsetId) {
      this.snackBar.open('Enter a term and select a language refset.', 'Dismiss', { duration: 5000 });
      return;
    }
    const refset = this.detail.langRefsets.find(r => r.refsetId === this.newSynonymRefsetId);
    const lang = refset?.languageCode || 'en';
    const description: AdminConceptDescription = {
      descriptionId: null,
      term,
      type: 'SYNONYM',
      lang,
      active: true,
      released: false,
      acceptabilityMap: { [this.newSynonymRefsetId]: 'ACCEPTABLE' }
    };
    this.detail.descriptions = [...this.detail.descriptions, description];
    this.newSynonymTerm = '';
    this.dirty = true;
  }

  typeLabel(type: string | undefined): string {
    switch (type) {
      case 'FSN':
        return 'FSN';
      case 'TEXT_DEFINITION':
        return 'Def';
      default:
        return 'Syn';
    }
  }

  canSave(): boolean {
    return !!this.detail && this.dirty && !this.loading && !this.saving;
  }

  private errorMessage(error: any): string {
    return error?.error?.message || error?.message || 'Request failed.';
  }
}
