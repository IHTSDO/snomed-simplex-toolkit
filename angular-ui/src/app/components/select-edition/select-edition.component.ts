import { ChangeDetectorRef, Component, EventEmitter, Input, NgZone, OnChanges, OnInit, Output } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

@Component({
  selector: 'app-select-edition',
  templateUrl: './select-edition.component.html',
  styleUrls: ['./select-edition.component.scss']
})
export class SelectEditionComponent implements OnChanges {

  editions = [];
  editionFields = ["name", "namespace", "defaultModule", "defaultModuleDisplay", "shortName"];
  @Input() selectedEdition: any;
  newEditionMode= false;
  loading = false;
  deleting = false;
  roles: any[] = [];
  availableUpgrades: any[] = [];
  loadingUpgrades = false;
  parentEdition: any;
  selectedUpgradeEdition: any;

  @Output() editionSelected = new EventEmitter<any>();

  constructor(private simplexService: SimplexService,
              private snackBar: MatSnackBar,
              private uiConfigurationService: UiConfigurationService,
              private changeDetectorRef: ChangeDetectorRef
              ) {}


  ngOnChanges() {
    // Refresh the selected edition when it changes
    if (this.selectedEdition) {
      this.refresh();
    }
  }

  async refresh() {
    this.availableUpgrades = [];
    this.parentEdition = null;
    this.selectedUpgradeEdition = null;
    this.getRoles();
    this.refreshSelectedEdition();
  }

  async refreshSelectedEdition() {
    this.loading = true;
    lastValueFrom(this.simplexService.getEdition(this.selectedEdition.shortName)).then(
      (edition) => {
        this.selectedEdition = edition;
        this.refreshAvailableUpgrades(edition);
        this.loading = false;
      },
      (error) => {
        console.error(error);
        this.loading = false;
        this.snackBar.open('Failed to refresh edition', 'Dismiss', {
          duration: 5000
        });
        this.loading = false;
      }
    );
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

  getRoles() {
    lastValueFrom(this.simplexService.getRoles()).then(
      (roles) => {
        this.roles = roles;
      },
      (error) => {
        console.error(error);
        this.loading = false;
        this.snackBar.open('Failed to load roles', 'Dismiss', {
          duration: 5000
        });
      }
    );
  }

  isAdmin(): boolean {
    return this.roles.includes('ADMIN');
  }

  loadEditions() {
    this.editions = [];
    this.loading = true;
    lastValueFrom(this.simplexService.getEditions()).then(
      (editions) => {
        // remove editions with empty name
        editions.items = editions.items.filter((item) => item.name); 
        this.editions = editions.items;
        this.loading = false;
        if (this.editions.length > 0 && !this.selectedEdition) { this.onEditionClick(this.editions[0]) } 
      },
      (error) => {
        console.error(error);
        this.loading = false;
        this.snackBar.open('Failed to load editions', 'Dismiss', {
          duration: 5000
        });
      }
    );
  }

  onEditionClick(item: any) {
    this.selectedEdition = item;
    this.editionSelected.emit(item);
    this.uiConfigurationService.setSelectedEdition(item);
  }

  toggleNewEditionMode() {
    this.newEditionMode = !this.newEditionMode;
  }

  deleteEdition(item: any) {
    this.deleting = true;
    lastValueFrom(this.simplexService.deleteEdition(item.shortName)).then(
      (result) => {
        this.deleting = false;
        this.selectedEdition = null;
        this.editionSelected.emit(null);
        this.loadEditions();
        this.snackBar.open('Edition deleted!', 'Dismiss', {
          duration: 5000
        });
      },
      (error) => {
        console.error(error);
        this.deleting = false;
        this.snackBar.open('Failed to delete editions', 'Dismiss', {
          duration: 5000
        });
      }
    );
  }

  upgradeEdition() {
    this.loading = true;
    lastValueFrom(this.simplexService.upgradeEdition(this.selectedEdition.shortName, this.selectedUpgradeEdition.effectiveDate)).then(
      (result) => {
        this.loading = false;
        this.refresh();
        this.snackBar.open('Edition upgraded!', 'Dismiss', {
          duration: 5000
        });
      },
      (error) => {
        console.error(error);
        this.loading = false;
        this.snackBar.open('Failed to upgrade edition', 'Dismiss', {
          duration: 5000
        });
      }
    );
  }

  onSelectionChange(event: any) {
    this.selectedUpgradeEdition = event.value;
  }
}