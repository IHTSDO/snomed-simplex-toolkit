import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { EclBuilderService } from '../ecl-builder.service';

export type EclBuilderDialogMode = 'builder' | 'plain';

export interface EclBuilderDialogData {
  ecl: string;
  branchPath: string;
}

@Component({
  selector: 'app-ecl-builder-dialog',
  templateUrl: './ecl-builder-dialog.component.html',
  styleUrls: ['./ecl-builder-dialog.component.scss'],
  standalone: false
})
export class EclBuilderDialogComponent implements OnInit {
  localEcl = '';
  branchPath = 'MAIN';
  mode: EclBuilderDialogMode = 'builder';
  isValid = false;
  unsupportedFeatures: string[] = [];
  unsupportedMessage = '';
  modeSwitchWarning = '';
  generatedEcl = '';

  constructor(
    public dialogRef: MatDialogRef<EclBuilderDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: EclBuilderDialogData,
    private eclBuilderService: EclBuilderService
  ) {
    this.localEcl = data.ecl ?? '';
    this.branchPath = data.branchPath ?? 'MAIN';
  }

  ngOnInit(): void {
    this.eclBuilderService.setBranchPath(this.branchPath);
    this.checkInitialUnsupportedFeatures();
  }

  onNoClick(): void {
    this.dialogRef.close();
  }

  confirm(): void {
    this.dialogRef.close(this.localEcl.trim());
  }

  eclChanged(ecl: string): void {
    this.localEcl = ecl;
    this.generatedEcl = ecl;
  }

  validityChanged(isValid: boolean): void {
    this.isValid = isValid;
  }

  unsupportedFeaturesChanged(features: string[]): void {
    this.unsupportedFeatures = features;
    if (features.length) {
      this.unsupportedMessage = `Unsupported in visual builder: ${features.join(', ')}. Switch to plain text to edit this ECL.`;
    } else {
      this.unsupportedMessage = '';
    }
  }

  setMode(mode: EclBuilderDialogMode): void {
    this.modeSwitchWarning = '';

    if (mode === 'plain' && this.mode === 'builder') {
      this.localEcl = this.generatedEcl || this.localEcl;
    }

    if (mode === 'builder' && this.mode === 'plain') {
      const ecl = this.localEcl.trim();
      if (!ecl) {
        this.mode = 'builder';
        return;
      }

      this.eclBuilderService.stringToModel(ecl).subscribe({
        next: (model) => {
          const unsupported = this.eclBuilderService.collectUnsupportedFeatures(model);
          if (unsupported.length) {
            this.modeSwitchWarning = `This ECL uses features not supported by the visual builder (${unsupported.join(', ')}). Stay in plain text mode to edit it.`;
            this.mode = 'plain';
            return;
          }
          this.mode = 'builder';
        },
        error: () => {
          this.modeSwitchWarning = 'Unable to parse this ECL in the visual builder. Stay in plain text mode to edit it.';
          this.mode = 'plain';
        }
      });
      return;
    }

    this.mode = mode;
  }

  switchToPlainText(): void {
    this.setMode('plain');
  }

  get canConfirm(): boolean {
    if (this.mode === 'plain') {
      return !!this.localEcl.trim();
    }
    return this.isValid;
  }

  private checkInitialUnsupportedFeatures(): void {
    const ecl = this.localEcl.trim();
    if (!ecl) {
      return;
    }

    this.eclBuilderService.stringToModel(ecl).subscribe({
      next: (model) => {
        const unsupported = this.eclBuilderService.collectUnsupportedFeatures(model);
        if (unsupported.length) {
          this.unsupportedFeatures = unsupported;
          this.unsupportedMessage = `This ECL uses features not supported by the visual builder (${unsupported.join(', ')}). Plain text mode is recommended.`;
          this.mode = 'plain';
        }
      },
      error: () => {
        this.mode = 'plain';
        this.modeSwitchWarning = 'Unable to parse this ECL in the visual builder. Plain text mode is recommended.';
      }
    });
  }
}
