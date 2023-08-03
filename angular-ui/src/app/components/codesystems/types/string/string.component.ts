import {Component, Input, OnInit} from '@angular/core';

@Component({
    selector: 'app-string',
    templateUrl: './string.component.html',
    styleUrls: ['./string.component.scss']
})
export class StringComponent implements OnInit {

    @Input() item: any;

    constructor() {
    }

    ngOnInit(): void {
    }

    isNumeric(val): boolean {
        return !!parseInt(val) || val === '0';
    }
}
