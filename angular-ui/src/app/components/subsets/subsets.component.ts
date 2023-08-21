import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-subsets',
  templateUrl: './subsets.component.html',
  styleUrls: ['./subsets.component.scss']
})
export class SubsetsComponent implements OnChanges {
  @Input() edition: string;
  subsets = [];
  selectedSubset = null;
  newSubsetMode = false;
  loading = false;
  editionFields = ["idAndFsnTerm", "active", "activeMemberCount", "moduleId"];
  saving = false;

  constructor(private simplexService: SimplexService) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes['edition'] && changes['edition'].currentValue) {
      this.loading = true;
      this.simplexService.getSimpleRefsets(changes['edition'].currentValue['shortName']).subscribe((subsets) => {
        this.subsets = subsets;
        this.loading = false;
      });
    }
  }

  onClick(item: any) {
    this.selectedSubset = item;
  }
  submit() {
  }
}