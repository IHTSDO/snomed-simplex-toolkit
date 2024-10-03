import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-download-releases',
  templateUrl: './download-releases.component.html',
  styleUrl: './download-releases.component.css'
})
export class DownloadReleasesComponent implements OnInit, OnChanges {
  @Input() edition: any;

  versions: any[] = [];
  loading = false;
  skeleton: any[] = Array(4).fill({});
  displayedColumns: string[] = ['effectiveDate', 'releasePackage', 'action'];
  downloadReleaseDisabled = false;

  constructor(private simplexService: SimplexService, private snackBar: MatSnackBar) {}

  ngOnInit() {
    this.loadVersions();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['edition'] && changes['edition'].currentValue) {
      if (!changes['edition'].previousValue || changes['edition'].currentValue.shortName !== changes['edition'].previousValue.shortName) {
        this.loadVersions();
      }
    }
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
      this.snackBar.open(
        `Requesting Release Package. The download will start soon.`,
        'Dismiss',
        {
          duration: 5000,
        }
      );
      this.simplexService.getReleasePackage(this.edition.shortName, version.effectiveDate).subscribe(
        (fileBlob: Blob) => {
          const filename = this.edition.shortName + '-Edition-Release-Candidate.zip'; // Example filename
          this.simplexService.triggerDownload(fileBlob, filename);
          setTimeout(() => {
            this.downloadReleaseDisabled = false;
          }, 5000);
        },
        (error) => {
          console.error('Download failed:', error);
          this.snackBar.open(`Download failed`, 'Dismiss', {
            duration: 5000,
          });
        }
      );

  }

}
