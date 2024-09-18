import { Component, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-string',
  templateUrl: './string.component.html',
  styleUrls: ['./string.component.scss'],
})
export class StringComponent implements OnInit {
  @Input() item: any;

  constructor() {}

  ngOnInit(): void {}

  isNumeric(val: any): boolean {
    return !!parseInt(val) || val === '0';
  }

  onInputChange(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    this.item.value = inputElement.value;
  }
}
