import {Component, OnInit} from '@angular/core';
import {Subscription} from "rxjs";
import {SnowstormService} from "../../services/snowstorm/snowstorm.service";
import {KeyValue} from "@angular/common";

@Component({
    selector: 'app-codesystems',
    templateUrl: './codesystems.component.html',
    styleUrls: ['./codesystems.component.scss']
})
export class CodesystemsComponent implements OnInit {

    activeCodesystem: any;
    activeCodesystemSubscription: Subscription;

    constructor(private snowstormService: SnowstormService) {
        this.activeCodesystemSubscription = this.snowstormService.getActiveCodesystem().subscribe(data => this.activeCodesystem = data);
    }

    ngOnInit(): void {
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
