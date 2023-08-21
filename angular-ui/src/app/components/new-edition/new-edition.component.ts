import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-new-edition',
  templateUrl: './new-edition.component.html',
  styleUrls: ['./new-edition.component.scss']
})
export class NewEditionComponent {

  saving = false;

  @Output() closePanel = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    name: ['', Validators.required],
    shortName: ['', Validators.required],
    defaultModule: [''],
    defaultModuleDisplay: [''],
    dependantVersionEffectiveTime: ['', Validators.required],
    branchPath: ['', Validators.required],
    workingBranchPath: [''],
    simplexWorkingBranch: ['']
  });

  get formKeys(): string[] {
    return Object.keys(this.form.controls);
  }

  constructor(
    private fb: FormBuilder,
    private simplexService: SimplexService,
    private snackBar: MatSnackBar
  ) { }

  submit() {
    this.form.markAllAsTouched();
    if (this.form.valid) {
      const edition = {
        name: this.form.value.name,
        shortName: this.form.value.shortName,
        defaultModule: this.form.value.defaultModule,
        defaultModuleDisplay: this.form.value.defaultModuleDisplay,
        dependantVersionEffectiveTime: this.form.value.dependantVersionEffectiveTime,
        branchPath: this.form.value.branchPath,
        workingBranchPath: this.form.value.workingBranchPath,
        simplexWorkingBranch: this.form.value.simplexWorkingBranch
      };
      this.saving = true;
      lastValueFrom(this.simplexService.createEdition(edition)).then(
        (edition) => {
          console.log(edition);
          this.saving = false;
          this.closePanelEvent();
        },
        (error) => {
          console.error(error);
          this.saving = false;
          this.snackBar.open('Failed to create edition', 'Dismiss', {
            duration: 5000
          });
        }
      );
    }
  }

  closePanelEvent() {
    this.closePanel.emit();
  }

}