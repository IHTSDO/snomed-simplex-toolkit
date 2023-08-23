import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-subsets',
  templateUrl: './subsets.component.html',
  styleUrls: ['./subsets.component.scss']
})
export class SubsetsComponent implements OnChanges {
  @Input() edition: string;
  subsets = [];
  selectedSubset = null;
  newSubsetMode = false;
  loading = false;
  subsetFields = ["idAndFsnTerm", "active", "activeMemberCount", "moduleId"];
  saving = false;

  form: FormGroup = this.fb.group({
    preferredTerm: ['', Validators.required]
  });

  constructor(private fb: FormBuilder,
              private simplexService: SimplexService,
              private snackBar: MatSnackBar) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes['edition'] && changes['edition'].currentValue) {
      this.loadSubsets(changes['edition'].currentValue);
    }
  }

  loadSubsets(edition: string) {
    this.subsets = [];
    this.loading = true;
    this.simplexService.getSimpleRefsets(edition).subscribe((subsets) => {
      this.subsets = subsets;
      this.loading = false;
    });
  }

  get formKeys(): string[] {
    return Object.keys(this.form.controls);
  }

  onClick(item: any) {
    this.selectedSubset = item;
  }
  submit() {
    this.form.markAllAsTouched();
    if (this.form.valid) {
      const subset = {
        preferredTerm: this.form.value.preferredTerm
      };
      this.saving = true;
      // Set the form to disabled
      this.form.disable();
      lastValueFrom(this.simplexService.createSimpleRefset(this.edition, subset)).then(
        (edition) => {
          this.saving = false;
          this.form.reset();
          this.newSubsetMode = false;
          this.loadSubsets(this.edition);
        },
        (error) => {
          console.error(error);
          this.saving = false;
          this.snackBar.open('Failed to create subset', 'Dismiss', {
            duration: 5000
          });
        }
      );
    }
  }
}