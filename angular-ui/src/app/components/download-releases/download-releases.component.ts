import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
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
  private subscriptions: Subscription = new Subscription();

  constructor(private simplexService: SimplexService, private uiConfigurationService: UiConfigurationService, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    // Add the subscription to the subscriptions object
    const editionSubscription = this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
      if (edition && (edition.shortName !== this.edition?.shortName)) {
        this.edition = edition;
        this.loadVersions();
      }
    });

    // Add the subscription to the subscriptions container
    this.subscriptions.add(editionSubscription);
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
      // Use direct download link to allow Chrome's native download progress
      const downloadUrl = `/api/codesystems/${this.edition.shortName}/versions/${version.effectiveDate}/package`;
      const anchor = document.createElement('a');
      anchor.href = downloadUrl;
      anchor.style.display = 'none';
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      
      // Re-enable button after a short delay
      setTimeout(() => {
        this.downloadReleaseDisabled = false;
      }, 1000);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

}
