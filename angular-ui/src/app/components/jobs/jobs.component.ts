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
    {value: 'mapSpreadsheet', viewValue: 'Spreadsheet Map', artifactTypes: ['map']},
    {value: 'snap2snomedMap', viewValue: 'SNAP2SNOMED Map', artifactTypes: ['map']},
    {value: 'refsetToolTranslation', viewValue: 'Refset Tool Translation', artifactTypes: ['translation']},
    {value: 'weblateTranslation', viewValue: 'Weblate Translation', artifactTypes: ['translation']}
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
        }, 15000);
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
  
  async uploadFile(refsetId: string, componentType: string, fileType: string): Promise<void> {
    // TODO: validate params
    if (this.selectedFile && this.edition && componentType && fileType) {
        try {
            if (componentType === 'translation' && fileType === 'weblateTranslation') {
              const response = await lastValueFrom(
                  this.simplexService.uploadWeblateTranslation(this.edition, refsetId, this.selectedFile, this.selectedLanguageCode)
              );
              this.selectedFile = null;
              this.loadJobs(false);
            } else if (componentType === 'translation' && fileType === 'refsetToolTranslation') {
              const response = await lastValueFrom(
                  this.simplexService.uploadRefsetToolTranslation(this.edition, refsetId, this.selectedFile)
              );
              this.selectedFile = null;
              this.loadJobs(false);
            } else if (componentType === 'subset' && fileType === 'refsetSpreadsheet') {
              const response = await lastValueFrom(
                  this.simplexService.uploadSpreadsheetRefset(this.edition, refsetId, this.selectedFile)
              );
              this.selectedFile = null;
              this.loadJobs(false);
            } else if (componentType === 'map' && fileType === 'mapSpreadsheet') {
              const response = await lastValueFrom(
                  this.simplexService.uploadSpreadsheetMap(this.edition, refsetId, this.selectedFile)
              );
              this.selectedFile = null;
              this.loadJobs(false);
            } else {
              console.error('File upload failed: Invalid componentType or fileType');
              this.snackBar.open('File upload failed: Invalid componentType or fileType', 'Dismiss', {
                duration: 5000
              });
            }
        } catch (error) {
            console.error('File upload failed:', error);
            this.snackBar.open('File upload failed:' + error.message, 'Dismiss', {
              duration: 5000
            });
        }
    }
  }

  filteredFileTypes() {
    return this.fileTypes.filter(type => type.artifactTypes.includes(this.artifact.type));
  }

}
