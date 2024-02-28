import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { any } from 'cypress/types/bluebird';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-validation-results',
  templateUrl: './validation-results.component.html',
  styleUrls: ['./validation-results.component.css']
})
export class ValidationResultsComponent implements OnInit, OnChanges {

  @Input() edition: string;
  
  issues: any;

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
    this.simplexService.getValidationResults(this.edition).subscribe(
      (data) => {
        this.issues = data;
      },
      (error) => {
        this.snackBar.open('Error getting validation results', 'Dismiss', {
          duration: 3000
        });
      }
    );
  }

}
