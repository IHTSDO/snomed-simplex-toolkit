import { Component, Input } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { lastValueFrom } from 'rxjs';

@Component({
  selector: 'app-publish',
  templateUrl: './publish.component.html',
  styleUrls: ['./publish.component.scss']
})
export class PublishComponent {
  @Input() edition: string;

  releases: any[] = [
    { name: '20210131', date: '2021-01-31', type: 'Edition' },
    { name: '20210131', date: '2021-01-31', type: 'Extension' },
    { name: '20200731', date: '2020-07-31', type: 'Edition' },
    { name: '20200131', date: '2020-01-31', type: 'Edition' }
  ];

  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar) {}

  async runClassification() {
    const response = await lastValueFrom(
      this.simplexService.startClassification(this.edition)
    );
    this.alert('Classification requested');
  }

  private alert(message: string) {
    this.snackBar.open(message, 'Dismiss', {
      duration: 5000
    });
  }

  exportDelta() {
    window.open('api/' + this.edition + '/rf2-export/delta');
  }

  exportSnapshot() {
    window.open('api/' + this.edition + '/rf2-export/snapshot');
  }

}
