import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { Subscription, interval, lastValueFrom } from 'rxjs';

@Component({
  selector: 'app-manage-codesystem',
  templateUrl: './manage-codesystem.component.html',
  styleUrls: ['./manage-codesystem.component.scss']
})
export class ManageCodesystemComponent implements OnInit, OnDestroy, OnChanges {
  @Input() edition: any;
  private refreshSubscription: Subscription;

  jobs: any[] = [];
  releases: any[] = [];
  activeStage: string;
  loadingReleaseStatus = false;

  releaseStages = [
    { name: 'Authoring', completed: false, active: false, actionsAvailable: ['Content cut-off'] },
    { name: 'Classification', completed: false, active: false, actionsAvailable: ['Classify'] },
    { name: 'Validation', completed: false, active: false, actionsAvailable: ['Validate', 'Review in browser', 'Download alpha release', 'Upload content fixes', 'Mark release as done'] },
    { name: 'Release preparation', completed: false, active: false, actionsAvailable: ['Download release', 'Prepare for new authoring cycle']}
  ];

  humanReadableIssueAdvice: string[] = [
    'Issues found in the following descriptions. Please update the descriptions to resolve the issues.',
  ];
  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.refreshEdition();
    this.startRefresh();
  }

  ngOnDestroy(): void {
    this.stopRefresh();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['edition']) {
      this,this.activeStage = null;
      this.refreshEdition();
    }
  }

  async runClassification() {
    this.edition.classificationStatus = 'IN_PROGRESS';
    const response = await lastValueFrom(
      this.simplexService.startClassification(this.edition.shortName)
    );
    this.alert('Classification requested');
    this.refreshEdition();
  }

  async runValidation() {
    this.edition.validationStatus = 'IN_PROGRESS';
    const response = await lastValueFrom(
      this.simplexService.startValidation(this.edition.shortName)
    );
    this.alert('Validation requested');
    this.refreshEdition();
  }

  downloadValidationResults() {
    window.open('api/codesystems/' + this.edition.shortName + '/validate/spreadsheet');
  }

  private alert(message: string) {
    this.snackBar.open(message, 'Dismiss', {
      duration: 5000
    });
  }

  exportDelta() {
    window.open('api/' + this.edition.shortName + '/rf2-export/delta');
  }

  exportSnapshot() {
    window.open('api/' + this.edition.shortName + '/rf2-export/snapshot');
  }

  startRefresh(): void {
    this.refreshSubscription = interval(15000).subscribe(() => {
      this.refreshEdition();
    });
  }

  stopRefresh(): void {
    if (this.refreshSubscription) {
      this.refreshSubscription.unsubscribe();
    }
  }

  startReleasePreparation() {
    this.simplexService.startReleasePreparation(this.edition.shortName).subscribe(
      (response: any) => {
        this.alert('Release preparation started');
        this.refreshEdition();
      },
      error => {
        console.error('Release preparation failed:', error);
        this.alert('Release preparation failed');
      }
    );
  }

  stopReleasePreparation() {
    this.simplexService.stopReleasePreparation(this.edition.shortName).subscribe(
      (response: any) => {
        this.alert('Release preparation started');
        this.refreshEdition();
      },
      error => {
        console.error('Release preparation failed:', error);
        this.alert('Release preparation failed');
      }
    );
  }

  async refreshJobs() {
    const response = await lastValueFrom(
      this.simplexService.getJobs(this.edition.shortName)
    );
    this.jobs = response;
  }

  async refreshEdition() {
    this.loadingReleaseStatus = true;
    const response = await lastValueFrom(
      this.simplexService.getEdition(this.edition.shortName)
    );
    this.edition = response;
    this.refreshJobs();
    this.activeStage = await lastValueFrom(this.simplexService.getCodeSystemReleaseStatus(this.edition.shortName));
    let found = false;
    for (const stage of this.releaseStages) {
      if (!found && stage.name!= this.activeStage) {
        stage.completed = true;
      }
      if (stage.name === this.activeStage) {
        stage.active = true;
        found = true;
      } else {
        stage.active = false;
      }
    }
    this.loadingReleaseStatus = false;
  }

}
