import { Component, EventEmitter, Output } from '@angular/core';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-select-edition',
  templateUrl: './select-edition.component.html',
  styleUrls: ['./select-edition.component.scss']
})
export class SelectEditionComponent {

  editions = [];
  editionFields = ["name", "shortName", "defaultModule", "defaultModuleDisplay", "dependantVersionEffectiveTime", "branchPath", "workingBranchPath", "simplexWorkingBranch"];
  selectedEdition: any;
  newEditionMode= false;
  loading = false;

  @Output() editionSelected = new EventEmitter<any>();

  constructor(private simplexService: SimplexService) {}

  ngOnInit() {
    this.loading = true;
    this.simplexService.getEditions().subscribe((editions) => {
      this.editions = editions.items;
      this.loading = false;
    });
    // this.editions = [
    //   {
    //     id: 1,
    //     name: 'International Edition',
    //     description: 'This is the first edition'
    //   },
    //   {
    //     id: 2,
    //     name: 'Spanish Edition',
    //     description: 'This is the second edition'
    //   },
    //   {
    //     id: 3,
    //     name: 'Argentina Edition',
    //     description: 'This is the third edition'
    //   }
    // ]
  }

  onEditionClick(item: any) {
    this.selectedEdition = item;
    this.editionSelected.emit(item);
  }

  toggleNewEditionMode() {
    this.newEditionMode = !this.newEditionMode;
  }
}