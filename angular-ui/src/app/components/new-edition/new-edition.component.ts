import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-new-edition',
  templateUrl: './new-edition.component.html',
  styleUrls: ['./new-edition.component.scss']
})
export class NewEditionComponent implements OnInit {
  saving = false;

  @Output() closePanel = new EventEmitter<void>();
  @Output() editionSaved = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(24), this.forbiddenWordsValidator]],
    shortName: ['SNOMEDCT-', Validators.required],
    createModule: [true],
    moduleId: [''],
    moduleName: [''],
    namespace: ['', Validators.required]
  });

  constructor(
    private fb: FormBuilder,
    private simplexService: SimplexService,
    private snackBar: MatSnackBar
  ) { }

  ngOnInit(): void {
    this.form.get('moduleId')?.valueChanges.subscribe(() => this.updateNamespaceFromModuleId());
    this.form.get('createModule')?.valueChanges.subscribe(() => this.updateNamespaceFromModuleId());
  }

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
      const { createModule, name, shortName, namespace, moduleId, moduleName } = this.form.value;
      const edition = {
        createModule,
        name,
        shortName,
        namespace,
        ...(createModule ? { moduleName } : { moduleId })
      };
      this.saving = true;
      this.form.disable();

      lastValueFrom(this.simplexService.createEdition(edition)).then(
        () => {
          this.saving = false;
          this.closePanelEvent();
          this.editionSaved.emit();
        },
        (error) => {
          console.error(error);
          this.saving = false;
          this.form.enable();
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

  private updateNamespaceFromModuleId(): void {
    if (this.form.get('createModule')?.value) {
      return;
    }
    const namespace = this.extractNamespaceFromModuleId(this.form.get('moduleId')?.value);
    if (namespace) {
      this.form.get('namespace')?.setValue(namespace);
    }
  }

  private extractNamespaceFromModuleId(moduleId: string): string | null {
    const trimmed = (moduleId ?? '').trim();
    if (!trimmed || trimmed.length < 10) {
      return null;
    }
    return trimmed.slice(0, -3).slice(-7);
  }
}
