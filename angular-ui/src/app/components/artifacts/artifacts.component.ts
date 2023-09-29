import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-artifacts',
  templateUrl: './artifacts.component.html',
  styleUrls: ['./artifacts.component.scss']
})
export class ArtifactsComponent implements OnChanges {
  @Input() edition: string;
  
  subsets = [];
  translations = [];
  maps = [];

  selectedArtifact = null;
  
  newSubsetMode = false;
  
  loadingSubsets = false;
  loadingTranslations = false;
  loadingMaps = false;

  // subsetFields = ["idAndFsnTerm", "active", "activeMemberCount", "moduleId"];
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
      this.loadTranslations(changes['edition'].currentValue);
      this.loadmaps(changes['edition'].currentValue);
    }
  }

  loadSubsets(edition: string) {
    this.subsets = [];
    this.loadingSubsets = true;
    this.simplexService.getSimpleRefsets(edition).subscribe((subsets) => {
      this.subsets = subsets;
      this.loadingSubsets = false;
    });
  }

  loadTranslations(edition: string) {
    this.translations = [];
    this.loadingTranslations = true;
    this.simplexService.getTranslations(edition).subscribe((translations) => {
      this.translations = translations;
      this.loadingTranslations = false;
    });
  }

  loadmaps(edition: string) {
    this.maps = [];
    this.loadingMaps = true;
    this.simplexService.getSimpleMaps(edition).subscribe((maps) => {
      this.maps = maps;
      this.loadingMaps = false;
    });
  }

  get formKeys(): string[] {
    return Object.keys(this.form.controls);
  }

  onClick(item: any, type: string) {
    item.type = type;
    this.selectedArtifact = item;
    console.log(item);
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
  getArtifactClass(type: string): string {
    switch (type) {
      case 'subset':
        return 'pill-green';
      case 'map':
        return 'pill-yellow';
      case 'translation':
        return 'pill-blue';
      default:
        return '';  // Default
  
    }
  }
}