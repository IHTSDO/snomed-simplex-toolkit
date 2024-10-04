import { ChangeDetectorRef, Component, EventEmitter, Input, Output } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

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
  parentEdition: any;
  selectedUpgradeEdition: any;
  lastEditionShortName: string;
  upgraderequested = false;

  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar,
    private uiConfigurationService: UiConfigurationService,
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

  async refreshAvailableUpgrades(edition: any) {
    this.loadingUpgrades = true;
    try {
      let workingBranchPath = edition.workingBranchPath;
      let parentBranch = workingBranchPath.substring(
        0,
        workingBranchPath.lastIndexOf('/')
      );
      const codeSystems = await lastValueFrom(
        this.simplexService.getCodeSystemForBranch(parentBranch)
      );
      this.parentEdition = codeSystems.items[0];
      let parentCodeSystemShortName = this.parentEdition.shortName;
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
    lastValueFrom(this.simplexService.upgradeEdition(this.edition.shortName, this.selectedUpgradeEdition.effectiveDate)).then(
      (result) => {
        this.edition.editionStatus = 'MAINTENANCE';
        this.upgradeStarted.emit();
        this.snackBar.open('Edition upgraded requested', 'Dismiss', {
          duration: 5000
        });
      },
      (error) => {
        console.error(error);
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
