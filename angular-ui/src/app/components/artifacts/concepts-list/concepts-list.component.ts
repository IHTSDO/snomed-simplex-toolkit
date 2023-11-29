import { Component, Input, OnChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-concepts-list',
  templateUrl: './concepts-list.component.html',
  styleUrls: ['./concepts-list.component.scss']
})
export class ConceptsListComponent implements OnChanges {

  @Input() edition: string;

  displayedColumns: string[] = ['conceptId', 'term'];
  concepts: any[] = [];
  loading = false;
  
  constructor(private simplexService: SimplexService,
    private snackBar: MatSnackBar) {}


  loadConcepts() {
    this.loading = true;
    this.simplexService.getConcepts(this.edition).subscribe((concepts) => {
      this.concepts = concepts.items;
      this.loading = false;
    });
  }

  ngOnChanges() {
    this.loadConcepts();
  }

}
