import { Component } from '@angular/core';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-maps',
  templateUrl: './maps.component.html',
  styleUrls: ['./maps.component.scss']
})
export class MapsComponent {
  maps = [];
  selectedMap = null;

  constructor(private simplexService: SimplexService) {}

  ngOnInit() {
    // this.simplexService.getMaps().subscribe((subsets) => {
    //   this.subsets = subsets.items;
    // });
    this.maps = [
      {
        id: 1,
        name: 'Reimbursement codes map',
        description: 'This is the first edition'
      },
      {
        id: 2,
        name: 'Anesthesiology procedure codes',
        description: 'This is the second edition'
      },
      {
        id: 3,
        name: 'ICPC-3 Map',
        description: 'This is the third edition'
      }
    ]
  }

  onClick(item: any) {
    this.selectedMap = item;
  }
}
