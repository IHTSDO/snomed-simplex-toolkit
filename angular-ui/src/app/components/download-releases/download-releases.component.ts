import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { Subscription } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

@Component({
  selector: 'app-download-releases',
  templateUrl: './download-releases.component.html',
  styleUrl: './download-releases.component.css'
})
export class DownloadReleasesComponent implements OnInit, OnDestroy {
  edition: any;

  versions: any[] = [];
  loading = false;
  skeleton: any[] = Array(4).fill({});
  displayedColumns: string[] = ['effectiveDate', 'releasePackage', 'action'];
  downloadReleaseDisabled = false;
  roles: string[] = [];
  addingToMlds: Record<number, boolean> = {};
  private subscriptions: Subscription = new Subscription();

  constructor(private simplexService: SimplexService, private uiConfigurationService: UiConfigurationService, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    const editionSubscription = this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
      if (edition && (edition.shortName !== this.edition?.shortName)) {
        this.edition = edition;
        this.loadVersions();
      }
    });
    this.subscriptions.add(editionSubscription);
    this.loadRoles();
  }

  loadRoles() {
    lastValueFrom(this.simplexService.getRoles()).then(
      (roles) => {
        this.roles = roles;
      },
      () => {
        this.roles = [];
      }
    );
  }

  isAdmin(): boolean {
    return this.roles.includes('ADMIN');
  }

  loadVersions() {
    this.loading = true;
    this.simplexService.getReleasePackages(this.edition.shortName).subscribe((versions) => {
      this.versions = versions.items;
      this.loading = false;
    });
  }

  downloadRelease(version: any) {
      this.downloadReleaseDisabled = true;
      const downloadUrl = `api/codesystems/${this.edition.shortName}/versions/${version.effectiveDate}/package`;
      const anchor = document.createElement('a');
      anchor.href = downloadUrl;
      anchor.style.display = 'none';
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);

      setTimeout(() => {
        this.downloadReleaseDisabled = false;
      }, 1000);
  }

  addToMlds(version: any) {
    if (!this.isAdmin() || this.addingToMlds[version.effectiveDate]) {
      return;
    }
    this.addingToMlds[version.effectiveDate] = true;
    lastValueFrom(this.simplexService.addReleaseToMlds(this.edition.shortName, version.effectiveDate)).then(
      () => {
        this.addingToMlds[version.effectiveDate] = false;
        this.snackBar.open('Release added to MLDS', 'Dismiss', { duration: 5000 });
      },
      (error) => {
        this.addingToMlds[version.effectiveDate] = false;
        const message = error?.error?.message || error?.message || 'Failed to add release to MLDS';
        this.snackBar.open(message, 'Dismiss', { duration: 8000 });
      }
    );
  }

  isAddingToMlds(version: any): boolean {
    return !!this.addingToMlds[version.effectiveDate];
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

}
