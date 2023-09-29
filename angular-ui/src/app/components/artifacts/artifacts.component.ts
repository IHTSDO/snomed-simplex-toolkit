import { Component, Input, OnChanges, SimpleChanges, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-artifacts',
  templateUrl: './artifacts.component.html',
  styleUrls: ['./artifacts.component.scss']
})
export class ArtifactsComponent implements OnChanges, OnDestroy {
  @Input() edition: string;
  
  subsets = [];
  translations = [];
  maps = [];
  private cancelOngoingRequests$ = new Subject<void>();

  selectedArtifact = null;
  newArtifactMode = false;
  loadingSubsets = false;
  loadingTranslations = false;
  loadingMaps = false;
  saving = false;

  selectedFile: File = null;
  selectedFileType = null;

  artifactTypes = ["subset", "map", "translation"];
  form: FormGroup = this.fb.group({
    type: ['', Validators.required],
    preferredTerm: ['', Validators.required]
  });

  constructor(private fb: FormBuilder,
              private simplexService: SimplexService,
              private snackBar: MatSnackBar) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes['edition'] && changes['edition'].currentValue) {
      this.loadArtifacts(changes['edition'].currentValue);
    }
  }

  loadArtifacts(edition: string) {
    // Cancel ongoing requests
    this.cancelOngoingRequests$.next();

    this.loadSubsets(edition);
    this.loadTranslations(edition);
    this.loadMaps(edition);
  }

  loadSubsets(edition: string) {
    this.subsets = [];
    this.loadingSubsets = true;
    this.simplexService.getSimpleRefsets(edition)
        .pipe(takeUntil(this.cancelOngoingRequests$))
        .subscribe((subsets) => {
            this.subsets = subsets;
            this.loadingSubsets = false;
        });
  }

  loadTranslations(edition: string) {
    this.translations = [];
    this.loadingTranslations = true;
    this.simplexService.getTranslations(edition)
        .pipe(takeUntil(this.cancelOngoingRequests$))
        .subscribe((translations) => {
            this.translations = translations;
            this.loadingTranslations = false;
        });
  }

  loadMaps(edition: string) {
    this.maps = [];
    this.loadingMaps = true;
    this.simplexService.getSimpleMaps(edition)
        .pipe(takeUntil(this.cancelOngoingRequests$))
        .subscribe((maps) => {
            this.maps = maps;
            this.loadingMaps = false;
        });
  }

  ngOnDestroy() {
    this.cancelOngoingRequests$.next();
    this.cancelOngoingRequests$.complete();
  }

  get formKeys(): string[] {
    return Object.keys(this.form.controls);
  }

  onClick(item: any, type: string) {
    item.type = type;
    this.selectedArtifact = item;
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
      switch (this.form.value.type) {
        case 'subset':
          lastValueFrom(this.simplexService.createSimpleRefset(this.edition, subset)).then(
            (edition) => {
              this.saving = false;
              this.form.reset();
              this.newArtifactMode = false;
              this.loadArtifacts(this.edition);
            },
            (error) => {
              console.error(error);
              this.saving = false;
              this.snackBar.open('Failed to create subset', 'Dismiss', {
                duration: 5000
              });
            }
          );
          break;
        case 'map':
          lastValueFrom(this.simplexService.createMap(this.edition, subset)).then(
            (edition) => {
              this.saving = false;
              this.form.reset();
              this.newArtifactMode = false;
              this.loadArtifacts(this.edition);
            },
            (error) => {
              console.error(error);
              this.saving = false;
              this.snackBar.open('Failed to create map', 'Dismiss', {
                duration: 5000
              });
            }
          );
          break;
        case 'translation':
          lastValueFrom(this.simplexService.createTranslations(this.edition, subset)).then(
            (edition) => {
              this.saving = false;
              this.form.reset();
              this.newArtifactMode = false;
              this.loadArtifacts(this.edition);
            },
            (error) => {
              console.error(error);
              this.saving = false;
              this.snackBar.open('Failed to create translation', 'Dismiss', {
                duration: 5000
              });
            }
          );
          break;
      }
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

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length) {
      this.selectedFile = input.files[0];
    }
  }
  
  async uploadRefsetToolTranslation(refsetId: string, componentType: string, fileType: string): Promise<void> {
    if (this.selectedFile && this.edition && componentType && fileType) {
        try {
            if (componentType === 'translation' && fileType === 'weblateTranslation') {
              const response = await lastValueFrom(
                  this.simplexService.uploadWeblateTranslation(this.edition, refsetId, this.selectedFile)
              );
              console.log('File uploaded successfully:', response);
            } else if (componentType === 'translation' && fileType === 'refsetToolTranslation') {
              const response = await lastValueFrom(
                  this.simplexService.uploadRefsetToolTranslation(this.edition, refsetId, this.selectedFile)
              );
              console.log('File uploaded successfully:', response);
            } else {
              console.error('File upload failed: Invalid componentType or fileType');
            }
        } catch (error) {
            console.error('File upload failed:', error);
        }
    }
  }

  getFileTypes(componentType: string) {
    switch (componentType) {
      case 'subset':
        let optionsS = ['refsetToolSubset'];
        this.selectedFileType = optionsS[0];
        return optionsS;
      case 'map':
        let optionsM =  ["snap2snomedMap"];
        this.selectedFileType = optionsM[0];
        return optionsM;
      case 'translation':
        let optionsT =  ["refsetToolTranslation","weblateTranslation"];
        this.selectedFileType = optionsT[0];
        return optionsT;
      default:
        this.selectedFileType = null;
        return [];  // Default
  
    }
  }
  
}