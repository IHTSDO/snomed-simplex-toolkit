import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subscription, lastValueFrom } from 'rxjs';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';
import {
  AdminConceptEditorDetail,
  AdminConceptEditorPanelState
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

  panels: AdminConceptEditorPanelState[] = [];

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

  trackPanel(_index: number, panel: AdminConceptEditorPanelState): string {
    return panel.panelId;
  }

  get selectedEditionDisplayName(): string {
    const edition = this.editions.find(e => e.shortName === this.selectedEdition);
    return edition?.name ?? this.selectedEdition;
  }

  loadConcept(): void {
    const conceptId = this.conceptIdInput?.trim();
    if (!this.selectedEdition || !conceptId) {
      this.snackBar.open('Select an edition and enter a concept ID.', 'Dismiss', { duration: 5000 });
      return;
    }
    if (this.panels.some(p => p.conceptId === conceptId)) {
      this.snackBar.open('That concept is already open in an editor panel.', 'Dismiss', { duration: 5000 });
      return;
    }

    const panelId = crypto.randomUUID();
    const panel: AdminConceptEditorPanelState = {
      panelId,
      conceptId,
      loading: true,
      saving: false,
      dirty: false,
      detail: null,
      newSynonymRefsetId: '',
      newSynonymTerm: ''
    };
    this.panels = [panel, ...this.panels];
    this.conceptIdInput = '';

    lastValueFrom(this.simplexService.getAdminConceptForEditor(this.selectedEdition, conceptId)).then(
      (detail: AdminConceptEditorDetail) => {
        const loaded = this.panels.find(p => p.panelId === panelId);
        if (!loaded) {
          return;
        }
        loaded.loading = false;
        loaded.detail = detail;
        loaded.conceptId = detail.conceptId;
        if (detail.langRefsets.length) {
          loaded.newSynonymRefsetId = detail.langRefsets[0].refsetId;
        }
      },
      (error) => {
        this.panels = this.panels.filter(p => p.panelId !== panelId);
        this.snackBar.open(this.errorMessage(error), 'Dismiss', { duration: 8000 });
      }
    );
  }

  saveAndClose(panel: AdminConceptEditorPanelState): void {
    if (!panel.detail || !this.selectedEdition) {
      return;
    }
    panel.saving = true;
    const body = { descriptions: panel.detail.descriptions };
    lastValueFrom(this.simplexService.updateAdminConceptDescriptions(
      this.selectedEdition,
      panel.detail.conceptId,
      body
    )).then(
      () => {
        this.panels = this.panels.filter(p => p.panelId !== panel.panelId);
        this.snackBar.open('Concept descriptions saved.', 'Dismiss', { duration: 4000 });
      },
      (error) => {
        panel.saving = false;
        this.snackBar.open(this.errorMessage(error), 'Dismiss', { duration: 8000 });
      }
    );
  }

  dismissPanel(panel: AdminConceptEditorPanelState): void {
    this.panels = this.panels.filter(p => p.panelId !== panel.panelId);
  }

  private errorMessage(error: any): string {
    return error?.error?.message || error?.message || 'Request failed.';
  }
}
