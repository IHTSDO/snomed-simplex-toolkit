import { Component, Input } from '@angular/core';

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

  exportDelta() {
    window.open('api/' + this.edition + '/rf2-export/delta');
  }

  exportSnapshot() {
    window.open('api/' + this.edition + '/rf2-export/snapshot');
  }

}