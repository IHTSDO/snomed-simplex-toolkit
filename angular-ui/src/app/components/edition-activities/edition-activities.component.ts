import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-edition-activities',
  templateUrl: './edition-activities.component.html',
  styleUrl: './edition-activities.component.css'
})
export class EditionActivitiesComponent implements OnInit, OnChanges {

  @Input() edition: any;
  activities: any;
  loading = false;
  skeleton: any[] = Array(2).fill({});

  displayedColumns: string[] = ['startDate', 'componentType', 'activityType', 'elapsed', 'icon'];

  constructor(private simplexService: SimplexService) { }

  ngOnInit() {
    this.loadActivities();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['edition'] && changes['edition'].currentValue) {
      // Check if the edition has changed
      if (changes['edition'].previousValue && changes['edition'].currentValue.shortName !== changes['edition'].previousValue.shortName) {
        this.loadActivities();
      }
    }
  }

  loadActivities() {
    this.loading = true;
    this.simplexService.getActivities(this.edition.shortName).subscribe(
      (data: any) => {
        this.loading = false;
        this.activities = data;
      },
      (error) => {
        this.loading = false;
        console.log('Error loading activities', error);
      }
    );
  }

}
