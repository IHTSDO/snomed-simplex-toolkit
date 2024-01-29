import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { Subscription, interval, lastValueFrom } from 'rxjs';

@Component({
  selector: 'app-manage-codesystem',
  templateUrl: './manage-codesystem.component.html',
  styleUrls: ['./manage-codesystem.component.scss']
})
export class ManageCodesystemComponent implements OnInit, OnDestroy, OnChanges {
  @Input() edition: any;
  private refreshSubscription: Subscription;


  releases: any[] = [
    { name: '20210131', date: '2021-01-31', type: 'Edition' },
    { name: '20210131', date: '2021-01-31', type: 'Extension' },
    { name: '20200731', date: '2020-07-31', type: 'Edition' },
    { name: '20200131', date: '2020-01-31', type: 'Edition' }
  ];

  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    // console.log('Classification status', this.edition.classificationStatus);
    // this.edition.classificationStatus = 'COMPLETE';
    // console.log('Classification status', this.edition.classificationStatus);
    this.refreshEdition();
    this.startRefresh();
  }

  ngOnDestroy(): void {
    this.stopRefresh();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['edition']) {
      this.refreshEdition();
    }
  }

  async runClassification() {
    const response = await lastValueFrom(
      this.simplexService.startClassification(this.edition.shortName)
    );
    this.alert('Classification requested');
    this.refreshEdition();
  }

  private alert(message: string) {
    this.snackBar.open(message, 'Dismiss', {
      duration: 5000
    });
  }

  exportDelta() {
    window.open('api/' + this.edition.shortName + '/rf2-export/delta');
  }

  exportSnapshot() {
    window.open('api/' + this.edition.shortName + '/rf2-export/snapshot');
  }

  startRefresh(): void {
    this.refreshSubscription = interval(15000).subscribe(() => {
      this.refreshEdition();
    });
  }

  stopRefresh(): void {
    if (this.refreshSubscription) {
      this.refreshSubscription.unsubscribe();
    }
  }

  async refreshEdition() {
    const response = await lastValueFrom(
      this.simplexService.getEdition(this.edition.shortName)
    );
    this.edition = response;
  }

}
