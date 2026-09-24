import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  AdminAcceptability,
  AdminConceptDescription,
  AdminConceptEditorPanelState,
  AdminConceptLangRefset,
  DescriptionModuleKind
} from 'src/app/models/admin-concept-editor';

@Component({
  selector: 'app-admin-concept-editor-panel',
  templateUrl: './admin-concept-editor-panel.component.html',
  styleUrl: './admin-concept-editor-panel.component.scss'
})
export class AdminConceptEditorPanelComponent {

  @Input({ required: true }) panel!: AdminConceptEditorPanelState;
  @Input() editionShortName = '';
  @Input() editionDisplayName = '';

  @Output() saveAndClose = new EventEmitter<AdminConceptEditorPanelState>();
  @Output() dismiss = new EventEmitter<AdminConceptEditorPanelState>();

  constructor(private snackBar: MatSnackBar) { }

  onDescriptionChange(): void {
    this.panel.dirty = true;
  }

  applicableLangRefsets(description: AdminConceptDescription): AdminConceptLangRefset[] {
    if (!this.panel.detail) {
      return [];
    }
    return this.panel.detail.langRefsets.filter(refset =>
      refset.languageCode === description.lang ||
      this.hasAcceptabilityInRefset(description, refset.refsetId)
    );
  }

  private hasAcceptabilityInRefset(description: AdminConceptDescription, refsetId: string): boolean {
    const value = description.acceptabilityMap?.[refsetId];
    return value === 'PREFERRED' || value === 'ACCEPTABLE';
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
    if (!this.panel.detail || !description.active || description.type !== 'SYNONYM') {
      return false;
    }
    if (description.acceptabilityMap?.[refsetId] !== 'PREFERRED') {
      return false;
    }
    const preferredSynonymCount = this.panel.detail.descriptions.filter(d =>
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
    this.panel.dirty = true;
  }

  addSynonym(): void {
    if (!this.panel.detail) {
      return;
    }
    const term = this.panel.newSynonymTerm?.trim();
    if (!term || !this.panel.newSynonymRefsetId) {
      this.snackBar.open('Enter a term and select a language refset.', 'Dismiss', { duration: 5000 });
      return;
    }
    const refset = this.panel.detail.langRefsets.find(r => r.refsetId === this.panel.newSynonymRefsetId);
    const lang = refset?.languageCode || 'en';
    const description: AdminConceptDescription = {
      descriptionId: null,
      term,
      type: 'SYNONYM',
      lang,
      active: true,
      released: false,
      moduleId: this.panel.detail.defaultModuleId,
      acceptabilityMap: { [this.panel.newSynonymRefsetId]: 'ACCEPTABLE' }
    };
    this.panel.detail.descriptions = [...this.panel.detail.descriptions, description];
    this.panel.newSynonymTerm = '';
    this.panel.dirty = true;
  }

  descriptionModuleKind(description: AdminConceptDescription): DescriptionModuleKind {
    const detail = this.panel.detail;
    const defaultModuleId = detail?.defaultModuleId;
    const moduleId = description.moduleId ?? defaultModuleId;
    if (moduleId && defaultModuleId && moduleId === defaultModuleId) {
      return 'extension';
    }
    if (moduleId && detail?.internationalModuleIds?.includes(moduleId)) {
      return 'international';
    }
    return 'other';
  }

  moduleDotTooltip(description: AdminConceptDescription): string {
    const kind = this.descriptionModuleKind(description);
    if (kind === 'extension') {
      const codeSystem = this.panel.detail?.codeSystem ?? '';
      const editionName = this.editionDisplayName || this.editionShortName;
      return `${codeSystem} / ${editionName}`;
    }
    if (kind === 'international') {
      return 'International';
    }
    const moduleId = description.moduleId ?? this.panel.detail?.defaultModuleId ?? '';
    return moduleId;
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

  canSaveAndClose(): boolean {
    return !!this.panel.detail && this.panel.dirty && !this.panel.loading && !this.panel.saving;
  }

  onSaveAndClose(): void {
    if (!this.canSaveAndClose()) {
      return;
    }
    this.saveAndClose.emit(this.panel);
  }

  onDismiss(): void {
    if (this.panel.dirty) {
      const confirmed = window.confirm('Discard unsaved changes for this concept?');
      if (!confirmed) {
        return;
      }
    }
    this.dismiss.emit(this.panel);
  }
}
