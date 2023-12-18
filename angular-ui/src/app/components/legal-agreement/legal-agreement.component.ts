import { Component, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-legal-agreement',
  templateUrl: './legal-agreement.component.html',
  styleUrls: ['./legal-agreement.component.scss']
})
export class LegalAgreementComponent {
  @Output() userResponse = new EventEmitter<boolean>();

  agree() {
    this.userResponse.emit(true);
  }

  disagree() {
    this.userResponse.emit(false);
  }

}
