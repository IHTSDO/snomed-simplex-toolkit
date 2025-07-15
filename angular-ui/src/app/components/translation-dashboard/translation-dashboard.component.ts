import { ChangeDetectorRef, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom, Subscription } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

@Component({
  selector: 'app-translation-dashboard',
  templateUrl: './translation-dashboard.component.html',
  styleUrl: './translation-dashboard.component.scss'
})
export class TranslationDashboardComponent {
  selectedEdition: any;
  private subscriptions: Subscription = new Subscription();
  loading = false;
  loadingSets = false;
  loadingLabelSetMembers = false;
  loadingTranslations = false;
  labelSets: any[] = [];
  translations: any[] = [];
  selectedTranslation: any;
  selectedLabelSet: any;
  selectedLabelSetMembers: any[] = [];
  mode = 'view';
  saving = false;
  deleting = false;
  
  form: FormGroup = this.fb.group({
    translation: ['', Validators.required],
    name: ['', Validators.required],
    label: [{value: '', disabled: true}, Validators.required],
    ecl: ['', Validators.required],
    branchPath: ['', Validators.required]
  });

  constructor(  private fb: FormBuilder,
                private simplexService: SimplexService,
                private snackBar: MatSnackBar,
                private uiConfigurationService: UiConfigurationService,
                private changeDetectorRef: ChangeDetectorRef) {}

