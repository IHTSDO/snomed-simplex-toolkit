import { trigger, state, style, transition, animate } from '@angular/animations';
import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { isEqual } from 'lodash';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-validation-results',
  templateUrl: './validation-results.component.html',
  styleUrls: ['./validation-results.component.css'],
  animations: [
    trigger('adviceAnimation', [
      state('collapsed', style({
        height: '0',
        opacity: 0,
        overflow: 'hidden'
      })),
      state('expanded', style({
        height: '*',
        opacity: 1
      })),
      transition('collapsed <=> expanded', [
        animate('300ms ease-in-out')
      ])
    ])
  ]
})
export class ValidationResultsComponent implements OnInit, OnChanges {

  @Input() edition: string;
  @Input() editionDetails: any;
  
  @Input() issues: any;
  localIssues: any;
  loadingValidationResults: boolean = false;
  
  adviceVisible: boolean = false;

  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar) {}
  
  ngOnChanges(changes: SimpleChanges) {
    if (changes['edition'] && !changes['edition'].currentValue) {
      this.refreshEdition();
      if (!changes['issues'].currentValue) {
        this.refreshIssues();
      }
    }
    if (changes['issues'] && !isEqual(changes['issues'].currentValue, changes['issues'].previousValue)) {
      this.localIssues = changes['issues'].currentValue;
    }
  }

  ngOnInit(): void {
    // this.refreshIssues();
    // this.refreshEdition();
  }

  refreshEdition() {
    lastValueFrom(this.simplexService.getEdition(this.edition)).then(
      (edition) => {
        this.editionDetails = edition;
      }
    );
  }

  public refreshIssues() {
    if (this.editionDetails.validationStatus != 'TODO' && this.editionDetails.validationStatus != 'IN_PROGRESS') {
      this.loadingValidationResults = true;
      this.simplexService.getValidationResults(this.edition).subscribe(
        (data) => {
          data.fixes.sort((a, b) => {
            const severityOrder = { 'ERROR': 1, 'WARNING': 2 };
            return severityOrder[a.severity] - severityOrder[b.severity];
          });
          this.issues = data;
          this.localIssues = data;
          this.loadingValidationResults = false;
        },
        (error) => {
          this.snackBar.open('Error getting validation results', 'Dismiss', {
            duration: 3000
          });
          this.loadingValidationResults = false;
        }
      );
    }
  }

  browseToConcept(conceptId: string) {
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

  toggleAdvice() {
    this.adviceVisible = !this.adviceVisible;
  }

}
