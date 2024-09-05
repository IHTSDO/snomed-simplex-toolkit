import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { Subscription, interval, lastValueFrom } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import * as XLSX from 'xlsx';
import { saveAs } from 'file-saver';

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
    { name: 'Release ready', completed: false, active: false, actionsAvailable: ['Download release', 'Prepare for new authoring cycle']}
  ];

  humanReadableIssueAdvice: string[] = [
    'Issues found in the following descriptions. Please update the descriptions to resolve the issues.',
  ];
  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar, private http: HttpClient) {}

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
        console.error('Starting release preparation failed:', error);
        this.alert('Starting release preparation failed');
      }
    );
  }

  stopReleasePreparation() {
    this.simplexService.stopReleasePreparation(this.edition.shortName).subscribe(
      (response: any) => {
        this.alert('Release preparation stopped');
        this.refreshEdition();
      },
      error => {
        console.error('Stopping release preparation failed:', error);
        this.alert('Stopping release preparation failed');
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

  getValidationStatusInfo(status: string): string {
    const statusMessages: { [key: string]: string } = {
      TODO: "Not run yet",
      IN_PROGRESS: "Validation is currently running",
      CONTENT_ERROR: "There are content errors. Please review and fix them.",
      CONTENT_WARNING: "There are content warnings. Consider reviewing them.",
      SYSTEM_ERROR: "System error occurred. Please contact support.",
      COMPLETE: "Validation completed successfully without any issues.",
      STALE: "Validation is outdated. The content has changed since the last validation.",
    };
  
    return statusMessages[status] || "Unknown validation status";
  }

  getClassificationStatusInfo(status: string): string {
    const statusMessages: { [key: string]: string } = {
      TODO: "Needs to be run",
      IN_PROGRESS: "Classification is currently running",
      EQUIVALENT_CONCEPTS: "There are equivalent concepts. Please contact support.",
      SYSTEM_ERROR: "System error occurred. Please contact support.",
      COMPLETE: "Classification completed successfully without any issues."
    };
  
    return statusMessages[status] || "Unknown classification status";
  }

  generateValidationReport() {
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

      this.saveAsExcelFile(excelBuffer, 'ValidationReport');
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
    lastValueFrom(this.simplexService.getEdition(this.edition)).then(
      (edition) => {
        const branch = edition.branchPath;
        let langs = Object.keys(edition.languages).join(',');
        let browserUrl = `/browser/?perspective=full&edition=${branch}&release=&languages=${langs}&simplexFlagModuleId=${edition.defaultModule}&dailyBuildFocus=true`;
        const tab = window.open(browserUrl, 'simplex-browser');
        tab.focus();
      }
    )
  } 
  

}
