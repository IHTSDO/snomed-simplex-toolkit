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

  displayedColumns: string[] = ['conceptId', 'term', 'active', 'action'];
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

  public loadConcepts() {
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

  refreshEdition() {
    lastValueFrom(this.simplexService.getEdition(this.edition)).then(
      (edition) => {
        this.editionDetails = edition;
      }
    );
  }

  ngOnChanges() {
    this.loadConcepts();
    this.refreshEdition();
  }

  onPageChange(event: PageEvent) {
    this.limit = event.pageSize;
    this.offset = event.pageIndex * event.pageSize;
    this.initializeLoadingData();
    this.loadConcepts();
  }

  browseToConcept(conceptId: string) {
    // Check if edition already has the necessary fields
    if (this.editionDetails?.branchPath && this.editionDetails?.languages && this.editionDetails?.defaultModule) {
      this.constructAndOpenBrowserUrl(conceptId, this.editionDetails);
    } else {
      // Fetch the edition data if it doesn't exist
      lastValueFrom(this.simplexService.getEdition(this.edition)).then(
        (edition) => {
          this.editionDetails = edition;
          this.constructAndOpenBrowserUrl(conceptId, edition);
        }
      );
    }
  }
  
  // Helper function to construct and open the browser URL
  private constructAndOpenBrowserUrl(conceptId: string, edition: any) {
    const branch = edition.branchPath;
    let langs = Object.keys(edition.languages).join(',');
    let browserUrl = `/browser/?perspective=full&conceptId1=${conceptId}&edition=${branch}&release=&languages=${langs}&simplexFlagModuleId=${edition.defaultModule}`;
    const tab = window.open(browserUrl, 'simplex-browser');
    tab.focus();
  }

}
