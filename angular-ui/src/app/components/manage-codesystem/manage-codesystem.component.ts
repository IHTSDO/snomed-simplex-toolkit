import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { Subscription, interval, lastValueFrom } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import * as XLSX from 'xlsx';
import { saveAs } from 'file-saver';
import { JobsComponent } from '../jobs/jobs.component';

@Component({
  selector: 'app-manage-codesystem',
  templateUrl: './manage-codesystem.component.html',
  styleUrls: ['./manage-codesystem.component.scss']
})
export class ManageCodesystemComponent implements OnInit, OnDestroy, OnChanges {
  @Input() edition: any;

  @ViewChild(JobsComponent) jobComponent: JobsComponent;

  private refreshSubscription: Subscription;

  jobs: any[] = [];
  releases: any[] = [];
  activeStage: string;
  loadingReleaseStatus = false;
  issuesReport: any;

  releaseStages = [
    { name: 'Authoring', completed: false, active: false, actionsAvailable: ['Content cut-off'] },
    { name: 'Classification', completed: false, active: false, actionsAvailable: ['Classify'] },
    { name: 'Validation', completed: false, active: false, actionsAvailable: ['Validate', 'Review in browser', 'Download alpha release', 'Upload content fixes', 'Mark release as done'] },
    { name: 'Release ready', completed: false, active: false, actionsAvailable: ['Download release', 'Prepare for new authoring cycle']}
  ];

