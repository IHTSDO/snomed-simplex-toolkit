import { ChangeDetectorRef, Component, EventEmitter, Input, Output } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { formatEffectiveTimeLabel } from 'src/app/utils/effective-time-format.util';

@Component({
  selector: 'app-upgrade-edition',
  templateUrl: './upgrade-edition.component.html',
  styleUrl: './upgrade-edition.component.scss'
})
export class UpgradeEditionComponent {

  @Input() edition: any;
  @Output() upgradeStarted = new EventEmitter<any>();

  availableUpgrades: any[] = [];
  loadingUpgrades = false;
  selectedUpgradeEdition: any;
  lastEditionShortName: string;
  upgradeRequested = false;

  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar,
    private changeDetectorRef: ChangeDetectorRef
    ) {}


  ngOnChanges() {
  // Refresh the selected edition when it changes
    if (this.edition && this.edition.editionStatus) {
      if (this.edition.shortName !== this.lastEditionShortName) {
        this.refreshAvailableUpgrades(this.edition);
        this.lastEditionShortName = this.edition.shortName;
      }
    }
  }

  formattedDependantVersion(): string | null {
    return formatEffectiveTimeLabel(this.edition?.dependantVersionEffectiveTime);
  }

  async refreshAvailableUpgrades(edition: any) {
    this.loadingUpgrades = true;
    try {
      const parentCodeSystemShortName = edition.dependantEditionShortName;
      if (!parentCodeSystemShortName) {
        this.availableUpgrades = [];
        return;
      }
      const versions = await lastValueFrom(
        this.simplexService.getCodeSystemVersions(parentCodeSystemShortName)
      );
      this.availableUpgrades = versions.items.filter(
        (version) => version.effectiveDate > edition.dependantVersionEffectiveTime
      );
    } catch (error) {
      console.error(error);
      this.snackBar.open('Failed to load available upgrades', 'Dismiss', {
        duration: 5000,
      });
    } finally {
      this.loadingUpgrades = false;
      // Manually trigger change detection
      this.changeDetectorRef.detectChanges();
    }
  }

  upgradeEdition() {
    this.snackBar.open('Requesting upgrade...', 'Dismiss', {
      duration: 5000
    });
    this.upgradeRequested = true;
    lastValueFrom(this.simplexService.upgradeEdition(this.edition.shortName, this.selectedUpgradeEdition.effectiveDate)).then(
      (result) => {
        this.selectedUpgradeEdition = null;
        this.upgradeRequested = false;
        this.availableUpgrades = [];
        this.edition.editionStatus = 'MAINTENANCE';
        this.upgradeStarted.emit();
        this.snackBar.open('Edition upgrade requested', 'Dismiss', {
          duration: 5000
        });
      },
      (error) => {
        console.error(error);
        this.selectedUpgradeEdition = null;
        this.upgradeRequested = false;
        this.availableUpgrades = [];
        this.snackBar.open('Failed to upgrade edition', 'Dismiss', {
          duration: 5000
        });
      }
    );
  }

  onUpgradeSelect(upgrade: any) {
    this.selectedUpgradeEdition = upgrade;
  }


}
