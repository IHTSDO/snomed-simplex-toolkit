import { Component, Input, OnChanges, ViewChild } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { lastValueFrom } from 'rxjs';


@Component({
  selector: 'app-concepts-list',
  templateUrl: './concepts-list.component.html',
  styleUrls: ['./concepts-list.component.scss']
})
export class ConceptsListComponent implements OnChanges {

  @Input() edition: string;

  displayedColumns: string[] = ['conceptId', 'term', 'action'];
  concepts: any[] = [];
  loading = false;
  loadingData = [];
  offset = 0;
  limit = 5;
  
  @ViewChild(MatPaginator) paginator: MatPaginator;

  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar) {
      this.initializeLoadingData();
    }

  initializeLoadingData() {
    this.loadingData = new Array(this.limit).fill({}); // Create an array with 'limit' empty elements
  }

  loadConcepts() {
    this.loading = true;
    this.simplexService.getConcepts(this.edition, this.offset, this.limit)
      .subscribe({
        next: (response: any) => {
          this.concepts = response.items;
          this.paginator.length = response.total; 
          this.loading = false;
        },
        error: (error) => {
          this.snackBar.open('Error loading concepts', 'Dismiss');
          this.loading = false;
        }
      });
  }

  ngOnChanges() {
    this.loadConcepts();
  }

  onPageChange(event: PageEvent) {
    this.limit = event.pageSize;
    this.offset = event.pageIndex * event.pageSize;
    this.initializeLoadingData();
    this.loadConcepts();
  }

  browseToConcept(conceptId: string) {
    lastValueFrom(this.simplexService.getEdition(this.edition)).then(
      (edition) => {
        const branch = edition.branchPath;
        let langs = Object.keys(edition.languages).join(',');
        const url = `https://dev-simplex.ihtsdotools.org/browser/?perspective=full&conceptId1=${conceptId}&edition=${branch}&release=&languages=${langs}`;
        const tab = window.open(url, 'simplex-browser');
        tab.focus();
      }
    )
  } 

}
