import { Component, Input, OnChanges, SimpleChanges, OnDestroy, OnInit, ViewChild, ChangeDetectorRef } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom, Subject, Subscription, Observable } from 'rxjs';
import { takeUntil, map, startWith, distinctUntilChanged } from 'rxjs/operators';
import {
  MAP_ARTIFACT_TYPE_LABELS,
  MapArtifactType,
  SimplexService
} from 'src/app/services/simplex/simplex.service';
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
  showUsEnglishSynonymsArtifact: boolean = false;
  showGbEnglishSynonymsArtifact: boolean = false;
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
  private languageCodeValueChangesSubscription?: Subscription;
  languageCodes: any[] = [];
  filteredLanguageCodes: Observable<any[]>;


  artifactTypes = ["subset", "map", "translation"];
  readonly mapArtifactTypes: MapArtifactType[] = ['correlation', 'fromSnomed', 'toSnomed'];
  readonly mapArtifactTypeLabels = MAP_ARTIFACT_TYPE_LABELS;
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
        const shortName = edition.shortName;
        if (this.edition !== shortName) {
          this.editionDetails = edition;
          this.edition = shortName;
          this.updatingEdition = true;
          this.loadArtifacts(shortName);
          this.selectedArtifact = null;
          this.newArtifactMode = false;
        } else {
          this.editionDetails = edition;
        }
      }
    });
    this.subscriptions.add(editionSubscription);
    this.simplexService.getLanguageCodes().subscribe(data => {
      this.languageCodes = data;
      this.setupLanguageCodeFilter();
    })
  }

  setupLanguageCodeFilter() {
    const languageCodeControl = this.form.get('languageCode');
    if (languageCodeControl) {
      this.filteredLanguageCodes = languageCodeControl.valueChanges.pipe(
        startWith(''),
        map(value => {
          const filterValue = typeof value === 'string' ? value.toLowerCase() : '';
          if (!filterValue) {
            return this.languageCodes;
          }
          return this.languageCodes.filter(lang => 
            lang.name.toLowerCase().includes(filterValue) || 
            lang.code.toLowerCase().includes(filterValue)
          );
        })
      );
      this.languageCodeValueChangesSubscription?.unsubscribe();
      this.languageCodeValueChangesSubscription = languageCodeControl.valueChanges.pipe(
        distinctUntilChanged()
      ).subscribe(code => this.autofillPreferredTermFromLanguageCode(code));
    }
  }

  autofillPreferredTermFromLanguageCode(code: string) {
    if (!code || !/^[a-z]{2}$/.test(code)) {
      return;
    }
    const language = this.languageCodes.find(lang => lang.code === code);
    if (!language) {
      return;
    }
    const preferredTermControl = this.form.get('preferredTerm');
    if (preferredTermControl) {
      preferredTermControl.setValue(`${language.name} language reference set`);
    }
  }

  toggleFormControls(typeValue: string) {
    if (typeValue === 'concepts' || typeValue === 'usEnglishSynonyms' || typeValue === 'gbEnglishSynonyms') {
        this.languageCodeValueChangesSubscription?.unsubscribe();
        this.languageCodeValueChangesSubscription = undefined;
        this.form.removeControl('preferredTerm');
        this.form.removeControl('languageCode');
        this.form.removeControl('mapType');
    } else if (typeValue == 'translation') {
        this.form.removeControl('mapType');
        if (!this.form.get('languageCode')) {
            const languageCodeControl = this.fb.control('', [Validators.required, this.languageCodeValidator]);
            this.form.addControl('languageCode', languageCodeControl);
        }
        if (!this.form.get('preferredTerm')) {
            this.form.addControl('preferredTerm', this.fb.control('', Validators.required));
        }
        this.setupLanguageCodeFilter();
    } else if (typeValue == 'map') {
        this.languageCodeValueChangesSubscription?.unsubscribe();
        this.languageCodeValueChangesSubscription = undefined;
        this.form.removeControl('languageCode');
        if (!this.form.get('mapType')) {
            this.form.addControl('mapType', this.fb.control('correlation', Validators.required));
        }
        if (!this.form.get('preferredTerm')) {
            this.form.addControl('preferredTerm', this.fb.control('', Validators.required));
        }
    } else {
        this.languageCodeValueChangesSubscription?.unsubscribe();
        this.languageCodeValueChangesSubscription = undefined;
        this.form.removeControl('languageCode');
        this.form.removeControl('mapType');
        if (!this.form.get('preferredTerm')) {
            this.form.addControl('preferredTerm', this.fb.control('', Validators.required));
        }
    }
  }

  displayLanguageCode(languageCode: any): string {
    if (!languageCode) {
      return '';
    }
    // If it's already a string (the code), find the full language object
    if (typeof languageCode === 'string') {
      const lang = this.languageCodes.find(l => l.code === languageCode);
      return lang ? `${lang.code} - ${lang.name}` : languageCode;
    }
    // If it's an object, display it
    return `${languageCode.code} - ${languageCode.name}`;
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
    this.simplexService.invalidateTranslationsCache(this.edition);
    this.loadArtifacts(this.edition);
  }

  onMapDeleted(): void {
    this.selectedArtifact = null;
    this.refreshArtifacts();
  }

  updateSelectedArtifact(artifact: any) {
    if (artifact.conceptId == this.selectedArtifact?.conceptId) {
      const mapEntry = this.maps.find((m) => m.conceptId === artifact.conceptId);
      const mapType = mapEntry?.mapType ?? artifact.mapType ?? this.selectedArtifact?.mapType;
      this.selectedArtifact = {
        ...artifact,
        type: this.selectedArtifact?.type ?? artifact.type,
        ...(mapType != null ? { mapType } : {}),
      };
      this.changeDetectorRef.detectChanges();
    }
  }

  loadConceptsArtifact(editionShortName: string) {
    lastValueFrom(this.simplexService.getEdition(editionShortName)).then(
      (edition) => {
        this.editionDetails = edition;
        this.uiService.setSelectedEdition(edition);
        this.showConceptsArtifact = edition?.showCustomConcepts;
        this.showUsEnglishSynonymsArtifact = edition?.showUsEnglishSynonyms;
        this.showGbEnglishSynonymsArtifact = edition?.showGbEnglishSynonyms;
        if (this.showConceptsArtifact) {
          lastValueFrom(this.simplexService.getConcepts(editionShortName,0,1)).then(
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
    this.languageCodeValueChangesSubscription?.unsubscribe();
    this.subscriptions.unsubscribe();
  }

  get formKeys(): string[] {
    if (this.form.get('languageCode')) {
      return ['type', 'languageCode', 'preferredTerm'];
    }
    if (this.form.get('mapType')) {
      return ['type', 'mapType', 'preferredTerm'];
    }
    if (this.form.get('preferredTerm')) {
      return ['type', 'preferredTerm'];
    }
    return Object.keys(this.form.controls);
  }

  mapTypeLabel(mapType: MapArtifactType | string | undefined): string {
    if (!mapType || !(mapType in MAP_ARTIFACT_TYPE_LABELS)) {
      return '';
    }
    return MAP_ARTIFACT_TYPE_LABELS[mapType as MapArtifactType];
  }

  selectedMapType(): MapArtifactType | undefined {
    if (this.selectedArtifact?.type !== 'map') {
      return undefined;
    }
    const fromList = this.maps.find((m) => m.conceptId === this.selectedArtifact?.conceptId);
    return (fromList?.mapType ?? this.selectedArtifact?.mapType) as MapArtifactType | undefined;
  }

  onClick(item: any, type: string) {
    let artifact = item;
    if (type === 'map') {
      artifact = this.maps.find((m) => m.conceptId === item.conceptId) ?? item;
    }
    artifact.type = type;
    if (type === 'map' && artifact.mapType == null && item.mapType != null) {
      artifact.mapType = item.mapType;
    }
    this.selectedArtifact = artifact;
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
      if (type == 'map') {
        subset.mapType = this.form.get('mapType')?.value;
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
          const createdMapType = subset.mapType as MapArtifactType;
          lastValueFrom(this.simplexService.createMap(this.edition, subset)).then(
            (created) => {
              this.saving = false;
              this.form.reset();
              this.form.enable();
              this.newArtifactMode = false;
              if (created?.conceptId) {
                this.selectedArtifact = { ...created, type: 'map', mapType: createdMapType };
              }
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
              this.form.enable();
              this.snackBar.open('Failed to enable custom concepts', 'Dismiss', {
                duration: 5000
              });
            }
          );
          break;
      case 'usEnglishSynonyms':
          lastValueFrom(this.simplexService.showUsEnglishSynonyms(this.edition)).then(
            () => {
              this.saving = false;
              this.form.reset();
              this.form.enable();
              this.newArtifactMode = false;
              this.loadArtifacts(this.edition);
            },
            (error) => {
              console.error(error);
              this.saving = false;
              this.form.enable();
              this.snackBar.open('Failed to enable US English synonyms', 'Dismiss', {
                duration: 5000
              });
            }
          );
          break;
      case 'gbEnglishSynonyms':
          lastValueFrom(this.simplexService.showGbEnglishSynonyms(this.edition)).then(
            () => {
              this.saving = false;
              this.form.reset();
              this.form.enable();
              this.newArtifactMode = false;
              this.loadArtifacts(this.edition);
            },
            (error) => {
              console.error(error);
              this.saving = false;
              this.form.enable();
              this.snackBar.open('Failed to enable GB English synonyms', 'Dismiss', {
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