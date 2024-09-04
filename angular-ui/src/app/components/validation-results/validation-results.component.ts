import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { any } from 'cypress/types/bluebird';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-validation-results',
  templateUrl: './validation-results.component.html',
  styleUrls: ['./validation-results.component.css']
})
export class ValidationResultsComponent implements OnInit, OnChanges {

  @Input() edition: string;
  
  issues: any;
  loadingValidationResults: boolean = false;

  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar) {}
  
  ngOnChanges(changes: SimpleChanges) {
    if (changes['edition'] && changes['edition'].currentValue) {
      this.refreshIssues();
    }
  }

  ngOnInit(): void {
    this.refreshIssues();
  }

  public refreshIssues() {
    this.loadingValidationResults = true;
    this.simplexService.getValidationResults(this.edition).subscribe(
      (data) => {
        this.issues = data;
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

  browseToConcept(conceptId: string) {
    lastValueFrom(this.simplexService.getEdition(this.edition)).then(
      (edition) => {
        const branch = edition.branchPath;
        let langs = Object.keys(edition.languages).join(',');
        const tab = window.open(`/browser/?perspective=full&conceptId1=${conceptId}&edition=${branch}&release=&languages=${langs}&simplexFlagModuleId=${edition.defaultModule}`, 'simplex-browser');
        tab.focus();
      }
    )
  }    

}
