import { Component, Input, OnChanges, SimpleChanges, OnDestroy, OnInit, ViewChild, ChangeDetectorRef } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom, Subject, Subscription } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { ConceptsListComponent } from './concepts-list/concepts-list.component';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-artifacts',
  templateUrl: './artifacts.component.html',
  styleUrls: ['./artifacts.component.scss']
})
export class ArtifactsComponent implements OnInit, OnDestroy {
  edition: string | null = null;

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
  editionDetails: any;
  newArtifactMode = false;
  loadingSubsets = false;
  loadingTranslations = false;
  loadingMaps = false;
  updatingEdition = false;
  saving = false;
  private subscriptions: Subscription = new Subscription();
  languageCodes: any[] = [];


  artifactTypes = ["subset", "map", "translation"];
  form: FormGroup = this.fb.group({
    type: ['', Validators.required]
  });

  constructor(private fb: FormBuilder,
              private simplexService: SimplexService,
              private changeDetectorRef: ChangeDetectorRef,
              private uiService: UiConfigurationService,
              private router: Router,
              private snackBar: MatSnackBar) {}

  ngOnInit() {
    this.form.get('type').valueChanges.subscribe(value => {
      this.toggleFormControls(value);
    });
    const editionSubscription = this.uiService.getSelectedEdition().subscribe(edition => {
      let url = this.router.url;
      if (edition && url.includes('artifact')) {
        this.editionDetails = edition;
        this.edition = edition.shortName;
        this.updatingEdition = true;
        this.loadArtifacts(edition.shortName);
        this.selectedArtifact = null;
        this.newArtifactMode = false;
      }
    });
    this.subscriptions.add(editionSubscription);
    this.simplexService.getLanguageCodes().subscribe(data => {
      this.languageCodes = data;
    })
  }

  toggleFormControls(typeValue: string) {
    if (typeValue === 'concepts') {
        this.form.removeControl('preferredTerm');
        this.form.removeControl('languageCode');
    } else if (typeValue == 'translation') {
        this.form.addControl('preferredTerm', this.fb.control('', Validators.required));
        this.form.addControl('languageCode', this.fb.control('', [Validators.required, this.languageCodeValidator]));
    } else {
        this.form.removeControl('languageCode');
        this.form.addControl('preferredTerm', this.fb.control('', Validators.required));
    }
  }

  languageCodeValidator(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const isValid = /^[a-z]{2}$/.test(value);
    return isValid ? null : { invalidLanguageCode: true };
  }

  loadArtifacts(edition: string) {
    // Cancel ongoing requests
    this.cancelOngoingRequests$.next();
    this.loadConceptsArtifact(edition);
    this.loadSubsets(edition);
    this.loadTranslations(edition);
    this.loadMaps(edition);
    if (this.conceptsList) {
      this.conceptsList.loadConcepts();
    }
  }

  refreshArtifacts() {
    this.loadArtifacts(this.edition);
  }

  updateSelectedArtifact(artifact: any) {
    if (artifact.conceptId == this.selectedArtifact?.conceptId) {
      this.selectedArtifact = artifact;
      this.changeDetectorRef.detectChanges();
    }
  }

  loadConceptsArtifact(conceptId: string) {
    // this.editionDetails = null;
    lastValueFrom(this.simplexService.getEdition(this.edition)).then(
      (edition) => {
        this.editionDetails = edition;
        this.showConceptsArtifact = edition?.showCustomConcepts;
        if (this.showConceptsArtifact) {
          lastValueFrom(this.simplexService.getConcepts(this.edition,0,1)).then(
            (concepts) => {
              this.conceptsArtifact.count = concepts.total;
              this.updateSelectedArtifact(this.conceptsArtifact)
            }
          )
        }
      }
    )
  }    

  loadSubsets(edition: string) {
    this.loadingSubsets = true;
    this.simplexService.getSimpleRefsets(edition)
        .pipe(takeUntil(this.cancelOngoingRequests$))
        .subscribe((subsets) => {
            this.subsets = subsets;
            this.loadingSubsets = false;
            if (!this.loadingMaps && !this.loadingTranslations) {
              this.updatingEdition = false;
            }
            this.subsets.forEach(subset => {
              this.updateSelectedArtifact(subset);
            });
            this.changeDetectorRef.detectChanges();
        });
  }

  loadTranslations(edition: string) {
    this.loadingTranslations = true;
    this.simplexService.getTranslations(edition)
        .pipe(takeUntil(this.cancelOngoingRequests$))
        .subscribe((translations) => {
            this.translations = translations;
            this.loadingTranslations = false;
            if (!this.loadingMaps && !this.loadingSubsets) {
              this.updatingEdition = false;
            }
            this.translations.forEach(translation => {
              this.updateSelectedArtifact(translation);
            });
            this.changeDetectorRef.detectChanges();
        });
  }

  loadMaps(edition: string) {
    this.loadingMaps = true;
    this.simplexService.getSimpleMaps(edition)
        .pipe(takeUntil(this.cancelOngoingRequests$))
        .subscribe((maps) => {
            this.maps = maps;
            this.loadingMaps = false;
            if (!this.loadingSubsets && !this.loadingTranslations) {
              this.updatingEdition = false;
            }
            this.maps.forEach(map => {
              this.updateSelectedArtifact(map);
            });
            this.changeDetectorRef.detectChanges();
        });
  }

  ngOnDestroy() {
    this.cancelOngoingRequests$.next();
    this.cancelOngoingRequests$.complete();
    this.subscriptions.unsubscribe();
  }

  get formKeys(): string[] {
    return Object.keys(this.form.controls);
  }

  onClick(item: any, type: string) {
    item.type = type;
    this.selectedArtifact = item;
    this.changeDetectorRef.detectChanges();
  }
  submit() {
    this.form.markAllAsTouched();
    const type = this.form.value.type;
    if (this.form.valid) {
      let subset: any = {
        preferredTerm: this.form.value.preferredTerm
      };
      if (type == 'translation') {
        subset.languageCode = this.form.value.languageCode;
      }
      this.saving = true;
      // Set the form to disabled
      this.form.disable();
      switch (type) {
        case 'subset':
          lastValueFrom(this.simplexService.createSimpleRefset(this.edition, subset)).then(
            (edition) => {
              this.saving = false;
              this.form.reset();
              this.form.enable();
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
              this.form.enable();
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
              this.form.enable();
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
              this.form.enable();
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

  browseToConcept(artifact: any) {
    // Check if edition already has the necessary fields
    if (this.editionDetails?.branchPath && this.editionDetails?.languages && this.editionDetails?.defaultModule) {
      this.constructAndOpenBrowserUrl(artifact, this.editionDetails);
    } else {
      // Fetch the edition data if it doesn't exist
      lastValueFrom(this.simplexService.getEdition(this.edition)).then(
        (edition) => {
          this.editionDetails = edition;
          this.constructAndOpenBrowserUrl(artifact, edition);
        }
      );
    }
  }
  
  // Helper function to construct and open the browser URL
  private constructAndOpenBrowserUrl(artifact: any, edition: any) {
    const branch = edition.branchPath;
    let langs = Object.keys(edition.languages).join(',');
    let browserUrl = `/browser/?perspective=full&conceptId1=${artifact.conceptId}&edition=${branch}&release=&languages=${langs}&simplexFlagModuleId=${edition.defaultModule}`;
    
    if (artifact.type === 'subset' || artifact.type === 'map' || artifact.type === 'translation') {
      browserUrl += '&cd1focus=members';
    }
  
    const tab = window.open(browserUrl, 'simplex-browser');
    tab.focus();
  }
      
  
}