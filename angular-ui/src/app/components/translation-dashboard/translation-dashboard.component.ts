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
      id: ['', Validators.required],
      name: ['', Validators.required],
      ecl: ['', Validators.required]
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
        }
        this.changeDetectorRef.detectChanges();
      }
    });
    this.subscriptions.add(editionSubscription);
  }

  async refreshEdition() {
      this.loading = true;
      lastValueFrom(this.simplexService.getEdition(this.selectedEdition.shortName)).then(
        (edition) => {
          this.selectedEdition = edition;
          this.getTranslations();
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

  submit() {
    console.log('Form submitted:', this.form.value);
    this.form.reset();
    this.mode = 'view';
  }

  getTranslationSets() {
    if (!this.selectedTranslation) {
      return;
    }
    
    this.loadingSets = true;
    this.simplexService.getTranslationSets(this.selectedEdition.shortName, this.selectedTranslation.id).subscribe(
      (translationSets) => {
        this.labelSets = translationSets;
        this.loadingSets = false;
      },
      (error) => {
        console.error(error);
        this.snackBar.open('Failed to fetch translation sets', 'Dismiss', {
          duration: 5000
        });
        this.loadingSets = false;
      }
    );
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
  }

  getTranslations() {
    this.loadingTranslations = true;
    this.simplexService.getTranslations(this.selectedEdition.shortName).subscribe(
      (translations) => {
        // Filter out translations with status "DELETING"
        this.translations = translations.filter(translation => translation.status !== 'DELETING');
        
        // Preselect the first translation if available
        if (this.translations && this.translations.length > 0) {
          this.selectedTranslation = this.translations[0];
          this.onTranslationChange();
        }
        this.loadingTranslations = false;
      },
      (error) => {
        console.error(error);
        this.snackBar.open('Failed to fetch translations', 'Dismiss', {
          duration: 5000
        });
        this.loadingTranslations = false;
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
    console.log('selectedTranslation:', this.selectedTranslation);
    console.log('selectedLabelSet:', this.selectedLabelSet);
    
    if (!this.selectedTranslation || !this.selectedLabelSet) {
      console.log('Missing required data for deletion');
      return;
    }

    const label = this.selectedLabelSet.label;
    console.log('Label from selectedLabelSet:', label);
    
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
        this.selectedTranslation.id,
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
