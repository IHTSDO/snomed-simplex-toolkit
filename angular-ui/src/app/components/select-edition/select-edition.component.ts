import { ChangeDetectorRef, Component, EventEmitter, Input, NgZone, OnChanges, OnInit, Output } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom, Subscription } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

@Component({
  selector: 'app-select-edition',
  templateUrl: './select-edition.component.html',
  styleUrls: ['./select-edition.component.scss']
})
export class SelectEditionComponent implements OnInit {

  editionFields = ["name", "namespace", "defaultModule", "defaultModuleDisplay", "shortName"];
  selectedEdition: any;
  newEditionMode= false;
  loading = false;
  deleting = false;
  roles: any[] = [];
  private subscriptions: Subscription = new Subscription();


  constructor(private simplexService: SimplexService,
              private snackBar: MatSnackBar,
              private uiConfigurationService: UiConfigurationService,
              private changeDetectorRef: ChangeDetectorRef
              ) {}

  ngOnInit(): void {
    const editionSubscription = this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
      if (edition) {
        this.selectedEdition = edition;
        if (!edition.namespace) {
          this.refreshEdition();
        }
        this.changeDetectorRef.detectChanges();
        this.getRoles();
      }
    });
    this.subscriptions.add(editionSubscription);
  }

  async refreshEdition() {
    this.loading = true;
    lastValueFrom(this.simplexService.getEdition(this.selectedEdition.shortName)).then(
      (edition) => {
        this.selectedEdition = edition;
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

  getRoles() {
    lastValueFrom(this.simplexService.getRoles()).then(
      (roles) => {
        this.roles = roles;
      },
      (error) => {
        console.error(error);
        this.loading = false;
        // Don't show error message if user is not authenticated (401 or 403)
        // This happens when users visit the app before logging in
        if (error.status !== 401 && error.status !== 403) {
          this.snackBar.open('Failed to load roles', 'Dismiss', {
            duration: 5000
          });
        }
      }
    );
  }

  isAdmin(): boolean {
    return this.roles.includes('ADMIN');
  }

  loadEditions() {
    this.loading = true;
    lastValueFrom(this.simplexService.getEditions()).then(
      (editions) => {
        // remove editions with empty name
        editions.items = editions.items.filter((item) => item.name); 
        this.uiConfigurationService.setSelectedEdition(editions.items[0]);
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

  toggleNewEditionMode() {
    this.newEditionMode = !this.newEditionMode;
  }

  deleteEdition(item: any) {
    this.deleting = true;
    lastValueFrom(this.simplexService.deleteEdition(item.shortName)).then(
      (result) => {
        this.deleting = false;
        this.selectedEdition = null;
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

}