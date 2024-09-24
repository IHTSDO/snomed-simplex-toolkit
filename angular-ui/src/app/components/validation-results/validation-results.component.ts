import {
  trigger,
  state,
  style,
  transition,
  animate,
} from '@angular/animations';
import {
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  signal,
  effect,
} from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { isEqual } from 'lodash';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-validation-results',
  templateUrl: './validation-results.component.html',
  styleUrls: ['./validation-results.component.css'],
  animations: [
    trigger('adviceAnimation', [
      state(
        'collapsed',
        style({
          height: '0',
          opacity: 0,
          overflow: 'hidden',
        })
      ),
      state(
        'expanded',
        style({
          height: '*',
          opacity: 1,
        })
      ),
      transition('collapsed <=> expanded', [animate('300ms ease-in-out')]),
    ]),
  ],
})
export class ValidationResultsComponent implements OnChanges {
  @Input() editionDetails: any;
  @Input() issues: any;

  // Internal Signals
  editionSignal = signal<string>(null);
  editionDetailsSignal = signal<any>(null);
  issuesSignal = signal<any>(null);

  localIssues = signal<any>(null);
  loadingValidationResults = signal<boolean>(false);
  adviceVisible = signal<boolean>(false);

  constructor(
    private simplexService: SimplexService,
    private snackBar: MatSnackBar
  ) {
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['editionDetails']) {
      this.editionDetailsSignal.set(changes['editionDetails'].currentValue);
    }

    if (changes['issues']) {
      if (
        !isEqual(
          changes['issues'].currentValue,
          changes['issues'].previousValue
        )
      ) {
        this.issuesSignal.set(changes['issues'].currentValue);
        this.localIssues.set(changes['issues'].currentValue);
      }
    }
  }

  refreshEdition() {
    const editionValue = this.editionSignal();
    if (editionValue) {
      this.simplexService.getEdition(editionValue).subscribe(
        (editionDetails) => {
          this.editionDetailsSignal.set(editionDetails);
          this.refreshIssues();
        },
        (error) => {
          this.snackBar.open('Error fetching edition details', 'Dismiss', {
            duration: 3000,
          });
        }
      );
    }
  }

  public refreshIssues() {
    const validationStatus = this.editionDetailsSignal()?.validationStatus;
    if (
      validationStatus !== 'TODO' &&
      validationStatus !== 'IN_PROGRESS'
    ) {
      this.loadingValidationResults.set(true);
      this.simplexService.getValidationResults(this.editionSignal()).subscribe(
        (data) => {
          data.fixes.sort((a, b) => {
            const severityOrder = { ERROR: 1, WARNING: 2 };
            return severityOrder[a.severity] - severityOrder[b.severity];
          });
          // fix issue report counts
          let errorCount = 0;
          let warningCount = 0;
          data.fixes.forEach(fix => {
            if (fix.severity === 'ERROR') {
              errorCount += fix.componentCount;
            } else if (fix.severity === 'WARNING') {
              warningCount += fix.componentCount;
            }
          });
          data.errorCount = errorCount;
          data.warningCount = warningCount;
          this.localIssues.set(data);
          this.loadingValidationResults.set(false);
        },
        (error) => {
          this.snackBar.open('Error getting validation results', 'Dismiss', {
            duration: 3000,
          });
          this.loadingValidationResults.set(false);
        }
      );
    }
  }

  browseToConcept(conceptId: string) {
    const editionDetails = this.editionDetailsSignal();
    if (
      editionDetails?.branchPath &&
      editionDetails?.languages &&
      editionDetails?.defaultModule
    ) {
      this.constructAndOpenBrowserUrl(conceptId, editionDetails);
    } else {
      // Fetch the edition data if it doesn't exist
      this.simplexService.getEdition(this.editionSignal()).subscribe(
        (edition) => {
          this.editionDetailsSignal.set(edition);
          this.constructAndOpenBrowserUrl(conceptId, edition);
        },
        (error) => {
          this.snackBar.open('Error fetching edition details', 'Dismiss', {
            duration: 3000,
          });
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
    tab?.focus();
  }

  toggleAdvice() {
    this.adviceVisible.update((visible) => !visible);
  }
}
