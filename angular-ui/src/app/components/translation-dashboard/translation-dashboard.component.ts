import { ChangeDetectorRef, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom, Subscription } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';
import { TerminologyService } from 'src/app/services/simplex/terminology.service';

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
  loadingLabelSetDetails = false;
  labelSets: any[] = [];
  translations: any[] = [];
  selectedTranslation: any;
  selectedLabelSet: any;
  selectedLabelSetMembers: any[] = [];
  mode = 'view';
  saving = false;
  deleting = false;
  private pollingInterval: any;
  private isPolling = false;
  private currentLabelSetRequestId = 0; // Track the most recent request
  
  // ECL input method properties
  eclInputMethod: 'manual' | 'refset' | 'derivative' | 'subtype' = 'manual';
  selectedRefsetCode: string = '';
  selectedDerivativeCode: string = '';
  selectedSubtype: any = null;
  
  // Dynamic refsets loaded from server
  refsets: any[] = [];
  loadingRefsets = false;
  refsetsLoaded = false; // Cache flag for refsets
  
  // Dynamic derivatives loaded from server
  derivatives: any[] = [];
  loadingDerivatives = false;
  derivativesLoaded = false; // Cache flag for derivatives
  
  form: FormGroup = this.fb.group({
    translation: ['', Validators.required],
    name: ['', Validators.required],
    label: [{value: '', disabled: true}, Validators.required],
    ecl: ['', Validators.required]
  });

  constructor(  private fb: FormBuilder,
                private simplexService: SimplexService,
                private snackBar: MatSnackBar,
                private uiConfigurationService: UiConfigurationService,
                private changeDetectorRef: ChangeDetectorRef,
                private terminologyService: TerminologyService) {}

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

    // Set up automatic label computation from name field
    this.form.get('name')?.valueChanges.subscribe(name => {
      if (name) {
        const computedLabel = this.computeLabelFromName(name);
        this.form.patchValue({ label: computedLabel }, { emitEvent: false });
      }
    });
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.subscriptions.unsubscribe();
  }

  openWeblateUrl(url: string) {
    if (url) {
      window.open(url, '_blank');
    }
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
      
      // Map form data to new API format
      const apiPayload = {
        name: formData.name,
        label: formData.label,
        ecl: formData.ecl,
        subsetType: this.determineSubsetType(),
        selectionCodesystem: this.determineSelectionCodesystem()
      };
      
      // Create the translation set using the new API format
      this.simplexService.createTranslationSet(
        this.selectedEdition.shortName,
        formData.translation, // This is now the translation ID
        apiPayload
      ).subscribe(
        () => {
          this.snackBar.open('Translation set is being created', 'Dismiss', {
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
          
          // Extract error message from API response
          let errorMessage = 'Failed to create translation set';
          if (error.error && error.error.message) {
            errorMessage = `${errorMessage}: ${error.error.message}`;
          }
          
          this.snackBar.open(errorMessage, 'Dismiss', {
            duration: 8000
          });
          this.saving = false;
        }
      );
    }
  }

  getTranslationSets() {
    this.loadingSets = true;
    
    this.simplexService.getAllTranslationSets(this.selectedEdition.shortName).subscribe(
      (allTranslationSets) => {
        // Filter out translation sets with status "DELETING" and join with translations data
        this.labelSets = allTranslationSets
          .filter((translationSet: any) => translationSet.status !== 'DELETING')
          .map((translationSet: any) => {
            const matchingTranslation = this.translations.find((translation: any) => translation.id === translationSet.refset);
            return {
              ...translationSet,
              translationId: translationSet.refset,
              translationName: matchingTranslation ? matchingTranslation.pt.term : 'Unknown Translation'
            };
          });
        this.loadingSets = false;
        
        // Check if any translation sets are processing and manage polling
        this.managePolling();
      },
      (error) => {
        console.error(error);
        this.snackBar.open('Failed to fetch translation sets', 'Dismiss', {
          duration: 5000
        });
        this.loadingSets = false;
        this.labelSets = [];
      }
    );
  }

  private managePolling() {
    const hasProcessingSets = this.labelSets.some((set: any) => set.status === 'PROCESSING');
    
    if (hasProcessingSets && !this.isPolling) {
      this.startPolling();
    } else if (!hasProcessingSets && this.isPolling) {
      this.stopPolling();
    }
  }

  private startPolling() {
    this.isPolling = true;
    this.pollingInterval = setInterval(() => {
      this.pollTranslationSets();
    }, 10000); // 10 seconds
  }

  private stopPolling() {
    this.isPolling = false;
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
      this.pollingInterval = null;
    }
  }

  private pollTranslationSets() {
    // Background reload without showing loading state
    this.simplexService.getAllTranslationSets(this.selectedEdition.shortName).subscribe(
      (allTranslationSets) => {
        // Filter out translation sets with status "DELETING" and join with translations data
        const updatedLabelSets = allTranslationSets
          .filter((translationSet: any) => translationSet.status !== 'DELETING')
          .map((translationSet: any) => {
            const matchingTranslation = this.translations.find((translation: any) => translation.id === translationSet.refset);
            return {
              ...translationSet,
              translationId: translationSet.refset,
              translationName: matchingTranslation ? matchingTranslation.pt.term : 'Unknown Translation'
            };
          });
        
        // Update the list without showing loading state
        this.labelSets = updatedLabelSets;
        
        // Silently update selectedLabelSet if it exists
        if (this.selectedLabelSet) {
          const updatedSelectedSet = updatedLabelSets.find((set: any) => set.id === this.selectedLabelSet.id);
          if (updatedSelectedSet) {
            this.selectedLabelSet = updatedSelectedSet;
          }
        }
        
        // Check if polling should continue
        this.managePolling();
      },
      (error) => {
        console.error('Background polling error:', error);
        // Don't show error to user for background polling
      }
    );
  }



  getLabelSetMembers(labelSet: any) {
    this.loadingLabelSetMembers = true;
    this.simplexService.getLabelSetMembers(this.selectedEdition.shortName, labelSet.refset, labelSet.label).subscribe(
      (labelSetMembers) => {
        this.selectedLabelSetMembers = labelSetMembers.results;
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
    this.loadLabelSetDetails(labelSet);
  }

  setMode(mode: string) {
    this.mode = mode;
    if (mode === 'create') {
      // Reset the form when entering create mode
      this.form.reset();
      // Reset ECL input method properties
      this.eclInputMethod = 'manual';
      this.selectedRefsetCode = '';
      this.selectedDerivativeCode = '';
      this.selectedSubtype = null; // Reset subtype
      // Reset cache flags to allow fresh data loading
      this.refsetsLoaded = false;
      this.derivativesLoaded = false;
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
        
        // Load translation sets after translations are loaded
        this.getTranslationSets();
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

  onEclInputMethodChange() {
    // Clear the ECL field and selected refset/derivative/subtype when switching input methods
    this.form.patchValue({ ecl: '' });
    this.selectedRefsetCode = '';
    this.selectedDerivativeCode = '';
    this.selectedSubtype = null; // Clear subtype on method change
    
    // Load refsets from server when refset option is selected (only if not already loaded)
    if (this.eclInputMethod === 'refset' && !this.refsetsLoaded) {
      this.loadRefsetsFromServer();
    }
    
    // Load derivatives from server when derivative option is selected (only if not already loaded)
    if (this.eclInputMethod === 'derivative' && !this.derivativesLoaded) {
      this.loadDerivativesFromServer();
    }
    
    // Validate branchPath is available when subtype option is selected
    if (this.eclInputMethod === 'subtype' && !this.selectedEdition?.branchPath) {
      this.snackBar.open('Branch path not available. Please ensure an edition is selected.', 'Dismiss', {
        duration: 5000
      });
      // Reset to manual mode if branchPath is not available
      this.eclInputMethod = 'manual';
    }
  }

  onRefsetSelectionChange(refsetCode: string) {
    // Set the selected refset code for the dropdown display
    this.selectedRefsetCode = refsetCode;
    
    // Find the selected refset to get its display name
    const selectedRefset = this.refsets.find(refset => refset.code === refsetCode);
    if (selectedRefset) {
      // Format ECL as: ^ refsetCode |refset display|
      const eclValue = `^ ${selectedRefset.code} |${selectedRefset.display}|`;
      this.form.patchValue({ ecl: eclValue });
    }
  }

  onDerivativeSelectionChange(derivativeCode: string) {
    // Set the selected derivative code for the dropdown display
    this.selectedDerivativeCode = derivativeCode;
    
    // Find the selected derivative to get its display name
    const selectedDerivative = this.derivatives.find(derivative => derivative.code === derivativeCode);
    if (selectedDerivative) {
      // Format ECL as: ^ derivativeCode |derivative display|
      const eclValue = `^ ${selectedDerivative.code} |${selectedDerivative.display}|`;
      this.form.patchValue({ ecl: eclValue });
    }
  }

  onSubtypeSelectionChange(subtype: any) {
    // Set the selected subtype
    this.selectedSubtype = subtype;
    
    if (subtype && subtype.code) {
      // Format ECL as: << subtypeCode |subtype display|
      const eclValue = `<< ${subtype.code} |${subtype.display}|`;
      this.form.patchValue({ ecl: eclValue });
    } else {
      // Clear ECL if no subtype selected
      this.form.patchValue({ ecl: '' });
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

  /**
   * Loads refsets array from a specific ECL with moduleId replacement
   * @param moduleId The module ID to replace in the ECL
   * @param fhirBase Optional FHIR base URL (defaults to /snowstorm/snomed-ct/fhir)
   * @param fhirUrl Optional FHIR URL parameter
   * @param offset Optional offset for pagination
   * @param count Optional count for pagination
   * @returns Observable with the refsets array
   */
  loadRefsetsArray(moduleId: string, fhirBase?: string, fhirUrl?: string, offset?: number, count?: number) {
    const baseEcl = `(< 446609009 |Simple type reference set (foundation metadata concept)| OR < 900000000000496009 |Simple map from SNOMED CT type reference set (foundation metadata concept)| OR < 1187636009 |Simple map to SNOMED CT type reference set (foundation metadata concept)| OR < 1193543008 |Simple map with correlation to SNOMED CT type reference set (foundation metadata concept)|) {{ C moduleId = ${moduleId} }}`;
    
    // Use the specified FHIR base or default to /snowstorm/snomed-ct/fhir
    const serverUrl = fhirBase || '/snowstorm/snomed-ct/fhir';
    
    return this.terminologyService.expandValueSetFromServer(serverUrl, fhirUrl, baseEcl, '', offset, count);
  }

  /**
   * Determines the subset type based on the ECL input method
   * @returns 'ECL', 'REFSET', or 'SUB_TYPE' (though SUB_TYPE is not yet implemented)
   */
  private determineSubsetType(): string {
    switch (this.eclInputMethod) {
      case 'manual':
        return 'ECL';
      case 'refset':
        return 'REFSET';
      case 'derivative':
        return 'REFSET'; // Derivatives are also reference sets
      case 'subtype':
        return 'SUB_TYPE';
      default:
        return 'ECL';
    }
  }

  /**
   * Determines the selection codesystem based on the ECL input method and selected items
   * @returns The codesystem identifier
   */
  private determineSelectionCodesystem(): string {
    switch (this.eclInputMethod) {
      case 'derivative':
        // For derivatives, use the hardcoded SNOMEDCT-DERIVATIVES
        return 'SNOMEDCT-DERIVATIVES';
      default:
        return this.selectedEdition?.shortName || 'http://snomed.info/sct';
    }
  }

  /**
   * Loads derivatives from the server using the fixed URL
   */
  loadDerivativesFromServer() {
    this.loadingDerivatives = true;
    
    this.terminologyService.getDerivativesFromServer().subscribe(
      (response) => {
        if (response?.referenceSets) {
          // Transform the response to match the expected derivatives format
          this.derivatives = Object.values(response.referenceSets).map((refset: any) => ({
            editionUri: 'http://snomed.info/sct/705115006', // Keep the same edition URI for consistency
            code: refset.conceptId,
            display: refset.pt?.term || refset.fsn?.term || `Derivative ${refset.conceptId}`,
            fsn: refset.fsn?.term,
            active: refset.active,
            definitionStatus: refset.definitionStatus,
            moduleId: refset.moduleId,
            effectiveTime: refset.effectiveTime,
            memberCount: response.memberCountsByReferenceSet?.[refset.conceptId] || 0
          }));
        } else {
          console.warn('No derivatives found in response:', response);
          this.derivatives = [];
        }
        this.loadingDerivatives = false;
        this.derivativesLoaded = true; // Mark as loaded for caching
      },
      (error) => {
        console.error('Error loading derivatives:', error);
        this.snackBar.open('Failed to load derivatives from server', 'Dismiss', {
          duration: 5000
        });
        this.loadingDerivatives = false;
        // Keep empty array as fallback
        this.derivatives = [];
      }
    );
  }

  /**
   * Loads refsets from the server using the selected edition's module ID
   */
  loadRefsetsFromServer() {
    if (!this.selectedEdition?.defaultModule) {
      console.warn('No default module available for loading refsets');
      return;
    }

    this.loadingRefsets = true;
    
    // Use the default module directly
    const moduleId = this.selectedEdition.defaultModule;
    console.log('Using defaultModule:', moduleId);
    
    // Use the branch path directly from the selected edition
    const branchPath = this.selectedEdition.branchPath;
    console.log('Using branchPath:', branchPath);
    
    this.terminologyService.getRefsetsFromNativeApi(branchPath, moduleId).subscribe(
      (response) => {
        if (response?.items && response.items.length > 0) {
          // Transform the response to match the expected refsets format
          this.refsets = response.items.map((item: any) => ({
            code: item.conceptId,
            display: item.pt?.term || item.fsn?.term || `Refset ${item.conceptId}`,
            fsn: item.fsn?.term,
            active: item.active,
            definitionStatus: item.definitionStatus,
            moduleId: item.moduleId,
            effectiveTime: item.effectiveTime
          }));
        } else {
          console.warn('No refsets found in response:', response);
          this.refsets = [];
        }
        this.loadingRefsets = false;
        this.refsetsLoaded = true; // Mark as loaded for caching
      },
      (error) => {
        console.error('Error loading refsets:', error);
        this.snackBar.open('Failed to load refsets from server', 'Dismiss', {
          duration: 5000
        });
        this.loadingRefsets = false;
        // Keep the hardcoded refsets as fallback
      }
    );
  }

  loadLabelSetDetails(labelSet: any) {
    if (!labelSet || !this.selectedEdition) {
      return;
    }

    // Increment request ID to track this as the most recent request
    const requestId = ++this.currentLabelSetRequestId;
    this.loadingLabelSetDetails = true;
    
    this.simplexService.getTranslationSetDetails(
      this.selectedEdition.shortName,
      labelSet.translationId,
      labelSet.label
    ).subscribe(
      (details) => {
        // Only update if this is still the most recent request
        if (requestId === this.currentLabelSetRequestId) {
          this.selectedLabelSet = { ...this.selectedLabelSet, ...details };
          this.loadingLabelSetDetails = false;
          this.changeDetectorRef.detectChanges();
        }
      },
      (error) => {
        console.error('Error loading label set details:', error);
        // Only update loading state if this is still the most recent request
        if (requestId === this.currentLabelSetRequestId) {
          this.loadingLabelSetDetails = false;
          this.changeDetectorRef.detectChanges();
        }
      }
    );
  }

  pullFromWeblate() {
    if (!this.selectedLabelSet) {
      this.snackBar.open('No translation set selected', 'Dismiss', {
        duration: 5000
      });
      return;
    }

    const label = this.selectedLabelSet.label;
    const translationId = this.selectedLabelSet.translationId;
    
    if (!label) {
      this.snackBar.open('No label found for this translation set', 'Dismiss', {
        duration: 5000
      });
      return;
    }

    // Show confirmation dialog
    if (confirm(`Are you sure you want to pull content from Weblate for "${this.selectedLabelSet.name}"?`)) {
      this.simplexService.pullFromWeblate(
        this.selectedEdition.shortName,
        translationId,
        label
      ).subscribe(
        () => {
          this.snackBar.open('Task is scheduled', 'Dismiss', {
            duration: 5000
          });
          // Reload the translation set to show updated data
          this.getLabelSetMembers(this.selectedLabelSet);
        },
        (error) => {
          console.error(error);
          this.snackBar.open('Failed to pull content from Weblate', 'Dismiss', {
            duration: 5000
          });
        }
      );
    }
  }
}
