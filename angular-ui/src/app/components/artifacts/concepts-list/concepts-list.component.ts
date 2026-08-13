import { Component, EventEmitter, Input, OnChanges, Output, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { lastValueFrom } from 'rxjs';
import { CustomConceptDialogComponent } from '../custom-concept-dialog/custom-concept-dialog.component';


@Component({
  selector: 'app-concepts-list',
  templateUrl: './concepts-list.component.html',
  styleUrls: ['./concepts-list.component.scss']
})
export class ConceptsListComponent implements OnChanges {

  @Input() edition: string;
  @Input() editionDetails: any;
  @Input() editable = false;

  @Output() conceptsChanged = new EventEmitter<void>();

  editionDetailsLocal: any;
  displayedColumns: string[] = ['conceptId', 'term', 'active', 'action'];
  concepts: any[] = [];
  loading = false;
  inactivatingConceptId: string | null = null;
  loadingData = [];
  offset = 0;
  limit = 25;

  @ViewChild(MatPaginator) paginator: MatPaginator;

  constructor(
    private simplexService: SimplexService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
  ) {
    this.initializeLoadingData();
  }

  initializeLoadingData() {
    this.loadingData = new Array(this.limit).fill({});
  }

  public loadConcepts() {
    this.loading = true;
    this.simplexService.getConcepts(this.edition, this.offset, this.limit)
      .subscribe({
        next: (response: any) => {
          this.concepts = response.items;
          if (this.paginator) {
            this.paginator.length = response.total;
          }
          this.loading = false;
        },
        error: () => {
          this.snackBar.open('Error loading concepts', 'Dismiss');
          this.loading = false;
        }
      });
  }

  refreshEdition() {
    if (this.editionDetails) {
      this.editionDetailsLocal = this.editionDetails;
      return;
    }
    lastValueFrom(this.simplexService.getEdition(this.edition)).then(
      (edition) => {
        this.editionDetailsLocal = edition;
      }
    );
  }

  ngOnChanges() {
    if (this.editionDetails) {
      this.editionDetailsLocal = this.editionDetails;
    }
    this.loadConcepts();
    this.refreshEdition();
  }

  onPageChange(event: PageEvent) {
    this.limit = event.pageSize;
    this.offset = event.pageIndex * event.pageSize;
    this.initializeLoadingData();
    this.loadConcepts();
  }

  openCreateDialog(): void {
    this.openConceptDialog();
  }

  openEditDialog(conceptId: string): void {
    this.openConceptDialog(conceptId);
  }

  private openConceptDialog(conceptId?: string): void {
    const dialogRef = this.dialog.open(CustomConceptDialogComponent, {
      width: '720px',
      maxWidth: '95vw',
      data: {
        edition: this.edition,
        editionDetails: this.editionDetailsLocal || this.editionDetails,
        conceptId
      }
    });
    dialogRef.afterClosed().subscribe((saved) => {
      if (saved) {
        this.loadConcepts();
        this.conceptsChanged.emit();
        this.snackBar.open(conceptId ? 'Concept updated.' : 'Concept created.', 'Dismiss', { duration: 4000 });
      }
    });
  }

  confirmInactivate(concept: any): void {
    if (!concept?.conceptId || !concept.active || this.inactivatingConceptId) {
      return;
    }
    const term = concept.fsn?.term || concept.pt?.term || concept.conceptId;
    if (!window.confirm(`Inactivate concept ${concept.conceptId} (${term})?`)) {
      return;
    }
    this.inactivatingConceptId = concept.conceptId;
    this.simplexService.updateCustomConcept(this.edition, concept.conceptId, {
      active: false,
      langRefsetTerms: {}
    }).subscribe({
      next: () => {
        this.inactivatingConceptId = null;
        this.loadConcepts();
        this.conceptsChanged.emit();
        this.snackBar.open('Concept inactivated.', 'Dismiss', { duration: 4000 });
      },
      error: (err) => {
        this.inactivatingConceptId = null;
        const message = err?.error?.message || err?.message || 'Failed to inactivate concept.';
        this.snackBar.open(message, 'Dismiss', { duration: 8000 });
      }
    });
  }

  isInactivating(conceptId: string): boolean {
    return this.inactivatingConceptId === conceptId;
  }

  browseToConcept(conceptId: string) {
    const edition = this.editionDetailsLocal || this.editionDetails;
    if (edition?.branchPath && edition?.languages && edition?.defaultModule) {
      this.constructAndOpenBrowserUrl(conceptId, edition);
    } else {
      lastValueFrom(this.simplexService.getEdition(this.edition)).then(
        (loadedEdition) => {
          this.editionDetailsLocal = loadedEdition;
          this.constructAndOpenBrowserUrl(conceptId, loadedEdition);
        }
      );
    }
  }

  private constructAndOpenBrowserUrl(conceptId: string, edition: any) {
    const branch = edition.branchPath;
    const langs = Object.keys(edition.languages).join(',');
    const browserUrl = `/browser/?perspective=full&conceptId1=${conceptId}&edition=${branch}&release=&languages=${langs}&simplexFlagModuleId=${edition.defaultModule}`;
    const tab = window.open(browserUrl, 'simplex-browser');
    tab?.focus();
  }

}
