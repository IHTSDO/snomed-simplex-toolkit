import { Component, EventEmitter, Output } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-select-edition',
  templateUrl: './select-edition.component.html',
  styleUrls: ['./select-edition.component.scss']
})
export class SelectEditionComponent {

  editions = [];
  editionFields = ["name", "shortName", "defaultModule", "defaultModuleDisplay", "dependantVersionEffectiveTime", "branchPath", "workingBranchPath", "simplexWorkingBranch", "namespace"];
  selectedEdition: any;
  newEditionMode= false;
  loading = false;
  deleting = false;

  @Output() editionSelected = new EventEmitter<any>();

  constructor(private simplexService: SimplexService,
              private snackBar: MatSnackBar) {}

  ngOnInit() {
   this.loadEditions(); 
  }

  loadEditions() {
    this.editions = [];
    this.loading = true;
    lastValueFrom(this.simplexService.getEditions()).then(
      (editions) => {
        this.editions = editions.items;
        this.loading = false;
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