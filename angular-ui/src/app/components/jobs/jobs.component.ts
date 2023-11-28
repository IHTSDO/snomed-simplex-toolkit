import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from '../../services/simplex/simplex.service';
import { Subscription, catchError, lastValueFrom } from 'rxjs';

@Component({
  selector: 'app-jobs',
  templateUrl: './jobs.component.html',
  styleUrls: ['./jobs.component.scss']
})
export class JobsComponent implements OnChanges, OnInit {

  @Input() edition: string;
  @Input() refsetId: string;
  @Input() artifact: any;

  jobs: any[] = [];
  loading = false;

  selectedFile: File = null;
  selectedFileType = null;
  fileTypes = [
    {value: 'refsetSpreadsheet', viewValue: 'Spreadsheet Subset', artifactTypes: ['subset']},
    {value: 'refsetToolSubset', viewValue: 'Refset Tool Subset', artifactTypes: ['subset']},
    // {value: 'mapSpreadsheet', viewValue: 'Spreadsheet Map', artifactTypes: ['map']},
    {value: 'mapSpreadsheet', viewValue: 'Snap2SNOMED Map', artifactTypes: ['map']},
    {value: 'refsetToolTranslation', viewValue: 'Refset Tool Translation', artifactTypes: ['translation']},
    {value: 'weblateTranslation', viewValue: 'Weblate Translation', artifactTypes: ['translation']},
    {value: 'conceptsSpreadsheet', viewValue: 'Concepts Sppreadsheet', artifactTypes: ['concepts']}
  ];
  selectedLanguageCode: '';

  private subscription: Subscription;
  private intervalId?: any;  // Declare an intervalId property

  displayedColumns: string[] = ['date', 'display', 'status', 'total', 'icon'];

  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar) {}

  ngOnInit() {
  }

  ngOnChanges() {
    this.loadJobs(true);
    if (this.filteredFileTypes().length > 0) {
      this.selectedFileType = this.filteredFileTypes()[0].value;
    }
  }

  loadJobs(clear: boolean) {
    if (clear) { 
      this.jobs = [];
      this.selectedFile = null;
      this.loading = true;
    } 
    this.subscription = this.simplexService.getJobs(this.edition, this.refsetId)
    .pipe(
      catchError(error => {
        console.error('An error occurred:', error);
        this.snackBar.open('Error loading jobs', 'Dismiss', {
          duration: 5000
        });
        this.loading = false;
        return []; 
      })
    )
    .subscribe(data => {
      this.jobs = data.slice(0, 3);
      this.loading = false;
      
      const hasInProgressJob = this.jobs.some(job => job.status === 'IN_PROGRESS');
      
      // If there's an in-progress job and no interval is currently set, set up the interval
      if (hasInProgressJob && !this.intervalId) {
        clearInterval(this.intervalId);
        this.intervalId = setInterval(() => {
          this.loadJobs(false);
        }, 2000);
      }
      
      // If there's no in-progress job and an interval is currently set, clear the interval
      if (!hasInProgressJob && this.intervalId) {
        clearInterval(this.intervalId);
        this.intervalId = setInterval(() => {
          this.loadJobs(false);
        }, 10000);
      }
    });
  }

  ngOnDestroy() {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }

    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length) {
      this.selectedFile = input.files[0];
    }
  }

  downloadConceptsSpreadsheet() {
    this.simplexService.downloadConceptsSpreadsheet(this.edition).subscribe(
      (fileBlob: Blob) => {
        const filename = 'conceptsSpreadsheet.xlsx'; // Example filename
        this.simplexService.triggerDownload(fileBlob, filename);
      },
      error => {
        console.error('Download failed:', error);
      }
    );
  }
  
  async uploadFile(refsetId: string, componentType: string, fileType: string): Promise<void> {
    if (this.selectedFile && this.edition && componentType && fileType) {
        try {
            if (componentType === 'translation' && fileType === 'weblateTranslation' && this.selectedLanguageCode) {
              const response = await lastValueFrom(
                  this.simplexService.uploadWeblateTranslation(this.edition, refsetId, this.selectedFile, this.selectedLanguageCode)
              );
              this.selectedFile = null;
              this.loadJobs(false);
              this.alert('File import job created');
            } else if (componentType === 'translation' && fileType === 'weblateTranslation' && !this.selectedLanguageCode) {
              this.alert('Error: Language code not specified');
            } else if (componentType === 'translation' && fileType === 'refsetToolTranslation') {
              const response = await lastValueFrom(
                  this.simplexService.uploadRefsetToolTranslation(this.edition, refsetId, this.selectedFile)
              );
              this.selectedFile = null;
              this.loadJobs(false);
              this.alert('File import job created');
            } else if (componentType === 'subset' && fileType === 'refsetSpreadsheet') {
              const response = await lastValueFrom(
                  this.simplexService.uploadSpreadsheetRefset(this.edition, refsetId, this.selectedFile)
              );
              this.selectedFile = null;
              this.loadJobs(false);
              this.alert('File import job created');
            } else if (componentType === 'subset' && fileType === 'refsetToolSubset') {
              const response = await lastValueFrom(
                  this.simplexService.uploadRefsetToolSubset(this.edition, refsetId, this.selectedFile)
              );
              this.selectedFile = null;
              this.loadJobs(false);
              this.alert('File import job created');
            } else if (componentType === 'map' && fileType === 'mapSpreadsheet') {
              const response = await lastValueFrom(
                  this.simplexService.uploadSpreadsheetMap(this.edition, refsetId, this.selectedFile)
              );
              this.selectedFile = null;
              this.loadJobs(false);
              this.alert('File import job created');
            } else if (componentType === 'concepts' && fileType === 'conceptsSpreadsheet') {
              const response = await lastValueFrom(
                  this.simplexService.uploadConceptsSpreadsheet(this.edition, this.selectedFile)
              );
              this.selectedFile = null;
              this.loadJobs(false);
              this.alert('File import job created');
            } else {
              console.error('File upload failed: Invalid componentType or fileType');
              this.alert('File upload failed: Invalid componentType or fileType');
            }
        } catch (error) {
            console.error('File upload failed:', error);
            this.alert('File upload failed:' + error.message);
        }
    }
  }

  private alert(message: string) {
    this.snackBar.open(message, 'Dismiss', {
      duration: 5000
    });
  }

  filteredFileTypes() {
    return this.fileTypes.filter(type => type.artifactTypes.includes(this.artifact.type));
  }

}
