import { Component, Input, SimpleChanges } from '@angular/core';
import { FormGroup, Validators, FormBuilder } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-translations',
  templateUrl: './translations.component.html',
  styleUrls: ['./translations.component.scss']
})
export class TranslationsComponent {
  @Input() edition: string;
  translations = [];
  selectedTranslation = null;
  newTranslationMode = false;
  loading = false;
  translationFields = ["idAndFsnTerm", "active", "activeMemberCount", "moduleId"];
  saving = false;

  form: FormGroup = this.fb.group({
    preferredTerm: ['', Validators.required]
  });

  constructor(private fb: FormBuilder,
              private simplexService: SimplexService,
              private snackBar: MatSnackBar) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes['edition'] && changes['edition'].currentValue) {
      this.loadTranslations(changes['edition'].currentValue);
    }
  }

  loadTranslations(edition: string) {
    this.translations = [];
    this.loading = true;
    this.simplexService.getTranslations(edition).subscribe((translations) => {
      this.translations = translations;
      this.loading = false;
    });
  }

  get formKeys(): string[] {
    return Object.keys(this.form.controls);
  }

  onClick(item: any) {
    this.selectedTranslation = item;
  }
  submit() {
    this.form.markAllAsTouched();
    if (this.form.valid) {
      const translation = {
        preferredTerm: this.form.value.preferredTerm
      };
      this.saving = true;
      // Set the form to disabled
      this.form.disable();
      lastValueFrom(this.simplexService.createTranslations(this.edition, translation)).then(
        (edition) => {
          console.log(edition);
          this.saving = false;
          this.form.reset();
          this.newTranslationMode = false;
          this.loadTranslations(this.edition);
        },
        (error) => {
          console.error(error);
          this.saving = false;
          this.snackBar.open('Failed to create translation', 'Dismiss', {
            duration: 5000
          });
        }
      );
    }
  }
}
