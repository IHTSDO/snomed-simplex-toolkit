import { Component, ViewChild } from '@angular/core';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { Router } from '@angular/router';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-admin-settings',
  templateUrl: './admin-settings.component.html',
  styleUrl: './admin-settings.component.scss'
})
export class AdminSettingsComponent {

  sharedSets: any = {};
  loadingSharedSets = false;
  selectedSet: any;
  setRecords: any[] = [];
  loadingSetRecords = false;
  loadingData = [];
  displayedColumns: string[] = ['source', 'target', 'context', 'developer_comments'];
  offset = 0;
  limit = 10;
    
  @ViewChild(MatPaginator) paginator: MatPaginator;

  constructor(private router: Router, private simplexService: SimplexService) { }

  ngOnInit() {
    this.loadSharedSets();
  }

  goHome() {
    this.router.navigate(['/home']);
  }

  goArtifacts() {
    this.router.navigate(['/artifacts']);
  }

  loadSharedSets() {
    this.loadingSharedSets = true;
    this.simplexService.getSharedSets().subscribe((data: any) => {
      this.sharedSets = data;
      this.loadingSharedSets = false;
    });
  }

  loadSelectedSetRecords() {
    this.loadingSetRecords = true;
    this.simplexService.getSharedSetRecords(this.selectedSet.slug, this.offset, this.limit).subscribe((data: any) => {
      this.setRecords = data.items;
      this.paginator.length = data.total; 
      this.loadingSetRecords = false;
    });
  }

  selectSharedSet(set) {
    this.selectedSet = set;
    this.loadSelectedSetRecords();
  }

  onPageChange(event: PageEvent) {
      this.limit = event.pageSize;
      this.offset = event.pageIndex * event.pageSize;
      this.loadSelectedSetRecords();
  }

}
