import {Component, Input, OnInit} from '@angular/core';

@Component({
    selector: 'app-boolean',
    templateUrl: './boolean.component.html',
    styleUrls: ['./boolean.component.scss']
})
export class BooleanComponent implements OnInit {

    @Input() item: any;

    constructor() {
    }

    ngOnInit(): void {
    }

}
