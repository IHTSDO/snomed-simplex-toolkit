import { Component, Output, EventEmitter } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-welcome',
  templateUrl: './welcome.component.html',
  styleUrls: ['./welcome.component.scss'] // Corrected `styleUrls` (was `styleUrl`)
})
export class WelcomeComponent {

  constructor(private router: Router) { }

  closeWelcome() {
    this.router.navigate(['artifacts']);
  }

}