  ngOnInit(): void {
    const editionSubscription = this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
      if (edition) {
        this.selectedEdition = edition;
        if (!edition.namespace) {
          this.refreshEdition();
        } else {
          this.getTranslations();
          this.getTranslationSets();
        }
        this.changeDetectorRef.detectChanges();
      }
    });
    this.subscriptions.add(editionSubscription);

    // Set up automatic label computation from name field
    this.form.get('name')?.valueChanges.subscribe(name => {
      if (name) {
        const computedLabel = this.computeLabelFromName(name);
        this.form.patchValue({ label: computedLabel }, { emitEvent: false });
      }
    });
  }

  async refreshEdition() {
      this.loading = true;
      lastValueFrom(this.simplexService.getEdition(this.selectedEdition.shortName)).then(
        (edition) => {
          this.selectedEdition = edition;
          this.getTranslations();
          this.getTranslationSets();
          this.loading = false;
        },
        (error) => {
          console.error(error);
          this.loading = false;
          this.snackBar.open('Failed to refresh edition', 'Dismiss', {
            duration: 5000
          });
          this.loading = false;
        }
      );
  }

  get formKeys(): string[] {
    return Object.keys(this.form.controls);
  }

  computeLabelFromName(name: string): string {
    return name
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '') // Remove diacritics (accented characters)
      .replace(/\s+/g, '-') // Replace whitespace with hyphens
      .replace(/[^a-z0-9-]/g, '') // Remove any other non-alphanumeric characters except hyphens
      .replace(/-+/g, '-') // Replace multiple consecutive hyphens with single hyphen
      .replace(/^-|-$/g, ''); // Remove leading and trailing hyphens
  }

  submit() {
    if (this.form.valid) {
      this.saving = true;
      const formData = this.form.value;
      
      // Include the disabled label field value in the form data
      formData.label = this.form.get('label')?.value;
      
      // Create the translation set using the form data
      this.simplexService.createTranslationSet(
        this.selectedEdition.shortName,
        formData.translation, // This is now the translation ID
        formData
      ).subscribe(
        () => {
          this.snackBar.open('Translation set created successfully', 'Dismiss', {
            duration: 5000
          });
          this.form.reset();
          this.mode = 'view';
          this.saving = false;
          
          // Reload the translation sets to show the new one
          this.getTranslationSets();
        },
        (error) => {
          console.error(error);
          this.snackBar.open('Failed to create translation set', 'Dismiss', {
            duration: 5000
          });
          this.saving = false;
        }
      );
    }
  }

  getTranslationSets() {
    this.loadingSets = true;
    // Load translation sets for all translations
    const allTranslationSets: any[] = [];
    let completedRequests = 0;
    
    if (this.translations.length === 0) {
      this.loadingSets = false;
      this.labelSets = [];
      return;
    }
    
    this.translations.forEach(translation => {
      this.simplexService.getTranslationSets(this.selectedEdition.shortName, translation.id).subscribe(
        (translationSets) => {
          // Add translation info to each set
          const setsWithTranslation = translationSets.map((set: any) => ({
            ...set,
            translationId: translation.id,
            translationName: translation.pt.term
          }));
          allTranslationSets.push(...setsWithTranslation);
          completedRequests++;
          
          if (completedRequests === this.translations.length) {
            this.labelSets = allTranslationSets;
            this.loadingSets = false;
          }
        },
        (error) => {
          console.error(error);
          completedRequests++;
          if (completedRequests === this.translations.length) {
            this.labelSets = allTranslationSets;
            this.loadingSets = false;
          }
        }
      );
    });
  }



  getLabelSetMembers(labelSet: any) {
    this.loadingLabelSetMembers = true;
    this.simplexService.getLabelSetMembers(this.selectedEdition.shortName, labelSet.id).subscribe(
      (labelSetMembers) => {
        this.selectedLabelSetMembers = labelSetMembers;
        this.loadingLabelSetMembers = false;
        this.changeDetectorRef.detectChanges();
      },
      (error) => {
        console.error(error);
        this.snackBar.open('Failed to fetch label set members', 'Dismiss', {
          duration: 5000
        });
        this.loadingLabelSetMembers = false;
      }
    );
  }

  selectSet(labelSet: any) {
    this.selectedLabelSet = labelSet;
    this.getLabelSetMembers(labelSet);
  }

  setMode(mode: string) {
    this.mode = mode;
    if (mode === 'create') {
      // Reset the form when entering create mode
      this.form.reset();
    }
  }

  getTranslations() {
    this.loadingTranslations = true;
    // Disable the translation form control while loading
    this.form.get('translation')?.disable();
    
    this.simplexService.getTranslations(this.selectedEdition.shortName).subscribe(
      (translations) => {
        // Filter out translations with status "DELETING"
        this.translations = translations.filter(translation => translation.status !== 'DELETING');
        this.loadingTranslations = false;
        // Re-enable the translation form control after loading
        this.form.get('translation')?.enable();
      },
      (error) => {
        console.error(error);
        this.snackBar.open('Failed to fetch translations', 'Dismiss', {
          duration: 5000
        });
        this.loadingTranslations = false;
        // Re-enable the translation form control after error
        this.form.get('translation')?.enable();
      }
    );
  }

  onTranslationChange() {
    if (this.selectedTranslation) {
      // Clear current selection
      this.selectedLabelSet = null;
      this.selectedLabelSetMembers = [];
      
      // Load translation sets for the selected translation
      this.getTranslationSets();
    }
  }

  deleteTranslationSet() {
    console.log('Delete button clicked');
    console.log('selectedLabelSet:', this.selectedLabelSet);
    
    if (!this.selectedLabelSet) {
      console.log('Missing required data for deletion');
      return;
    }

    const label = this.selectedLabelSet.label;
    const translationId = this.selectedLabelSet.translationId;
    console.log('Label from selectedLabelSet:', label);
    console.log('Translation ID from selectedLabelSet:', translationId);
    
    if (!label) {
      console.log('No label found in selectedLabelSet');
      this.snackBar.open('No label found for this translation set', 'Dismiss', {
        duration: 5000
      });
      return;
    }

    // Show confirmation dialog
    if (confirm(`Are you sure you want to delete the translation set "${this.selectedLabelSet.name}"?`)) {
      this.deleting = true;
      this.simplexService.deleteTranslationSet(
        this.selectedEdition.shortName,
        translationId,
        label
      ).subscribe(
        () => {
          this.snackBar.open('Translation set deleted successfully', 'Dismiss', {
            duration: 5000
          });
          // Clear selection and reload translation sets
          this.selectedLabelSet = null;
          this.selectedLabelSetMembers = [];
          this.getTranslationSets();
          this.deleting = false;
        },
        (error) => {
          console.error(error);
          this.snackBar.open('Failed to delete translation set', 'Dismiss', {
            duration: 5000
          });
          this.deleting = false;
        }
      );
    }
  }
}
