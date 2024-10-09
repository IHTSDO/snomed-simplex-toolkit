import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from '../../services/simplex/simplex.service';
import { Subscription, catchError, lastValueFrom, of } from 'rxjs';
import { trigger, state, style, transition, animate } from '@angular/animations';
import { MatRadioChange } from '@angular/material/radio';
@Component({
  selector: 'app-jobs',
  templateUrl: './jobs.component.html',
  styleUrls: ['./jobs.component.scss'],
  animations: [
    trigger('expandCollapse', [
      state('true', style({ maxHeight: '500px' })),
      state('false', style({ maxHeight: '0px', opacity: 0 })),
      transition('false => true', [
        style({ opacity: 0 }),
        animate('300ms ease-in-out', style({ maxHeight: '500px', opacity: 1 })),
      ]),
      transition('true => false', [
        animate('300ms ease-in-out', style({ maxHeight: '0px', opacity: 0 })),
      ]),
    ]),
  ],
})
export class JobsComponent implements OnChanges, OnInit, OnDestroy {
  @Input() edition: string;
  @Input() refsetId: string;
  @Input() artifact: any;

  @Output() jobCompleted = new EventEmitter<any>();

  jobs: any[] = [];
  skeleton: any[] = Array(2).fill({});
  loading = false;
  showMapInfo = false;
  hasInProgressJob = false;

  selectedFile: File = null;
  selectedFileType: string = null;
  fileTypes = [
    {
      value: 'refsetSpreadsheet',
      viewValue: 'Spreadsheet Subset',
      artifactTypes: ['subset'],
    },
    {
      value: 'refsetToolSubset',
      viewValue: 'Refset Tool Subset',
      artifactTypes: ['subset'],
    },
    {
      value: 'mapSpreadsheet',
      viewValue: 'Snap2SNOMED Map',
      artifactTypes: ['map'],
    },
    {
      value: 'refsetToolTranslation',
      viewValue: 'Refset Tool Translation',
      artifactTypes: ['translation'],
    },
    {
      value: 'weblateTranslation',
      viewValue: 'Weblate Translation',
      artifactTypes: ['translation'],
    },
    {
      value: 'conceptsSpreadsheet',
      viewValue: 'Concepts Spreadsheet',
      artifactTypes: ['concepts'],
    },
  ];
  filteredFileTypes = [];

  downloadConceptFileDisabled = false;
  downloadRefsetFileDisabled = false;

  private subscription: Subscription;
  private intervalId?: any;

  displayedColumns: string[] = ['date', 'display', 'status', 'total', 'icon'];

