import { Component, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-number',
  templateUrl: './number.component.html',
  styleUrls: ['./number.component.scss'],
})
export class NumberComponent implements OnInit {
  @Input() item: any;

  constructor() {}

  ngOnInit(): void {}

  onInputChange(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    this.item.value = inputElement.valueAsNumber;
  }
}