  humanReadableIssueAdvice: string[] = [
    'Issues found in the following descriptions. Please update the descriptions to resolve the issues.',
  ];
  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar, private http: HttpClient) {}

  ngOnInit(): void {
    this.startRefresh();
  }

  ngOnDestroy(): void {
    this.stopRefresh();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['edition']) {
      this.activeStage = null;
      this.issuesReport = null;
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
    this.jobComponent.loadJobs(true);
  }

  async runValidation() {
    this.edition.validationStatus = 'IN_PROGRESS';
    const response = await lastValueFrom(
      this.simplexService.startValidation(this.edition.shortName)
    );
    this.alert('Validation requested');
    this.refreshEdition();
    this.jobComponent.loadJobs(true);
  }

  async runClassificationAndValidation() {
    this.edition.classificationStatus = 'IN_PROGRESS';
    this.edition.validationStatus = 'IN_PROGRESS';
    const classificationResponse = await lastValueFrom(
      this.simplexService.startClassification(this.edition.shortName)
    );
    const validationResponse = await lastValueFrom(
      this.simplexService.startValidation(this.edition.shortName)
    );
    this.alert('Classification and validation requested');
    this.refreshEdition();
    this.jobComponent.loadJobs(true);
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
    this.activeStage = null;
    this.alert('Closing authoring cycle and starting release preparation');
    this.simplexService.startReleasePreparation(this.edition.shortName).subscribe(
      (response: any) => {
        this.alert('Release preparation started');
        this.refreshEdition();
      },
      error => {
        console.error('Starting release preparation failed:', error);
        this.alert('Starting release preparation failed');
        this.refreshEdition();
      }
    );
  }

  stopReleasePreparation() {
    this.activeStage = null;
    this.alert('Stopping release preparation');
    this.simplexService.stopReleasePreparation(this.edition.shortName).subscribe(
      (response: any) => {
        this.alert('Release preparation stopped');
        this.refreshEdition();
      },
      error => {
        console.error('Stopping release preparation failed:', error);
        this.alert('Stopping release preparation failed');
        this.refreshEdition();
      }
    );
  }

  addContentApproval() {
    this.activeStage = null;
    this.alert('Approving edition content');
    this.simplexService.addContentApproval(this.edition.shortName).subscribe(
      (response: any) => {
        this.alert('Content approved');
        this.refreshEdition();
      },
      error => {
        console.error('Approving edition content failed:', error);
        this.alert('Approving edition content failed');
        this.refreshEdition();
      }
    );
  }

  removeContentApproval() {
    this.activeStage = null;
    this.alert('Removing content approval');
    this.simplexService.removeContentApproval(this.edition.shortName).subscribe(
      (response: any) => {
        this.alert('Content approval removed');
        this.refreshEdition();
      },
      error => {
        console.error('Removing content approval failed:', error);
        this.alert('Removing content approval failed');
        this.refreshEdition();
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
    this.refreshIssues();
    this.activeStage = await lastValueFrom(this.simplexService.getCodeSystemReleaseStatus(this.edition.shortName));
    this.updateByStage();
    this.loadingReleaseStatus = false;
  }

  async refreshIssues() {
    if (this.edition.validationStatus != 'TODO' && this.edition.validationStatus != 'IN_PROGRESS') {
      this.loadingReleaseStatus = true;
      const response = await lastValueFrom(
        this.simplexService.getValidationResults(this.edition.shortName)
      );
      response?.fixes.sort((a, b) => {
        const severityOrder = { 'ERROR': 1, 'WARNING': 2 };
        return severityOrder[a.severity] - severityOrder[b.severity];
      });
      this.issuesReport = response;
    }
  }

  updateByStage() {
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
  }


  getValidationStatusInfo(status: string): string {
    const statusMessages: { [key: string]: string } = {
      TODO: "Validation has not run yet",
      IN_PROGRESS: "Validation is currently running",
      CONTENT_ERROR: "There are content errors, please review and fix",
      CONTENT_WARNING: "There are content warnings, please review",
      SYSTEM_ERROR: "System error has occurred, please contact support",
      COMPLETE: "Validation has completed successfully without any issues",
      STALE: "The content has changed and the results are outdated",
    };
  
    return statusMessages[status] || "Unknown validation status";
  }

  getClassificationStatusInfo(status: string): string {
    const statusMessages: { [key: string]: string } = {
      TODO: "Needs to be run",
      IN_PROGRESS: "Classification is currently running",
      EQUIVALENT_CONCEPTS: "There are equivalent concepts. Please contact support",
      SYSTEM_ERROR: "System error occurred. Please contact support",
      COMPLETE: "Classification completed successfully without any issues"
    };
  
    return statusMessages[status] || "Unknown classification status";
  }

  generateChangeReport() {
    const branch = this.edition.branchPath;
    const reportSections: string[] = [
      'new-concepts', 
      'inactivated-concepts', 
      'reactivated-concepts', 
      'changed-fully-specified-names', 
      'inactivated-synonyms', 
      'new-synonyms-on-existing-concepts',
      'reactivated-synonyms',
      'new-refsets',
      'refsets-with-changed-members'
    ];

    // Create a workbook to accumulate sheets
    const workbook: XLSX.WorkBook = { Sheets: {}, SheetNames: [] };
    let requests: any[] = [];

    reportSections.forEach(section => {
      const url = `/snowstorm/snomed-ct/${branch}/authoring-stats/${section}`;
      
      // Store the request promise
      requests.push(this.http.get<any[]>(url).toPromise().then(data => {
        const worksheet: XLSX.WorkSheet = XLSX.utils.json_to_sheet(data);
        workbook.Sheets[section] = worksheet;
        workbook.SheetNames.push(section.substring(0, 31));
      }).catch(error => {
        console.error(`Error fetching data for section ${section}`, error);
      }));
    });

    // Wait for all requests to complete and then generate the Excel file
    Promise.all(requests).then(() => {
      const excelBuffer: any = XLSX.write(workbook, {
        bookType: 'xlsx',
        type: 'array'
      });

      this.saveAsExcelFile(excelBuffer, 'ChangeReport');
    }).catch(error => {
      console.error('Error processing report sections', error);
    });
  }

  private saveAsExcelFile(buffer: any, fileName: string): void {
    const data: Blob = new Blob([buffer], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8'
    });
    saveAs(data, `${fileName}.xlsx`);
  }

  browseToDailyBuild() {
    // console.log('Browsing to daily build for edition:', this.edition);
    const branch = this.edition.branchPath;
    let langs = Object.keys(this.edition.languages).join(',');
    let browserUrl = `/browser/?perspective=full&edition=${branch}&release=&languages=${langs}&simplexFlagModuleId=${this.edition.defaultModule}&dailyBuildFocus=true`;
    const tab = window.open(browserUrl, 'simplex-browser');
    tab.focus();
  } 
  

}
