import { Component, EventEmitter, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
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
  @Output() editionSaved = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(24), this.forbiddenWordsValidator]],
    shortName: ['', Validators.required],
    moduleId: [''],
    moduleName: [''],
    namespace: ['', Validators.required]
  });

  get formKeys(): string[] {
    return Object.keys(this.form.controls);
  }

  constructor(
    private fb: FormBuilder,
    private simplexService: SimplexService,
    private snackBar: MatSnackBar
  ) { }

  forbiddenWordsValidator(control: AbstractControl): ValidationErrors | null {
    const forbiddenWords = ['edition', 'extension'];
    const value = control.value?.toLowerCase();
    if (value && forbiddenWords.some(word => value.includes(word))) {
      return { forbiddenWords: true };
    }
    return null;
  }

  submit() {
    this.form.markAllAsTouched();
    if (this.form.valid) {
      const edition = {
        createModule: true,
        name: this.form.value.name,
        shortName: this.form.value.shortName,
        moduleId: this.form.value.moduleId,
        moduleName: this.form.value.moduleName,
        namespace: this.form.value.namespace
      };
      this.saving = true;
      this.form.disable(); // Disable form to prevent multiple submissions

      lastValueFrom(this.simplexService.createEdition(edition)).then(
        () => {
          this.saving = false;
          this.closePanelEvent();
          this.editionSaved.emit();
        },
        (error) => {
          console.error(error);
          this.saving = false;
          this.form.enable(); // Re-enable form on error
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
