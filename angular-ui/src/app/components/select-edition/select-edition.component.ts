import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

@Component({
  selector: 'app-select-edition',
  templateUrl: './select-edition.component.html',
  styleUrls: ['./select-edition.component.scss']
})
export class SelectEditionComponent {

  editions = [];
  editionFields = ["name", "namespace", "defaultModule", "defaultModuleDisplay", "dependantVersionEffectiveTime", "shortName"];
  @Input() selectedEdition: any;
  newEditionMode= false;
  loading = false;
  deleting = false;

  @Output() editionSelected = new EventEmitter<any>();

  constructor(private simplexService: SimplexService,
              private snackBar: MatSnackBar,
              private uiConfigurationService: UiConfigurationService) {}

  ngOnInit() {
   this.loadEditions(); 
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
}