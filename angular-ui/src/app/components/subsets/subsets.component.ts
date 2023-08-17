import { Component } from '@angular/core';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-subsets',
  templateUrl: './subsets.component.html',
  styleUrls: ['./subsets.component.scss']
})
export class SubsetsComponent {
  subsets = [];
  selectedSubset = null;

  constructor(private simplexService: SimplexService) {}

  ngOnInit() {
    // this.simplexService.getSubsets().subscribe((subsets) => {
    //   this.subsets = subsets.items;
    // });
    this.subsets = [
      {
        id: 1,
        name: 'Cardiology findings',
        description: 'This is the first edition'
      },
      {
        id: 2,
        name: 'Cardiology procedures',
        description: 'This is the second edition'
      },
      {
        id: 3,
        name: 'Communicable diseases',
        description: 'This is the third edition'
      }
    ]
  }

  onClick(item: any) {
    this.selectedSubset = item;
  }
  
}
