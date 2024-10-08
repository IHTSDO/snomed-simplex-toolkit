import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { MatPaginator, PageEvent } from '@angular/material/paginator';

@Component({
  selector: 'app-edition-activities',
  templateUrl: './edition-activities.component.html',
  styleUrls: ['./edition-activities.component.css']
})
export class EditionActivitiesComponent implements OnInit, OnChanges {

  @Input() edition: any;
  activities: any[] = [];
  loading = false;
  skeleton: any[] = Array(2).fill({});

  displayedColumns: string[] = ['startDate', 'componentType', 'activityType', 'elapsed', 'icon'];

  @ViewChild(MatPaginator) paginator: MatPaginator;

  pageSize = 5;
  pageIndex = 0;
  totalItems = 0;

  constructor(private simplexService: SimplexService) { }

  ngOnInit() {
    this.loadActivities(true);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['edition'] && changes['edition'].currentValue) {
      if (!changes['edition'].previousValue || changes['edition'].currentValue.shortName !== changes['edition'].previousValue.shortName) {
        this.pageIndex = 0;
        this.loadActivities(true);
      }
    }
  }

  public loadActivities(clear: boolean) {
    if (clear) {
      this.loading = true;
    }
    const offset = this.pageIndex * this.pageSize;
    this.simplexService.getActivities(this.edition.shortName, offset, this.pageSize).subscribe(
      (data: any) => {
        this.loading = false;
        this.activities = data.items;
        this.totalItems = data.total;
      },
      (error) => {
        this.loading = false;
        console.error('Error loading activities', error);
      }
    );
  }

  onPageChange(event: PageEvent) {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadActivities(true);
  }

}
