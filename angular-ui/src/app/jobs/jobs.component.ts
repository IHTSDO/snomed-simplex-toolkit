import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from '../services/simplex/simplex.service';
import { Subscription, catchError } from 'rxjs';

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
  private subscription: Subscription;
  private intervalId?: any;  // Declare an intervalId property

  displayedColumns: string[] = ['date', 'display', 'status', 'total', 'icon'];

  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar) {}

  ngOnInit() {
    // load jobs every 5 seconds
    // setInterval(() => {
    //   this.loadJobs(false);
    // }, 5000);
  }

  ngOnChanges() {
    this.loadJobs(true);
  }

  loadJobs(clear: boolean) {
    if (clear) { 
      this.jobs = [];
      this.loading = true;
    } 
    this.subscription = this.simplexService.getJobs(this.edition, this.refsetId)
    .pipe(
      catchError(error => {
        console.error('An error occurred:', error);
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
        this.intervalId = setInterval(() => {
          this.loadJobs(false);
        }, 2000);
      }
      
      // If there's no in-progress job and an interval is currently set, clear the interval
      if (!hasInProgressJob && this.intervalId) {
        clearInterval(this.intervalId);
        this.intervalId = undefined;
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

}
