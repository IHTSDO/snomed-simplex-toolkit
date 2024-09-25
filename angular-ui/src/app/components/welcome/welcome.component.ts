import { Component, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'app-welcome',
  templateUrl: './welcome.component.html',
  styleUrls: ['./welcome.component.scss'] // Corrected `styleUrls` (was `styleUrl`)
})
export class WelcomeComponent {

  @Output() closePanel: EventEmitter<boolean> = new EventEmitter<boolean>();

  constructor() { }

  closeWelcome() {
    this.closePanel.emit(false);  // Emit false when the welcome panel is closed
  }

}
