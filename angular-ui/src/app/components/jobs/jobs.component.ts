import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, ViewChild, ElementRef } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { SimplexService } from '../../services/simplex/simplex.service';
import { ModalService } from '../../services/modal/modal.service';
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

  @ViewChild('fileInput') fileInput: ElementRef;

  jobs: any[] = [];
  activities: any[] = [];
  skeleton: any[] = Array(2).fill({});
  loading = false;
  loadingActivities = false;
  saving = false;
  showMapInfo = false;
  hasInProgressJob = false;
  hasWeblateActivity = false;
  hasWeblateActivityFailed = false;

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
      viewValue: 'Snowlate Translation',
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
  private activitiesSubscription: Subscription;
  private intervalId?: any;
  private activitiesIntervalId?: any;
  private isPollingActivities = false;

  displayedColumns: string[] = ['date', 'display', 'status', 'total', 'icon'];

  constructor(
    private simplexService: SimplexService,
    private changeDetectorRef: ChangeDetectorRef,
    private snackBar: MatSnackBar,
    private modalService: ModalService,
    private router: Router
  ) {}

  ngOnInit() {
    if (this.refsetId == 'external') {
      this.displayedColumns = ['date', 'display', 'status', 'icon'];
    }
  }

  ngOnChanges() {
    // Reset activity flags immediately when component changes
    this.hasWeblateActivity = false;
    this.hasWeblateActivityFailed = false;
    
    this.loadJobs(true);
    this.loadActivities(true);
    this.selectedFileType = null;
    this.filterFileTypes();
  }

  ngOnDestroy() {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }

    if (this.activitiesSubscription) {
      this.activitiesSubscription.unsubscribe();
    }

    if (this.intervalId) {
      clearInterval(this.intervalId);
    }

    if (this.activitiesIntervalId) {
      clearInterval(this.activitiesIntervalId);
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
        console.log('Jobs loaded:', data); // Debug: see what jobs are returned
        this.jobs = data.slice(0, 10); // Increased limit to 10 jobs
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

  public loadActivities(clear: boolean) {
    if (clear) {
      this.activities = [];
      this.loadingActivities = true;
    }
    
    this.activitiesSubscription = this.simplexService
      .getActivities(this.edition)
      .pipe(
        catchError((error) => {
          console.error('An error occurred loading activities:', error);
          this.loadingActivities = false;
          return of([]);
        })
      )
      .subscribe((data) => {
        console.log('Activities loaded:', data); // Debug: see what activities are returned
        this.activities = data.items || data; // Handle both response format and direct array
        this.loadingActivities = false;
        this.changeDetectorRef.detectChanges();

        // Check for Translation Tool-related activities
        this.checkWeblateActivities();

        // Manage activities polling
        this.manageActivitiesPolling();
      });
  }

  private checkWeblateActivities() {
    // Check if there are any activities related to Translation Tool language initialization for this artifact
    this.hasWeblateActivity = this.activities.some((activity: any) => {
      // Check if activity is related to Translation Tool language initialization and this specific artifact
      // Activity is still running if it has no endDate
      return activity.activityType === 'WEBLATE_LANGUAGE_INITIALISATION' && 
             activity.componentType === 'TRANSLATION' &&
             activity.componentId === this.artifact?.conceptId &&
             !activity.endDate;
    });

    // Check for failed Translation Tool activities
    this.hasWeblateActivityFailed = this.activities.some((activity: any) => {
      return activity.activityType === 'WEBLATE_LANGUAGE_INITIALISATION' && 
             activity.componentType === 'TRANSLATION' &&
             activity.componentId === this.artifact?.conceptId &&
             activity.error === true;
    });


  }



  getWeblateActivityErrorMessage(): string {
    const failedActivity = this.activities.find((activity: any) => 
      activity.activityType === 'WEBLATE_LANGUAGE_INITIALISATION' && 
      activity.componentType === 'TRANSLATION' &&
      activity.componentId === this.artifact?.conceptId &&
      activity.error === true
    );
    
    if (failedActivity) {
      return 'Snowlate language initialization failed';
    }
    return 'Snowlate linking failed';
  }

  private manageActivitiesPolling() {
    const hasProcessingActivities = this.activities.some((activity: any) => 
      activity.activityType === 'WEBLATE_LANGUAGE_INITIALISATION' && 
      activity.componentType === 'TRANSLATION' &&
      activity.componentId === this.artifact?.conceptId &&
      !activity.endDate
    );
    
    const hasFailedActivities = this.activities.some((activity: any) => 
      activity.activityType === 'WEBLATE_LANGUAGE_INITIALISATION' && 
      activity.componentType === 'TRANSLATION' &&
      activity.componentId === this.artifact?.conceptId &&
      activity.error === true
    );
    
    if ((hasProcessingActivities || hasFailedActivities) && !this.isPollingActivities) {
      this.startActivitiesPolling();
    } else if (!hasProcessingActivities && !hasFailedActivities && this.isPollingActivities) {
      this.stopActivitiesPolling();
    }
  }

  private startActivitiesPolling() {
    this.isPollingActivities = true;
    this.activitiesIntervalId = setInterval(() => {
      this.loadActivities(false);
    }, 10000); // Poll every 10 seconds for activities
  }

  private stopActivitiesPolling() {
    this.isPollingActivities = false;
    if (this.activitiesIntervalId) {
      clearInterval(this.activitiesIntervalId);
      this.activitiesIntervalId = null;
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length) {
      this.selectedFile = input.files[0];
    } else {
      this.selectedFile = null;
    }
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
      this.alert('Uploading file...');
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
      } finally {
        this.selectedFile = null;
        if (this.fileInput) {
          this.fileInput.nativeElement.value = '';
        }
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
      // If translation is linked to Translation Tool, only show weblateTranslation option
      if (this.artifact.type === 'translation' && this.isWeblateLinked()) {
        this.filteredFileTypes = this.fileTypes.filter((type) =>
          type.value === 'weblateTranslation'
        );
        this.selectedFileType = 'weblateTranslation';
      } else {
        // Normal filtering for all other cases
        this.filteredFileTypes = this.fileTypes.filter((type) =>
          type.artifactTypes.includes(this.artifact.type)
        );
        if (this.filteredFileTypes.length === 1) {
          this.selectedFileType = this.filteredFileTypes[0].value;
        }
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

  linkToWeblate() {
    // Show confirmation dialog instead of directly linking
    this.modalService.open('weblate-confirmation-modal');
  }

  closeWeblateConfirmation() {
    this.modalService.close('weblate-confirmation-modal');
  }

  confirmLinkToWeblate() {
    this.modalService.close('weblate-confirmation-modal');
    
    if (this.artifact && this.artifact.type === 'translation' && this.edition) {
      // Prevent multiple requests
      this.saving = true;
      
      this.simplexService.linkTranslationToWeblate(this.edition, this.artifact.conceptId).subscribe(
        (response) => {
          console.log('Snowlate linking response:', response); // Debug: see the job response
          this.snackBar.open('Snowlate linking job created successfully', 'Dismiss', {
            duration: 5000,
          });
          // Load the new job to show it in the list
          this.loadJobs(false);
          // Load activities to show processing status
          this.loadActivities(false);
          // Emit job completion for parent component
          this.jobCompleted.emit(response);
          this.saving = false;
        },
        (error) => {
          console.error('Failed to link to Snowlate:', error);
          this.snackBar.open('Failed to link to Snowlate', 'Dismiss', {
            duration: 5000,
          });
          this.saving = false;
        }
      );
    }
  }

  isWeblateLinked(): boolean {
    return this.artifact && this.artifact.type === 'translation' && this.artifact.isWeblate;
  }

  hasActiveRefsetChangeJob(): boolean {
    return this.jobs.some(job => job.jobType === 'REFSET_CHANGE' && job.status === 'IN_PROGRESS');
  }

  shouldShowEditingOptions(): boolean {
    // Don't show editing options if there's an active REFSET_CHANGE job
    if (this.hasActiveRefsetChangeJob()) {
      return false;
    }
    return true;
  }

  shouldDisableWeblateLinking(): boolean {
    // Disable Translation Tool linking if there's a specific activity for this artifact
    return this.hasWeblateActivity || this.saving;
  }

  shouldDisableEditing(): boolean {
    // Disable all editing options if there's a Translation Tool language initialization activity for this specific language
    return this.hasWeblateActivity || this.hasActiveRefsetChangeJob();
  }

  navigateToTranslationDashboard(): void {
    if (this.edition) {
      this.router.navigate(['/translation-dashboard', this.edition]);
    } else {
      this.router.navigate(['/translation-dashboard']);
    }
  }

}
