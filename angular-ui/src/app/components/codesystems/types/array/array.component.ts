import {Component, Input, OnInit} from '@angular/core';
import {KeyValue} from "@angular/common";
import {ModalService} from "../../../../services/modal/modal.service";

@Component({
    selector: 'app-array',
    templateUrl: './array.component.html',
    styleUrls: ['./array.component.scss']
})
export class ArrayComponent implements OnInit {

    @Input() items: any;
    collapsed: boolean = true;

    constructor(private modalService: ModalService) {
    }

    ngOnInit(): void {
    }

    openModal(id: string): void {
        this.modalService.open(id);
    }

    closeModal(id: string): void {
        this.modalService.close(id);
    }

    isString(val): boolean {
        return typeof val === 'string';
    }

    isBoolean(val): boolean {
        return typeof val === 'boolean';
    }

    isNumber(val): boolean {
        return typeof val === 'number';
    }

    isArray(val): boolean {
        return val instanceof Array;
    }

    isObject(val): boolean {
        return typeof val === 'object' && !this.isArray(val);
    }

    originalOrder = (a: KeyValue<number,string>, b: KeyValue<number,string>): number => {
        return 0;
    }
}
