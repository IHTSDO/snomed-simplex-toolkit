import { Component } from '@angular/core';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-translations',
  templateUrl: './translations.component.html',
  styleUrls: ['./translations.component.scss']
})
export class TranslationsComponent {
  translations = [];
  selectedTranslation = null;

  constructor(private simplexService: SimplexService) {}

  ngOnInit() {
    // this.simplexService.getMaps().subscribe((subsets) => {
    //   this.subsets = subsets.items;
    // });
    this.translations = [
      {
        id: 1,
        name: 'Spanish',
        description: 'This is the first edition'
      }
    ]
  }

  onClick(item: any) {
    this.selectedTranslation = item;
  }
}