  constructor(
    private simplexService: SimplexService,
    private changeDetectorRef: ChangeDetectorRef,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit() {
    if (this.refsetId == 'external') {
      this.displayedColumns = ['date', 'display', 'status', 'icon'];
    }
  }

  ngOnChanges() {
    this.loadJobs(true);
    this.selectedFileType = null;
    this.filterFileTypes();
  }

  ngOnDestroy() {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }

    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  public getJobs(): any[] {
    return this.jobs;
  }

  public loadJobs(clear: boolean) {
    if (clear) {
      this.jobs = [];
      this.selectedFile = null;
      this.loading = true;
    }
    this.subscription = this.simplexService
      .getJobs(this.edition, this.refsetId)
      .pipe(
        catchError((error) => {
          console.error('An error occurred:', error);
          this.snackBar.open('Error loading jobs', 'Dismiss', {
            duration: 15000,
          });
          this.loading = false;
          return of([]); // Return an empty observable array
        })
      )
      .subscribe((data) => {
        const previousInProgressJobs = this.jobs.filter(
          (job) => job.status === 'IN_PROGRESS'
        );
        this.jobs = data.slice(0, 5);
        this.loading = false;
        this.changeDetectorRef.detectChanges();

        // After updating jobs, check if any previously IN_PROGRESS jobs are now COMPLETED
        previousInProgressJobs.forEach((prevJob) => {
          const updatedJob = this.jobs.find((job) => job.id === prevJob.id);
          if (updatedJob && updatedJob.status === 'COMPLETE') {
            this.jobCompleted.emit(updatedJob); // Emit the job that was completed
          }
        });

        this.hasInProgressJob = this.jobs.some(
          (job) => job.status === 'IN_PROGRESS'
        );

        // If there's an in-progress job and no interval is currently set, set up the interval
        if (this.hasInProgressJob && !this.intervalId) {
          clearInterval(this.intervalId);
          this.intervalId = setInterval(() => {
            this.loadJobs(false);
          }, 2000);
        }

        // If there's no in-progress job and an interval is currently set, adjust the interval
        if (!this.hasInProgressJob && this.intervalId) {
          clearInterval(this.intervalId);
          this.intervalId = setInterval(() => {
            this.loadJobs(false);
          }, 10000);
        }
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length) {
      this.selectedFile = input.files[0];
    }
    event.target['value'] = '';
  }

  downloadConceptsSpreadsheet() {
    this.downloadConceptFileDisabled = true;
    this.snackBar.open(
      `Requesting spreadsheet. The download will start soon.`,
      'Dismiss',
      {
        duration: 5000,
      }
    );
    this.simplexService.downloadConceptsSpreadsheet(this.edition).subscribe(
      (fileBlob: Blob) => {
        const filename = 'conceptsSpreadsheet.xlsx'; // Example filename
        this.simplexService.triggerDownload(fileBlob, filename);
        setTimeout(() => {
          this.downloadConceptFileDisabled = false;
        }, 5000);
      },
      (error) => {
        console.error('Download failed:', error);
        this.snackBar.open(`Download failed`, 'Dismiss', {
          duration: 5000,
        });
      }
    );
  }

  downloadRefsetsSpreadsheet() {
    this.downloadRefsetFileDisabled = true;
    this.snackBar.open(
      `Requesting spreadsheet. The download will start soon.`,
      'Dismiss',
      {
        duration: 5000,
      }
    );
    this.simplexService
      .downloadRefsetSpreadsheet(this.edition, this.artifact.conceptId)
      .subscribe(
        (fileBlob: Blob) => {
          const filename = 'refsetSpreadsheet.xlsx'; // Example filename
          this.simplexService.triggerDownload(fileBlob, filename);
          setTimeout(() => {
            this.downloadRefsetFileDisabled = false;
          }, 5000);
        },
        (error) => {
          console.error('Download failed:', error);
          this.snackBar.open(`Download failed`, 'Dismiss', {
            duration: 5000,
          });
        }
      );
  }

  async uploadFile(
    refsetId: string,
    componentType: string,
    fileType: string
  ): Promise<void> {
    if (this.selectedFile && this.edition && componentType && fileType) {
      try {
        if (
          componentType === 'translation' &&
          fileType === 'weblateTranslation'
        ) {
          const response = await lastValueFrom(
            this.simplexService.uploadWeblateTranslation(
              this.edition,
              refsetId,
              this.selectedFile
            )
          );
          this.selectedFile = null;
          this.loadJobs(false);
          this.alert('File import job created');
        } else if (
          componentType === 'translation' &&
          fileType === 'refsetToolTranslation'
        ) {
          const response = await lastValueFrom(
            this.simplexService.uploadRefsetToolTranslation(
              this.edition,
              refsetId,
              this.selectedFile
            )
          );
          this.selectedFile = null;
          this.loadJobs(false);
          this.alert('File import job created');
        } else if (
          componentType === 'subset' &&
          fileType === 'refsetSpreadsheet'
        ) {
          const response = await lastValueFrom(
            this.simplexService.uploadSpreadsheetRefset(
              this.edition,
              refsetId,
              this.selectedFile
            )
          );
          this.selectedFile = null;
          this.loadJobs(false);
          this.alert('File import job created');
        } else if (
          componentType === 'subset' &&
          fileType === 'refsetToolSubset'
        ) {
          const response = await lastValueFrom(
            this.simplexService.uploadRefsetToolSubset(
              this.edition,
              refsetId,
              this.selectedFile
            )
          );
          this.selectedFile = null;
          this.loadJobs(false);
          this.alert('File import job created');
        } else if (componentType === 'map' && fileType === 'mapSpreadsheet') {
          const response = await lastValueFrom(
            this.simplexService.uploadSpreadsheetMap(
              this.edition,
              refsetId,
              this.selectedFile
            )
          );
          this.selectedFile = null;
          this.loadJobs(false);
          this.alert('File import job created');
        } else if (
          componentType === 'concepts' &&
          fileType === 'conceptsSpreadsheet'
        ) {
          const response = await lastValueFrom(
            this.simplexService.uploadConceptsSpreadsheet(
              this.edition,
              this.selectedFile
            )
          );
          this.selectedFile = null;
          this.loadJobs(false);
          this.alert('File import job created');
        } else {
          console.error(
            'File upload failed: Invalid componentType or fileType'
          );
        }
      } catch (error) {
        console.error('File upload failed:', error);
      }
    }
  }

  private alert(message: string) {
    this.snackBar.open(message, 'Dismiss', {
      duration: 5000,
    });
  }

  filterFileTypes() {
    if (this.artifact && this.artifact.type) {
      this.filteredFileTypes = this.fileTypes.filter((type) =>
        type.artifactTypes.includes(this.artifact.type)
      );
      if (this.filteredFileTypes.length === 1) {
        this.selectedFileType = this.filteredFileTypes[0].value;
      }
    }
  }

  toggleMapInfo() {
    this.showMapInfo = !this.showMapInfo;
  }

  // Handle changes in selected file type
  onSelectedFileTypeChange(event: MatRadioChange): void {
    this.selectedFileType = event.value;
    // Optionally reset related variables
    this.selectedFile = null;
  }

}
