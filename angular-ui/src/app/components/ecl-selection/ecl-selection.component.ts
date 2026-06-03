import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TerminologyService } from 'src/app/services/simplex/terminology.service';

@Component({
  selector: 'app-ecl-selection',
  templateUrl: './ecl-selection.component.html',
  styleUrls: ['./ecl-selection.component.scss']
})
export class EclSelectionComponent implements OnChanges {

  @Input() edition: any;
  @Input() eclForm: FormGroup;

  eclInputMethod: 'manual' | 'refset' | 'derivative' | 'subtype' = 'subtype';
  selectedRefsetCode = '';
  selectedDerivativeCode = '';
  selectedSubtype: any = null;

  refsets: any[] = [];
  loadingRefsets = false;
  refsetsLoaded = false;

  derivatives: any[] = [];
  loadingDerivatives = false;
  derivativesLoaded = false;

  constructor(
    private terminologyService: TerminologyService,
    private snackBar: MatSnackBar
  ) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes['edition'] && !changes['edition'].firstChange) {
      this.resetSelection();
    }
  }

  get selectedCodesystem(): string {
    switch (this.eclInputMethod) {
      case 'derivative':
        return 'SNOMEDCT-DERIVATIVES';
      case 'manual': {
        const eclInput = this.eclForm?.get('ecl')?.value || '';
        const idMatches = eclInput.match(/\b\d+\b/g);
        if (idMatches && idMatches.length > 0) {
          const extractedIds = idMatches.filter((id: string) => id.length > 0 && !id.startsWith('0'));
          const hasDerivativeMatch = extractedIds.some((id: string) =>
            this.derivatives.some(derivative => derivative.code === id)
          );
          if (hasDerivativeMatch) {
            return 'SNOMEDCT-DERIVATIVES';
          }
        }
        return this.edition?.shortName || 'http://snomed.info/sct';
      }
      case 'refset':
      case 'subtype':
      default:
        return this.edition?.shortName || 'http://snomed.info/sct';
    }
  }

  onEclInputMethodChange() {
    this.eclForm?.patchValue({ ecl: '' });
    this.selectedRefsetCode = '';
    this.selectedDerivativeCode = '';
    this.selectedSubtype = null;

    if (this.eclInputMethod === 'refset' && !this.refsetsLoaded) {
      this.loadRefsetsFromServer();
    }

    if ((this.eclInputMethod === 'derivative' || this.eclInputMethod === 'manual') && !this.derivativesLoaded) {
      this.loadDerivativesFromServer();
    }

    if (this.eclInputMethod === 'subtype' && !this.edition?.branchPath) {
      this.snackBar.open('Branch path not available. Please ensure an edition is selected.', 'Dismiss', {
        duration: 5000
      });
      this.eclInputMethod = 'manual';
    }
  }

  onRefsetSelectionChange(refsetCode: string) {
    this.selectedRefsetCode = refsetCode;
    const selectedRefset = this.refsets.find(refset => refset.code === refsetCode);
    if (selectedRefset) {
      const eclValue = `^ ${selectedRefset.code} |${selectedRefset.display}|`;
      this.eclForm.patchValue({ ecl: eclValue });
    }
  }

  onDerivativeSelectionChange(derivativeCode: string) {
    this.selectedDerivativeCode = derivativeCode;
    const selectedDerivative = this.derivatives.find(derivative => derivative.code === derivativeCode);
    if (selectedDerivative) {
      const eclValue = `^ ${selectedDerivative.code} |${selectedDerivative.display}|`;
      this.eclForm.patchValue({ ecl: eclValue });
    }
  }

  onSubtypeSelectionChange(subtype: any) {
    this.selectedSubtype = subtype;
    if (subtype?.code) {
      const eclValue = `<< ${subtype.code} |${subtype.display}|`;
      this.eclForm.patchValue({ ecl: eclValue });
    } else {
      this.eclForm.patchValue({ ecl: '' });
    }
  }

  loadRefsetsFromServer() {
    if (!this.edition?.defaultModule) {
      return;
    }

    this.loadingRefsets = true;
    const moduleId = this.edition.defaultModule;
    const branchPath = this.edition.branchPath;

    this.terminologyService.getRefsetsFromNativeApi(branchPath, moduleId).subscribe(
      (response) => {
        if (response?.items?.length > 0) {
          this.refsets = response.items.map((item: any) => ({
            code: item.conceptId,
            display: item.pt?.term || item.fsn?.term || `Refset ${item.conceptId}`,
          }));
        } else {
          this.refsets = [];
        }
        this.loadingRefsets = false;
        this.refsetsLoaded = true;
      },
      () => {
        this.snackBar.open('Failed to load refsets from server', 'Dismiss', { duration: 5000 });
        this.loadingRefsets = false;
        this.refsets = [];
      }
    );
  }

  loadDerivativesFromServer() {
    this.loadingDerivatives = true;
    this.terminologyService.getDerivativesFromServer().subscribe(
      (response) => {
        if (response?.referenceSets) {
          this.derivatives = Object.values(response.referenceSets).map((refset: any) => ({
            code: refset.conceptId,
            display: refset.pt?.term || refset.fsn?.term || `Derivative ${refset.conceptId}`,
            memberCount: response.memberCountsByReferenceSet?.[refset.conceptId] || 0
          }));
        } else {
          this.derivatives = [];
        }
        this.loadingDerivatives = false;
        this.derivativesLoaded = true;
      },
      () => {
        this.snackBar.open('Failed to load derivatives from server', 'Dismiss', { duration: 5000 });
        this.loadingDerivatives = false;
        this.derivatives = [];
      }
    );
  }

  resetSelection() {
    this.eclInputMethod = 'subtype';
    this.selectedRefsetCode = '';
    this.selectedDerivativeCode = '';
    this.selectedSubtype = null;
    this.refsetsLoaded = false;
    this.derivativesLoaded = false;
    this.refsets = [];
    this.derivatives = [];
    this.eclForm?.patchValue({ ecl: '' });
  }

}
