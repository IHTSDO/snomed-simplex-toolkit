import { Component, Input, OnChanges, SimpleChanges, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { ConceptsListComponent } from './concepts-list/concepts-list.component';

@Component({
  selector: 'app-artifacts',
  templateUrl: './artifacts.component.html',
  styleUrls: ['./artifacts.component.scss']
})
export class ArtifactsComponent implements OnInit, OnChanges, OnDestroy {
  @Input() edition: string;

  @ViewChild('conceptsList') conceptsList: ConceptsListComponent;
  
  subsets = [];
  translations = [];
  maps = [];
  private cancelOngoingRequests$ = new Subject<void>();
  showConceptsArtifact: boolean = false;
  conceptsArtifact: any = { 
    conceptId: 'concepts', 
    fsn: { term:'Simple extension concepts' },
    pt: { term:'Simple extension concepts' }, 
    count: '-' 
  };

  selectedArtifact = null;
  newArtifactMode = false;
  loadingSubsets = false;
  loadingTranslations = false;
  loadingMaps = false;
  saving = false;

  loadingReleaseStatus = false;
  releaseStatus = '';

  artifactTypes = ["subset", "map", "translation"];
  form: FormGroup = this.fb.group({
    type: ['', Validators.required],
    preferredTerm: ['', Validators.required]
  });

  constructor(private fb: FormBuilder,
              private simplexService: SimplexService,
              private snackBar: MatSnackBar) {}

  ngOnInit() {
    this.form.get('type').valueChanges.subscribe(value => {
      this.toggleFormControls(value);
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['edition'] && changes['edition'].currentValue) {
      this.loadArtifacts(changes['edition'].currentValue);
      this.selectedArtifact = null;
    }
  }

  toggleFormControls(typeValue: string) {
    this.formKeys.forEach(controlName => {
      if (controlName !== 'type') {
        const control = this.form.get(controlName);
        if (typeValue === 'concepts') {
          control.disable();
        } else {
          control.enable();
        }
      }
    });
  }

  loadArtifacts(edition: string) {
    // Cancel ongoing requests
    this.cancelOngoingRequests$.next();
    this.loadConceptsArtifact(edition);
    this.loadSubsets(edition);
    this.loadTranslations(edition);
    this.loadMaps(edition);
    this.releaseStatus = '';
    this.loadingReleaseStatus = true;
    this.simplexService.getCodeSystemReleaseStatus(edition)
        .pipe(takeUntil(this.cancelOngoingRequests$))
        .subscribe((status) => {
            this.releaseStatus = status;
            this.loadingReleaseStatus = false;
    });
    if (this.conceptsList) {
      this.conceptsList.loadConcepts();
    }
  }

  refreshArtifacts() {
    this.loadArtifacts(this.edition);
  }

  loadConceptsArtifact(conceptId: string) {
    lastValueFrom(this.simplexService.getEdition(this.edition)).then(
      (edition) => {
        this.showConceptsArtifact = edition?.showCustomConcepts;
        if (this.showConceptsArtifact) {
          lastValueFrom(this.simplexService.getConcepts(this.edition,0,1)).then(
            (concepts) => {
              this.conceptsArtifact.count = concepts.total;
            }
          )
        }
      }
    )
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
    const type = this.form.value.type;
    if (this.form.valid) {
      const subset = {
        preferredTerm: this.form.value.preferredTerm
      };
      this.saving = true;
      // Set the form to disabled
      this.form.disable();
      switch (type) {
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
      case 'concepts':
          lastValueFrom(this.simplexService.showCustomConcepts(this.edition)).then(
            (edition) => {
              this.saving = false;
              this.form.reset();
              this.newArtifactMode = false;
              this.loadArtifacts(this.edition);
            },
            (error) => {
              console.error(error);
              this.saving = false;
              this.snackBar.open('Failed to enable custom concepts', 'Dismiss', {
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
      case 'concepts':
        return 'pill-purple';
      default:
        return '';  // Default
  
    }
  }

  browseToConcept(conceptId: string) {
    lastValueFrom(this.simplexService.getEdition(this.edition)).then(
      (edition) => {
        const branch = edition.branchPath;
        let langs = Object.keys(edition.languages).join(',');
        const tab = window.open(`/browser/?perspective=full&conceptId1=${conceptId}&edition=${branch}&release=&languages=${langs}`, 'simplex-browser');
        tab.focus();
      }
    )
  }    
  
}