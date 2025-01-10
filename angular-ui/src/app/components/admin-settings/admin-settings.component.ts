import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-admin-settings',
  templateUrl: './admin-settings.component.html',
  styleUrl: './admin-settings.component.css'
})
export class AdminSettingsComponent {

  constructor(private router: Router) { }

  goHome() {
    this.router.navigate(['/home']);
  }

  goArtifacts() {
    this.router.navigate(['/artifacts']);
  }

}
