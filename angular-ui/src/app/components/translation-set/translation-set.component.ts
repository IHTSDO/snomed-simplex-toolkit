import { ChangeDetectorRef, Component } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom, Subscription } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

@Component({
  selector: 'app-translation-set',
  templateUrl: './translation-set.component.html',
  styleUrl: './translation-set.component.scss'
})
export class TranslationSetComponent {
  selectedEdition: any;
  private subscriptions: Subscription = new Subscription();
  loading = false;
  loadingSets = false;
  loadingLabelSetDetails = false;
  loadingLabelSetMembers = false;
  labelSets: any[] = [];
  selectedLabelSet: any;
  selectedLabelSetDetails: any;
  selectedLabelSetMembers: any[] = [];
  mode = 'view';
  

  constructor(  private simplexService: SimplexService,
                private snackBar: MatSnackBar,
                private uiConfigurationService: UiConfigurationService,
                private changeDetectorRef: ChangeDetectorRef) {}

  ngOnInit(): void {
    const editionSubscription = this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
      if (edition) {
        this.selectedEdition = edition;
        if (!edition.namespace) {
          this.refreshEdition();
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
          this.getLabelSets();
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

  getLabelSets() {
    this.loadingSets = true;
    this.simplexService.getLabelSets(this.selectedEdition.shortName).subscribe(
      (labelSets) => {
        this.labelSets = labelSets;
        this.loadingSets = false;
      },
      (error) => {
        console.error(error);
        this.snackBar.open('Failed to fetch label sets', 'Dismiss', {
          duration: 5000
        });
        this.loadingSets = false;
      }
    );
  }

  getLabelSetDetails(labelSet: any) {
    this.loadingLabelSetDetails = true;
    this.simplexService.getLabelSetDetails(this.selectedEdition.shortName, labelSet.id).subscribe(
      (labelSetDetails) => {
        this.selectedLabelSetDetails = labelSetDetails;
        this.loadingLabelSetDetails = false;
        this.changeDetectorRef.detectChanges();
      },
      (error) => {
        console.error(error);
        this.snackBar.open('Failed to fetch label set details', 'Dismiss', {
          duration: 5000
        });
        this.loadingLabelSetDetails = false;
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
    this.selectedLabelSetDetails = null;
    this.getLabelSetDetails(labelSet);
    this.getLabelSetMembers(labelSet);
  }

  setMode(mode: string) {
    this.mode = mode;
  }
}
