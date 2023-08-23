import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-publish',
  templateUrl: './publish.component.html',
  styleUrls: ['./publish.component.scss']
})
export class PublishComponent {
  @Input() edition: string;

  exportDelta() {
    window.open('api/' + this.edition + '/rf2-export/delta');
  }
}
